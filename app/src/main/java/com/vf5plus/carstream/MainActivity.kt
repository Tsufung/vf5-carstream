package com.vf5plus.carstream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Photo picker for QR
    private val pickQrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    val dataUrl = "data:image/png;base64,$b64"

                    runOnUiThread {
                        webView.evaluateJavascript("window.setQrImageBase64('$dataUrl')", null)
                        Toast.makeText(this, "Đã chọn ảnh QR thành công!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission request for GPS speed
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webViewStudio)

        // Request location permissions if not granted
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            requestLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

        setupWebView()
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

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // Register AndroidBridge
        webView.addJavascriptInterface(AndroidBridge(this, webView), "AndroidBridge")

        // Load Studio HTML from assets
        webView.loadUrl("file:///android_asset/studio.html")
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
    }
}
