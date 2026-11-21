package com.vf5plus.carstream

import android.app.Presentation
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File

class SplitScreenPresentation(
    private val themedContext: Context,
    display: Display
) : Presentation(themedContext, display, android.R.style.Theme_DeviceDefault_NoActionBar) {

    companion object {
        var activeInstance: SplitScreenPresentation? = null
            private set
    }

    // Config values read from SharedPreferences
    private var splitRatio: Int = 70
    private var hudX: Int = 16
    private var hudY: Int = 16
    private var speedOffset: Int = 0

    // Views
    lateinit var mapWebView: WebView
        private set
    lateinit var mediaWebView: WebView
        private set

    private lateinit var rootLayout: FrameLayout
    private lateinit var mapContainer: FrameLayout
    private lateinit var mediaContainer: LinearLayout
    private lateinit var dividerView: View
    private lateinit var speedHudLayout: LinearLayout
    private lateinit var tvSpeedNumber: TextView
    private lateinit var qrOverlayLayout: FrameLayout
    private lateinit var btnTabYt: TextView
    private lateinit var btnTabTk: TextView

    private var activeMediaTab = "youtube"
    private var isQrVisible = false

    private val userAgentDesktop = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    init {
        loadConfiguration()
    }

    private fun loadConfiguration() {
        val prefs = themedContext.getSharedPreferences("vf5_layout_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("layout_config", "")
        if (!raw.isNullOrEmpty()) {
            try {
                val json = JSONObject(raw)
                splitRatio = json.optInt("splitRatio", 70).coerceIn(0, 85)
                hudX = json.optInt("hudX", 16)
                hudY = json.optInt("hudY", 16)
                speedOffset = json.optInt("speedOffset", 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this

        rootLayout = FrameLayout(themedContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        buildMainContent()
        buildSpeedHudOverlay()
        buildQrOverlay()
        buildBottomBar()

        setContentView(rootLayout)

        requestAudioFocus()
    }

    override fun onStop() {
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onStop()
    }

    private fun requestAudioFocus() {
        try {
            val audioManager = themedContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── 1. Main Split Content ───────────────────────────────────────────────

    private fun buildMainContent() {
        val contentBox = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                bottomMargin = dp(42) // space for bottom bar
            }
        }

        val splitRow = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        // Left Pane: Map (width by splitRatio)
        mapContainer = FrameLayout(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (splitRatio > 0) splitRatio.toFloat() else 0.001f
            )
            setBackgroundColor(Color.parseColor("#0D0F12"))
            visibility = if (splitRatio > 0) View.VISIBLE else View.GONE
        }

        mapWebView = WebView(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setupWebViewSettings(this)
            loadUrl("file:///android_asset/map.html")
        }
        mapContainer.addView(mapWebView)

        // Split Divider line (Draggable to resize)
        dividerView = View(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(6),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#C86A3E"))
            visibility = if (splitRatio > 0) View.VISIBLE else View.GONE
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE) {
                    val totalWidth = rootLayout.width
                    if (totalWidth > 0) {
                        val ratio = ((event.rawX / totalWidth) * 100).toInt().coerceIn(20, 80)
                        setSplitRatio(ratio)
                    }
                }
                true
            }
        }

        // Right Pane: Media (YouTube, TikTok)
        mediaContainer = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (splitRatio > 0) (100 - splitRatio).toFloat() else 100f
            )
            setBackgroundColor(Color.parseColor("#0A0C0E"))
        }

        // Quick Tabs: YouTube & TikTok ONLY (No VTV)
        val tabRow = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
            setBackgroundColor(Color.parseColor("#16181F"))
        }

        btnTabYt = createTabButton("▶ YouTube", true) { selectTab("youtube") }
        btnTabTk = createTabButton("♪ TikTok", false) { selectTab("tiktok") }

        tabRow.addView(btnTabYt)
        tabRow.addView(btnTabTk)

        mediaWebView = WebView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            setupWebViewSettings(this)
            loadUrl("https://m.youtube.com")
        }

        mediaContainer.addView(tabRow)
        mediaContainer.addView(mediaWebView)

        splitRow.addView(mapContainer)
        splitRow.addView(dividerView)
        splitRow.addView(mediaContainer)

        contentBox.addView(splitRow)
        rootLayout.addView(contentBox)
    }

    private fun createTabButton(text: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        return TextView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
            )
            gravity = Gravity.CENTER
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            if (isSelected) {
                setBackgroundColor(Color.parseColor("#C86A3E"))
                setTextColor(Color.WHITE)
            } else {
                setBackgroundColor(Color.parseColor("#16181F"))
                setTextColor(Color.parseColor("#9CA3AF"))
            }
            setOnClickListener { onClick() }
        }
    }

    fun selectTab(tab: String) {
        activeMediaTab = tab
        val activeBg = Color.parseColor("#C86A3E")
        val inactiveBg = Color.parseColor("#16181F")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#9CA3AF")

        btnTabYt.setBackgroundColor(if (tab == "youtube") activeBg else inactiveBg)
        btnTabYt.setTextColor(if (tab == "youtube") activeText else inactiveText)

        btnTabTk.setBackgroundColor(if (tab == "tiktok") activeBg else inactiveBg)
        btnTabTk.setTextColor(if (tab == "tiktok") activeText else inactiveText)

        requestAudioFocus()

        if (tab == "youtube") {
            mediaWebView.settings.userAgentString = null // default mobile agent
            mediaWebView.loadUrl("https://m.youtube.com")
        } else {
            mediaWebView.settings.userAgentString = userAgentDesktop
            mediaWebView.loadUrl("https://www.tiktok.com/explore")
        }
    }

    fun setSplitRatio(ratio: Int) {
        splitRatio = ratio
        Handler(Looper.getMainLooper()).post {
            if (ratio <= 0) {
                mapContainer.visibility = View.GONE
                dividerView.visibility = View.GONE
                val mediaParams = mediaContainer.layoutParams as LinearLayout.LayoutParams
                mediaParams.weight = 100f
                mediaContainer.layoutParams = mediaParams
            } else {
                mapContainer.visibility = View.VISIBLE
                dividerView.visibility = View.VISIBLE

                val mapParams = mapContainer.layoutParams as LinearLayout.LayoutParams
                mapParams.weight = ratio.toFloat()
                mapContainer.layoutParams = mapParams

                val mediaParams = mediaContainer.layoutParams as LinearLayout.LayoutParams
                mediaParams.weight = (100 - ratio).toFloat()
                mediaContainer.layoutParams = mediaParams
            }

            // Save ratio
            val prefs = themedContext.getSharedPreferences("vf5_layout_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("layout_config", "")
            val json = if (raw.isNullOrEmpty()) JSONObject() else JSONObject(raw)
            json.put("splitRatio", ratio)
            prefs.edit().putString("layout_config", json.toString()).apply()
        }
    }

    // ─── 2. Speed HUD Overlay ────────────────────────────────────────────────

    private fun buildSpeedHudOverlay() {
        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#E6000000"))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(2), Color.parseColor("#10B981"))
        }

        speedHudLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bgDrawable
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(hudX)
                topMargin = dp(hudY)
            }
        }

        // Green dot
        val dotView = View(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                rightMargin = dp(6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#34D399"))
            }
        }

        tvSpeedNumber = TextView(themedContext).apply {
            text = "0"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#34D399"))
        }

        val tvUnit = TextView(themedContext).apply {
            text = " KM/H"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#D1D5DB"))
        }

        speedHudLayout.addView(dotView)
        speedHudLayout.addView(tvSpeedNumber)
        speedHudLayout.addView(tvUnit)

        rootLayout.addView(speedHudLayout)
    }

    // ─── 3. Large Centered Payment QR Overlay ────────────────────────────────

    private fun buildQrOverlay() {
        val qrBg = GradientDrawable().apply {
            setColor(Color.parseColor("#F50D1117"))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(3), Color.parseColor("#C86A3E"))
        }

        qrOverlayLayout = FrameLayout(themedContext).apply {
            background = qrBg
            setPadding(dp(16), dp(16), dp(16), dp(16))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dp(280),
                dp(280)
            ).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener { toggleQr() }
        }

        val container = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val qrImageView = ImageView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                0,
                1.0f
            ).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Load permanent file payment_qr.png first
        loadQrBitmap(qrImageView)

        val btnClose = TextView(themedContext).apply {
            text = "✕ Đóng Mã QR"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { toggleQr() }
        }

        container.addView(qrImageView)
        container.addView(btnClose)

        qrOverlayLayout.addView(container)
        rootLayout.addView(qrOverlayLayout)
    }

    private fun loadQrBitmap(imageView: ImageView) {
        val qrFile = File(themedContext.filesDir, "payment_qr.png")
        if (qrFile.exists()) {
            try {
                val bmp = BitmapFactory.decodeFile(qrFile.absolutePath)
                if (bmp != null) {
                    imageView.setImageBitmap(bmp)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        imageView.setBackgroundColor(Color.DKGRAY)
    }

    fun toggleQr() {
        isQrVisible = !isQrVisible
        qrOverlayLayout.visibility = if (isQrVisible) View.VISIBLE else View.GONE
    }

    // ─── 4. Bottom Action Bar ────────────────────────────────────────────────

    private fun buildBottomBar() {
        val barLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#121419"))
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        // Ratio buttons
        val btn70 = createBottomButton("70/30", Color.parseColor("#20242E"), Color.WHITE) { setSplitRatio(70) }
        val btn50 = createBottomButton("50/50", Color.parseColor("#20242E"), Color.WHITE) { setSplitRatio(50) }
        val btnFull = createBottomButton("Full Video", Color.parseColor("#20242E"), Color.parseColor("#38BDF8")) { setSplitRatio(0) }
        val btnQr = createBottomButton("💳 Mã QR", Color.parseColor("#C86A3E"), Color.WHITE) { toggleQr() }

        barLayout.addView(btn70)
        barLayout.addView(btn50)
        barLayout.addView(btnFull)
        barLayout.addView(btnQr)

        // Spacer
        val spacer = View(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1.0f)
        }
        barLayout.addView(spacer)

        // Media controls (Next / Play / Prev)
        val mediaControlGroup = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0B0E"))
                cornerRadius = dp(8).toFloat()
            }
        }

        val btnPrev = createMediaButton("⏮") { handlePrevTrack() }
        val btnPlay = createMediaButton("⏯") { handlePlayPause() }
        val btnNext = createMediaButton("⏭") { handleNextTrack() }

        mediaControlGroup.addView(btnPrev)
        mediaControlGroup.addView(btnPlay)
        mediaControlGroup.addView(btnNext)
        barLayout.addView(mediaControlGroup)

        // Reload button
        val btnReload = createBottomButton("🔄 Tải lại", Color.parseColor("#20242E"), Color.LTGRAY) {
            mapWebView.reload()
            mediaWebView.reload()
        }
        barLayout.addView(btnReload)

        rootLayout.addView(barLayout)
    }

    private fun createBottomButton(title: String, bgColor: Int, textColor: Int, onClick: () -> Unit): TextView {
        return TextView(themedContext).apply {
            text = title
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(4)
            }
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createMediaButton(icon: String, onClick: () -> Unit): TextView {
        return TextView(themedContext).apply {
            text = icon
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(2)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B1F2A"))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    // ─── 5. Steering Wheel Controls Callback ─────────────────────────────────

    fun handleNextTrack() {
        Handler(Looper.getMainLooper()).post {
            if (activeMediaTab == "tiktok") {
                mediaWebView.evaluateJavascript("window.scrollBy({top: window.innerHeight, behavior: 'smooth'});", null)
            } else {
                mediaWebView.evaluateJavascript(
                    "var btn = document.querySelector('.ytp-next-button') || document.querySelector('button[aria-label*=\"Tiếp theo\"]') || document.querySelector('button[aria-label*=\"Next\"]'); if (btn) { btn.click(); } else { window.history.forward(); }",
                    null
                )
            }
        }
    }

    fun handlePrevTrack() {
        Handler(Looper.getMainLooper()).post {
            if (activeMediaTab == "tiktok") {
                mediaWebView.evaluateJavascript("window.scrollBy({top: -window.innerHeight, behavior: 'smooth'});", null)
            } else {
                mediaWebView.evaluateJavascript("window.history.back();", null)
            }
        }
    }

    fun handlePlayPause() {
        Handler(Looper.getMainLooper()).post {
            mediaWebView.evaluateJavascript(
                "var v = document.querySelector('video'); if (v) { v.paused ? v.play() : v.pause(); }",
                null
            )
        }
    }

    fun switchToYoutube() {
        Handler(Looper.getMainLooper()).post {
            selectTab("youtube")
        }
    }

    fun switchToTikTok() {
        Handler(Looper.getMainLooper()).post {
            selectTab("tiktok")
        }
    }

    // ─── 6. Navigation Turn Guidance & OSRM Route ─────────────────────────────

    fun updateTurnBanner(icon: String, distance: String, road: String) {
        Handler(Looper.getMainLooper()).post {
            val safeIcon = icon.replace("'", "\\'")
            val safeDist = distance.replace("'", "\\'")
            val safeRoad = road.replace("'", "\\'")
            mapWebView.evaluateJavascript("setTurnGuidance('$safeIcon', '$safeDist', '$safeRoad');", null)
        }
    }

    fun drawRoute(geoJsonStr: String) {
        Handler(Looper.getMainLooper()).post {
            mapWebView.evaluateJavascript("drawRouteGeoJson($geoJsonStr);", null)
        }
    }

    // ─── 7. GPS & Speed Updates ──────────────────────────────────────────────

    fun updateSpeed(filteredKmh: Int) {
        val adjusted = (filteredKmh * (1.0 + speedOffset / 100.0)).toInt().coerceAtLeast(0)
        tvSpeedNumber.text = "$adjusted"
    }

    fun updateLocation(lat: Double, lng: Double, accuracy: Float, speed: Float) {
        mapWebView.evaluateJavascript("updateLocation($lat, $lng, $accuracy, $speed)", null)
    }

    // ─── 8. Touch Event Forwarding ───────────────────────────────────────────

    fun dispatchClick(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = MotionEvent.obtain(downTime, eventTime + 50, MotionEvent.ACTION_UP, x, y, 0)

        window?.decorView?.dispatchTouchEvent(downEvent)
        window?.decorView?.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        upEvent.recycle()
    }

    fun dispatchScroll(distanceX: Float, distanceY: Float) {
        mapWebView.scrollBy(distanceX.toInt(), distanceY.toInt())
    }

    // ─── 9. WebView Setup ────────────────────────────────────────────────────

    private fun setupWebViewSettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false // Let WebView load it
                }
                // Block intent://, market://, snssdk:// to prevent net::ERR_UNKNOWN_URL_SCHEME
                return true
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources) // Automatically grant mic permission for voice search
            }
        }
    }

    private fun dp(value: Int): Int {
        val density = themedContext.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}
