package com.vf5plus.carstream

import android.app.Presentation
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class SplitScreenPresentation(
    private val themedContext: Context,
    display: Display
) : Presentation(themedContext, display, android.R.style.Theme_DeviceDefault_NoActionBar) {

    companion object {
        var activeInstance: SplitScreenPresentation? = null
            private set
    }

    // Config values read from SharedPreferences (set by Studio)
    var splitRatio: Int = 79
    var hudX: Int = 16
    var hudY: Int = 16
    var qrX: Int = 24
    var qrY: Int = 90
    var qrSize: Int = 130
    var speedOffset: Int = 0
    var qrImageBase64: String = ""

    // Views
    lateinit var mapWebView: WebView
        private set
    lateinit var mediaWebView: WebView
        private set

    private lateinit var rootLayout: FrameLayout
    private lateinit var speedHudLayout: LinearLayout
    private lateinit var tvSpeedNumber: TextView
    private lateinit var qrOverlayLayout: FrameLayout
    private lateinit var btnTabYt: TextView
    private lateinit var btnTabTk: TextView
    private lateinit var btnTabVtv: TextView

    private var activeMediaTab = "youtube"
    private var isQrVisible = false

    init {
        loadConfiguration()
    }

    private fun loadConfiguration() {
        val prefs = themedContext.getSharedPreferences("vf5_layout_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("layout_config", "")
        if (!raw.isNullOrEmpty()) {
            try {
                val json = JSONObject(raw)
                splitRatio = json.optInt("splitRatio", 79).coerceIn(20, 85)
                hudX = json.optInt("hudX", 16)
                hudY = json.optInt("hudY", 16)
                qrX = json.optInt("qrX", 24)
                qrY = json.optInt("qrY", 90)
                qrSize = json.optInt("qrSize", 130).coerceIn(80, 250)
                speedOffset = json.optInt("speedOffset", 0)
                qrImageBase64 = json.optString("qrImageBase64", "")
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
        val mapContainer = FrameLayout(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                splitRatio.toFloat()
            )
            setBackgroundColor(Color.parseColor("#12151B"))
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

        // Split Divider line
        val dividerView = View(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(3),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#C86A3E"))
        }

        // Right Pane: Media (YouTube, TikTok, VTV)
        val mediaContainer = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                (100 - splitRatio).toFloat()
            )
            setBackgroundColor(Color.parseColor("#0A0C0E"))
        }

        // Quick Tabs
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
        btnTabVtv = createTabButton("📺 VTV", false) { selectTab("vtvgo") }

        tabRow.addView(btnTabYt)
        tabRow.addView(btnTabTk)
        tabRow.addView(btnTabVtv)

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
            textSize = 11f
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

    private fun selectTab(tab: String) {
        activeMediaTab = tab
        val activeBg = Color.parseColor("#C86A3E")
        val inactiveBg = Color.parseColor("#16181F")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#9CA3AF")

        btnTabYt.setBackgroundColor(if (tab == "youtube") activeBg else inactiveBg)
        btnTabYt.setTextColor(if (tab == "youtube") activeText else inactiveText)

        btnTabTk.setBackgroundColor(if (tab == "tiktok") activeBg else inactiveBg)
        btnTabTk.setTextColor(if (tab == "tiktok") activeText else inactiveText)

        btnTabVtv.setBackgroundColor(if (tab == "vtvgo") activeBg else inactiveBg)
        btnTabVtv.setTextColor(if (tab == "vtvgo") activeText else inactiveText)

        when (tab) {
            "youtube" -> mediaWebView.loadUrl("https://m.youtube.com")
            "tiktok" -> mediaWebView.loadUrl("https://www.tiktok.com")
            "vtvgo" -> mediaWebView.loadUrl("https://vtvgo.vn")
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

    // ─── 3. QR Code Overlay ──────────────────────────────────────────────────

    private fun buildQrOverlay() {
        val qrBg = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(3), Color.parseColor("#C86A3E"))
        }

        qrOverlayLayout = FrameLayout(themedContext).apply {
            background = qrBg
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dp(qrSize),
                dp(qrSize)
            ).apply {
                leftMargin = dp(qrX)
                topMargin = dp(qrY)
            }
        }

        val qrImageView = ImageView(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        if (qrImageBase64.isNotEmpty()) {
            try {
                val clean = qrImageBase64.substringAfter("base64,")
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                qrImageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                qrImageView.setBackgroundColor(Color.LTGRAY)
            }
        } else {
            qrImageView.setBackgroundColor(Color.LTGRAY)
        }

        qrOverlayLayout.addView(qrImageView)
        rootLayout.addView(qrOverlayLayout)
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

        // Left buttons
        val btnHome = createBottomButton("🏠 Trang chủ", Color.parseColor("#20242E"), Color.WHITE) {
            mapWebView.loadUrl("file:///android_asset/map.html")
            mediaWebView.loadUrl("https://m.youtube.com")
        }
        val btnQr = createBottomButton("💳 Mã QR Thu Tiền", Color.parseColor("#C86A3E"), Color.WHITE) {
            toggleQr()
        }

        barLayout.addView(btnHome)
        barLayout.addView(btnQr)

        // Spacer
        val spacer = View(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1.0f)
        }
        barLayout.addView(spacer)

        // Media controls (Steering wheel simulation)
        val mediaControlGroup = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0B0E"))
                cornerRadius = dp(8).toFloat()
            }
        }

        val btnPrev = createMediaButton("⏮") { triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        val btnPlay = createMediaButton("⏯") { triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        val btnNext = createMediaButton("⏭") { triggerMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }

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
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(8).toFloat()
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
                leftMargin = dp(2)
                rightMargin = dp(2)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E222B"))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    override fun onStop() {
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onStop()
    }

    fun switchToYoutube() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            selectTab("youtube")
        }
    }

    fun switchToTikTok() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            selectTab("tiktok")
        }
    }

    fun switchToVtv() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            selectTab("vtvgo")
        }
    }

    fun triggerMediaKey(keyCode: Int) {
        val audioManager = themedContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager?.dispatchMediaKeyEvent(eventDown)
        audioManager?.dispatchMediaKeyEvent(eventUp)

        // Web video helpers
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (activeMediaTab == "tiktok") {
                    mediaWebView.evaluateJavascript("window.scrollBy({top: window.innerHeight, behavior: 'smooth'});", null)
                } else {
                    mediaWebView.evaluateJavascript("document.querySelector('.ytp-next-button')?.click();", null)
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (activeMediaTab == "tiktok") {
                    mediaWebView.evaluateJavascript("window.scrollBy({top: -window.innerHeight, behavior: 'smooth'});", null)
                } else {
                    mediaWebView.evaluateJavascript("document.querySelector('.ytp-prev-button')?.click();", null)
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                mediaWebView.evaluateJavascript(
                    "var v = document.querySelector('video'); if (v) { v.paused ? v.play() : v.pause(); }",
                    null
                )
            }
        }
    }

    // ─── GPS & Speed Updates ─────────────────────────────────────────────────

    fun updateSpeed(rawKmh: Int) {
        val adjusted = (rawKmh * (1.0 + speedOffset / 100.0)).toInt().coerceAtLeast(0)
        tvSpeedNumber.text = "$adjusted"
    }

    fun updateLocation(lat: Double, lng: Double, accuracy: Float, speed: Float) {
        mapWebView.evaluateJavascript("updateLocation($lat, $lng, $accuracy, $speed)", null)
    }

    // ─── Touch Event Forwarding ──────────────────────────────────────────────

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

    // ─── Utility ─────────────────────────────────────────────────────────────

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
        wv.webViewClient = WebViewClient()
        wv.webChromeClient = WebChromeClient()
    }

    private fun dp(value: Int): Int {
        val density = themedContext.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}
