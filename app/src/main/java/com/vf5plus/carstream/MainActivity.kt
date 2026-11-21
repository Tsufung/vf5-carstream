package com.vf5plus.carstream

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null

    // Photo picker for QR
    private val pickQrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)

                    // 1. Save permanent file payment_qr.png to internal storage
                    val qrFile = File(filesDir, "payment_qr.png")
                    FileOutputStream(qrFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
                    }

                    // 2. Encode to base64 for studio webview preview
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 85, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    val dataUrl = "data:image/png;base64,$b64"

                    runOnUiThread {
                        webView.evaluateJavascript("window.setQrImageBase64('$dataUrl')", null)
                        Toast.makeText(this, "✅ Đã lưu mã QR thành công!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webViewStudio)

        requestPermissions()
        initTextToSpeech()
        setupWebView()

        handleNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attrs)
                }
            }
        }
    }

    fun speakGuidance(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "guidance_${System.currentTimeMillis()}")
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(AndroidBridge(this, webView), "AndroidBridge")
        webView.loadUrl("file:///android_asset/studio.html")
    }

    // ─── Navigation Intent Handler (Grab / Xanh SM) ───────────────────────────

    private fun handleNavigationIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme ?: ""
        var destLat: Double? = null
        var destLng: Double? = null
        var destName = ""

        try {
            if (scheme == "geo") {
                val ssp = data.schemeSpecificPart
                if (data.queryParameterNames.contains("q")) {
                    val q = data.getQueryParameter("q") ?: ""
                    val cleanQ = q.substringBefore("(").trim()
                    val parts = cleanQ.split(",")
                    if (parts.size >= 2) {
                        destLat = parts[0].toDoubleOrNull()
                        destLng = parts[1].toDoubleOrNull()
                    }
                    if (q.contains("(") && q.contains(")")) {
                        destName = q.substringAfter("(").substringBefore(")")
                    }
                } else {
                    val coords = ssp.substringBefore("?").split(",")
                    if (coords.size >= 2) {
                        destLat = coords[0].toDoubleOrNull()
                        destLng = coords[1].toDoubleOrNull()
                    }
                }
            } else if (scheme == "google.navigation") {
                val q = data.getQueryParameter("q") ?: data.schemeSpecificPart.substringAfter("q=").substringBefore("&")
                val parts = q.split(",")
                if (parts.size >= 2) {
                    destLat = parts[0].toDoubleOrNull()
                    destLng = parts[1].toDoubleOrNull()
                }
            } else if (data.host?.contains("google.com") == true && data.path?.contains("maps") == true) {
                val daddr = data.getQueryParameter("daddr") ?: ""
                val parts = daddr.split(",")
                if (parts.size >= 2) {
                    destLat = parts[0].toDoubleOrNull()
                    destLng = parts[1].toDoubleOrNull()
                }
            }

            if (destLat != null && destLng != null) {
                fetchAndDrawOsrmRoute(destLat, destLng, destName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchAndDrawOsrmRoute(destLat: Double, destLng: Double, destName: String) {
        Thread {
            try {
                var curLat = 10.7769
                var curLng = 106.7009

                val locMgr = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val lastLoc = locMgr?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locMgr?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (lastLoc != null) {
                    curLat = lastLoc.latitude
                    curLng = lastLoc.longitude
                }

                val urlStr = "https://router.project-osrm.org/route/v1/driving/$curLng,$curLat;$destLng,$destLat?overview=full&geometries=geojson&steps=true"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes")
                    if (routes != null && routes.length() > 0) {
                        val route0 = routes.getJSONObject(0)
                        val geometry = route0.getJSONObject("geometry").toString()

                        runOnUiThread {
                            SplitScreenPresentation.activeInstance?.drawRoute(geometry)
                            val name = if (destName.isNotEmpty()) destName else "Điểm đến"
                            SplitScreenPresentation.activeInstance?.updateTurnBanner("↱", "Đang điều hướng", name)
                            speakGuidance("Bắt đầu điều hướng đến $name")
                            Toast.makeText(this, "🚗 Đang dẫn đường đến: $name", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // ─── Login Dialog (YouTube & TikTok) ──────────────────────────────────────

    fun showLoginDialog(service: String) {
        val url = if (service == "youtube") {
            "https://accounts.google.com/ServiceLogin?service=youtube"
        } else {
            "https://www.tiktok.com/login"
        }

        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val loginWebView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            loadUrl(url)
        }

        dialog.setContentView(loginWebView)
        dialog.setOnDismissListener {
            CookieManager.getInstance().flush()
            Toast.makeText(this, "Đã lưu phiên đăng nhập!", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    inner class AndroidBridge(private val context: Context, private val webView: WebView) {

        @JavascriptInterface
        fun saveLayout(jsonConfig: String) {
            val prefs = context.getSharedPreferences("vf5_layout_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("layout_config", jsonConfig).apply()

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ Đã lưu bố cục thành công vào xe VF5!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun getSavedLayout(): String {
            val prefs = context.getSharedPreferences("vf5_layout_prefs", Context.MODE_PRIVATE)
            return prefs.getString("layout_config", "") ?: ""
        }

        @JavascriptInterface
        fun pickQrImage() {
            Handler(Looper.getMainLooper()).post {
                pickQrLauncher.launch("image/*")
            }
        }

        @JavascriptInterface
        fun openLogin(service: String) {
            Handler(Looper.getMainLooper()).post {
                showLoginDialog(service)
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
