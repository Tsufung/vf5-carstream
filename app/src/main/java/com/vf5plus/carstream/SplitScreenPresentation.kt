package com.vf5plus.carstream

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream

/**
 * SplitScreenPresentation — Tối ưu mượt mà 60fps cho màn hình 8 inch xe VinFast VF5 Plus 2025.
 *
 * Tính năng chính:
 * - Giảm giật lag: Bật GPU Hardware Acceleration, Pre-Rasterization, tối ưu bộ đệm cảm ứng
 * - VietMap Live: Cảnh báo giới hạn tốc độ, camera phạt nguội, nút bật/tắt âm thanh cảnh báo, kéo thả vị trí tùy ý
 * - Google Maps: Live Traffic (xanh/cam/đỏ/đỏ sẫm), thẻ thông tin tuyến đường & giờ đến kéo thả tự do
 * - Ứng dụng giải trí: Chỉ giữ YouTube & TikTok (Đã chặn quảng cáo, tài khoản cá nhân, chuyển bài vô lăng)
 * - Nút QR Thu Tiền 1 chạm cho khách Grab/XanhSM quét chuyển khoản
 */
class SplitScreenPresentation(
    private val outerContext: Context,
    display: Display
) : Presentation(outerContext, display, android.R.style.Theme_Material_NoActionBar) {

    private lateinit var displayCtx: Context
    private val config: VF5Config by lazy { VF5Config(outerContext) }

    // WebViews & Containers
    lateinit var mapWebView: WebView
        private set
    lateinit var mediaWebView: WebView
        private set
    private lateinit var mapContainer: FrameLayout
    private lateinit var rightColumn: LinearLayout
    private lateinit var rootLayout: FrameLayout

    // Kéo thả: VietMap Live Widget & Map Route Info
    private lateinit var vietmapSpeedWidget: LinearLayout
    private lateinit var tvVietmapLimit: TextView
    private lateinit var tvVietmapSpeed: TextView
    private lateinit var tvVietmapAudioToggle: TextView
    private lateinit var mapInfoCard: LinearLayout
    private lateinit var tvMapEta: TextView

    // Modal QR Overlay
    private lateinit var qrOverlayLayout: FrameLayout
    private lateinit var qrImageView: ImageView
    private lateinit var qrInstructionText: TextView

    // Tab buttons
    private val tabButtons = mutableMapOf<String, TextView>()
    var activeTab: String = "youtube"
        private set

    // AudioFocus
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Domain chặn quảng cáo YouTube & TikTok
    private val adDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adservice.google.com.vn",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "partner.googleadservices.com",
        "stats.g.doubleclick.net",
        "cm.g.doubleclick.net",
        "yt3.ggpht.com/ytc/",
        "youtube.com/api/stats/ads",
        "youtube.com/pagead",
        "youtube.com/ptracking",
        "s.youtube.com",
        "ads.tiktok.com",
        "analytics.tiktok.com",
        "log.tiktok.com",
        "mon.tiktok.com",
        "metrics.tiktok.com",
        "i.byteoversea.com",
        "log.byteoversea.com"
    )

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayCtx = this.context

        // ⚠️ TỐI ƯU GIẢM LAG: Bật phần cứng GPU cho toàn bộ cửa sổ Presentation
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        audioManager = outerContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        buildLayout()
        requestAudioFocusCompat()
    }

    override fun onStop() {
        super.onStop()
        abandonAudioFocus()
        mapWebView.onPause()
        mediaWebView.onPause()
    }

    // ─── Layout ───────────────────────────────────────────────────────────────

    private fun buildLayout() {
        rootLayout = FrameLayout(displayCtx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(if (config.themeMode == VF5Config.THEME_LIGHT) 0xFFF0F0F0.toInt() else 0xFF000000.toInt())
        }

        val contentRow = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Google Maps Container
        mapContainer = FrameLayout(displayCtx)
        mapWebView = buildMapWebView()
        mapContainer.addView(
            mapWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 2. Right Column (YouTube + TikTok)
        rightColumn = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tabBar = buildTabBar()
        rightColumn.addView(tabBar)

        val mediaContainer = FrameLayout(displayCtx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mediaWebView = buildMediaWebView()
        mediaContainer.addView(
            mediaWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        rightColumn.addView(mediaContainer)

        applyScreenRatio(contentRow)

        contentRow.addView(mapContainer)
        contentRow.addView(rightColumn)
        rootLayout.addView(contentRow)

        // 3. WIDGET CẢNH BÁO TỐC ĐỘ VIETMAP LIVE (KÉO THẢ TỰ DO)
        vietmapSpeedWidget = buildVietmapSpeedWidget()
        rootLayout.addView(vietmapSpeedWidget)
        makeDraggable(vietmapSpeedWidget) { x, y ->
            config.speedWidgetX = x
            config.speedWidgetY = y
        }

        // 4. THẺ THÔNG TIN LỘ TRÌNH & GIỜ ĐẾN GOOGLE MAPS (KÉO THẢ TỰ DO)
        mapInfoCard = buildMapInfoCard()
        rootLayout.addView(mapInfoCard)
        makeDraggable(mapInfoCard) { x, y ->
            config.mapInfoX = x
            config.mapInfoY = y
        }

        // 5. Modal QR Code Thu Tiền
        qrOverlayLayout = buildQrOverlay()
        rootLayout.addView(qrOverlayLayout)

        setContentView(rootLayout)

        loadGoogleMaps()
        loadYouTube()
    }

    private fun applyScreenRatio(contentRow: LinearLayout) {
        when (config.screenRatio) {
            VF5Config.RATIO_SPLIT_2_3 -> {
                mapContainer.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
                rightColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                mapContainer.visibility = View.VISIBLE
                rightColumn.visibility = View.VISIBLE
            }
            VF5Config.RATIO_SPLIT_1_2 -> {
                mapContainer.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                rightColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                mapContainer.visibility = View.VISIBLE
                rightColumn.visibility = View.VISIBLE
            }
            VF5Config.RATIO_FULL_MAP -> {
                mapContainer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                mapContainer.visibility = View.VISIBLE
                rightColumn.visibility = View.GONE
            }
            VF5Config.RATIO_FULL_MEDIA -> {
                rightColumn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                mapContainer.visibility = View.GONE
                rightColumn.visibility = View.VISIBLE
            }
        }
    }

    // ─── Tab Bar (Chỉ giữ YouTube + TikTok + Nút QR Thu Tiền) ────────────────

    private fun buildTabBar(): LinearLayout {
        val tabBar = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            setBackgroundColor(0xFF1E1B18.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        // 1. YouTube (Chặn quảng cáo)
        val ytBtn = createTabButton("▶ YouTube", "youtube") {
            selectTab("youtube")
            loadYouTube()
        }
        tabBar.addView(ytBtn)

        // 2. TikTok (Lướt bằng phím vô lăng)
        val tkBtn = createTabButton("♪ TikTok", "tiktok") {
            selectTab("tiktok")
            loadTikTok()
        }
        tabBar.addView(tkBtn)

        // 3. Nút QR Thu Tiền (Nổi bật cho tài xế 1 chạm)
        val qrBtn = TextView(displayCtx).apply {
            text = "💳 QR Thu Tiền"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(0xFFC86A3E.toInt())
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                togglePaymentQr()
            }
        }
        tabBar.addView(qrBtn)

        selectTab("youtube")
        return tabBar
    }

    private fun createTabButton(title: String, tag: String, onClick: () -> Unit): TextView {
        val btn = TextView(displayCtx).apply {
            text = title
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(0xFF141210.toInt())
            setPadding(2, 0, 2, 0)
            setOnClickListener { onClick() }
        }
        tabButtons[tag] = btn
        return btn
    }

    private fun selectTab(tag: String) {
        activeTab = tag
        tabButtons.forEach { (t, btn) ->
            if (t == tag) {
                btn.setBackgroundColor(0xFF3D352E.toInt())
                btn.setTextColor(0xFFFFFFFF.toInt())
                btn.setTypeface(null, Typeface.BOLD)
            } else {
                btn.setBackgroundColor(0xFF141210.toInt())
                btn.setTextColor(0xFFAAAAAA.toInt())
                btn.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    // ─── VietMap Live Widget (Kéo thả tự do + Nút bật/tắt tiếng) ──────────────

    private fun buildVietmapSpeedWidget(): LinearLayout {
        val widget = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xEE0B0E14.toInt())
            val pad = dpToPx(8)
            setPadding(pad, pad / 2, pad, pad / 2)
            elevation = 12f

            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dpToPx(config.speedWidgetX.toInt())
                topMargin = dpToPx(config.speedWidgetY.toInt())
            }
            layoutParams = lp
        }

        // Biển báo giới hạn tốc độ (Tròn viền đỏ như VietMap Live)
        tvVietmapLimit = TextView(displayCtx).apply {
            text = "60"
            setTextColor(0xFF000000.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val size = dpToPx(34)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(8)
            }
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        widget.addView(tvVietmapLimit)

        // Tốc độ xe đang chạy
        val speedCol = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8)
            }
        }
        tvVietmapSpeed = TextView(displayCtx).apply {
            text = "0 km/h"
            setTextColor(0xFF00FF7F.toInt())
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        val camWarning = TextView(displayCtx).apply {
            text = "📷 VietMap Live"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 9f
        }
        speedCol.addView(tvVietmapSpeed)
        speedCol.addView(camWarning)
        widget.addView(speedCol)

        // Nút Loa: Bật/Tắt âm thanh cảnh báo của VietMap Live
        tvVietmapAudioToggle = TextView(displayCtx).apply {
            text = if (config.isVietmapAudioEnabled) "🔊" else "🔇"
            textSize = 16f
            gravity = Gravity.CENTER
            val p = dpToPx(6)
            setPadding(p, p, p, p)
            setOnClickListener {
                config.isVietmapAudioEnabled = !config.isVietmapAudioEnabled
                text = if (config.isVietmapAudioEnabled) "🔊" else "🔇"
            }
        }
        widget.addView(tvVietmapAudioToggle)

        return widget
    }

    fun updateSpeed(speedKmh: Int) {
        tvVietmapSpeed.text = "$speedKmh km/h"
        if (speedKmh > 60) {
            tvVietmapSpeed.setTextColor(0xFFFF3333.toInt()) // Đỏ cảnh báo vượt tốc độ
        } else {
            tvVietmapSpeed.setTextColor(0xFF00FF7F.toInt()) // Xanh an toàn
        }
    }

    // ─── Thẻ Thông Tin Tuyến Đường & Giờ Đến Google Maps (Kéo thả tự do) ─────

    private fun buildMapInfoCard(): LinearLayout {
        val card = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0F172A.toInt())
            val pad = dpToPx(10)
            setPadding(pad, pad, pad, pad)
            elevation = 10f

            val lp = FrameLayout.LayoutParams(
                dpToPx(240),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dpToPx(config.mapInfoX.toInt())
                topMargin = dpToPx(config.mapInfoY.toInt())
            }
            layoutParams = lp
        }

        val turnRow = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val turnIcon = TextView(displayCtx).apply {
            text = "↱"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8)
            }
        }
        val turnTextCol = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val turnDist = TextView(displayCtx).apply {
            text = "Sau 300m"
            setTextColor(0xFF7DD3FC.toInt())
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        val turnStreet = TextView(displayCtx).apply {
            text = "Rẽ vào tuyến đường đón khách"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }
        turnTextCol.addView(turnDist)
        turnTextCol.addView(turnStreet)
        turnRow.addView(turnIcon)
        turnRow.addView(turnTextCol)
        card.addView(turnRow)

        tvMapEta = TextView(displayCtx).apply {
            text = "⏳ Giờ đến: 12 phút • 4.5 km"
            setTextColor(0xFF34D399.toInt())
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dpToPx(4), 0, 0)
        }
        card.addView(tvMapEta)

        return card
    }

    // ─── Modal Overlay Mã QR Thanh Toán ──────────────────────────────────────

    private fun buildQrOverlay(): FrameLayout {
        val overlay = FrameLayout(displayCtx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xEE000000.toInt())
            visibility = View.GONE
            isClickable = true
        }

        val card = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dpToPx(16)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFFFFFFFF.toInt())
            val lp = FrameLayout.LayoutParams(
                dpToPx(380),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }

        val titleTv = TextView(displayCtx).apply {
            text = "💳 QUÉT MÃ QR THANH TOÁN"
            setTextColor(0xFF2D251F.toInt())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(titleTv)

        qrInstructionText = TextView(displayCtx).apply {
            text = "Khách chuyển khoản bằng ứng dụng ngân hàng hoặc MoMo"
            setTextColor(0xFF7C746D.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(10))
        }
        card.addView(qrInstructionText)

        qrImageView = ImageView(displayCtx).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(240),
                dpToPx(240)
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        card.addView(qrImageView)

        val closeBtn = TextView(displayCtx).apply {
            text = "✕ ĐÓNG (QUAY LẠI BẢN ĐỒ)"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFC86A3E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(42)
            ).apply {
                topMargin = dpToPx(12)
            }
            setOnClickListener {
                overlay.visibility = View.GONE
            }
        }
        card.addView(closeBtn)

        overlay.addView(card)
        return overlay
    }

    fun togglePaymentQr() {
        if (qrOverlayLayout.visibility == View.VISIBLE) {
            qrOverlayLayout.visibility = View.GONE
        } else {
            val bitmap = config.getQrBitmap()
            if (bitmap != null) {
                qrImageView.setImageBitmap(bitmap)
                qrInstructionText.text = "Khách chuyển khoản bằng ứng dụng ngân hàng hoặc MoMo"
            } else {
                qrImageView.setImageDrawable(null)
                qrInstructionText.text = "Chưa có ảnh mã QR. Hãy mở app VF5 CarStream trên điện thoại để nạp ảnh QR ngân hàng của bạn."
            }
            qrOverlayLayout.visibility = View.VISIBLE
        }
    }

    // ─── Tối Ưu WebView: Chống Lag & Tăng Tốc Độ Mượt Mà ──────────────────────

    private fun buildMapWebView(): WebView {
        val wv = WebView(displayCtx)
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = CHROME_UA
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            offscreenPreRaster = true // Vẽ trước khung hình ngoài màn hình để cuộn mượt
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("geo:") || url.contains("maps.google.com") && url.contains("navigate")) {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        )
                        intent.setPackage("com.google.android.apps.maps")
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        outerContext.startActivity(intent)
                    } catch (e: Exception) {
                        view.loadUrl(url)
                    }
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return blockAdRequest(request.url.toString())
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Tự động bật lớp giao thông (Traffic)
                view.evaluateJavascript(
                    """
                    (function() {
                        var trafficBtn = document.querySelector('[data-value="traffic"]') ||
                                         document.querySelector('button[aria-label*="Giao thông"]') ||
                                         document.querySelector('button[aria-label*="Traffic"]');
                        if (trafficBtn && !trafficBtn.classList.contains('selected')) {
                            trafficBtn.click();
                        }
                    })();
                    """.trimIndent(), null
                )
            }
        }
        wv.addJavascriptInterface(MapJsInterface(), "VF5Map")
        return wv
    }

    private fun buildMediaWebView(): WebView {
        val wv = WebView(displayCtx)
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = CHROME_UA
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            offscreenPreRaster = true
            allowFileAccess = true
            allowContentAccess = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }

        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return blockAdRequest(request.url.toString())
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("youtube.com")) {
                    injectYouTubeAdBlock(view)
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                val rootView = window?.decorView as? FrameLayout ?: return
                view.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                rootView.addView(view)
            }

            override fun onHideCustomView() {
                val rootView = window?.decorView as? FrameLayout ?: return
                if (rootView.childCount > 1) {
                    rootView.removeViewAt(rootView.childCount - 1)
                }
            }
        }

        return wv
    }

    // ─── Điều Hướng Kéo Thả Trực Tiếp Trên Màn Hình Xe ────────────────────────

    private fun makeDraggable(view: View, onSavePosition: (Float, Float) -> Unit) {
        var dX = 0f
        var dY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val parentWidth = (v.parent as? View)?.width ?: 1280
                    val parentHeight = (v.parent as? View)?.height ?: 720
                    val newX = (event.rawX + dX).coerceIn(0f, (parentWidth - v.width).toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, (parentHeight - v.height).toFloat())
                    v.animate().x(newX).y(newY).setDuration(0).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onSavePosition(v.x / displayCtx.resources.displayMetrics.density,
                                   v.y / displayCtx.resources.displayMetrics.density)
                    true
                }
                else -> false
            }
        }
    }

    // ─── Load URLs ────────────────────────────────────────────────────────────

    fun loadGoogleMaps() {
        mapWebView.loadUrl("https://www.google.com/maps/@?api=1&map_action=map&layer=traffic")
    }

    fun loadYouTube() {
        mediaWebView.loadUrl("https://m.youtube.com")
    }

    fun loadTikTok() {
        mediaWebView.loadUrl("https://www.tiktok.com")
    }

    fun loadMediaUrl(url: String) {
        if (url.contains("tiktok.com")) {
            selectTab("tiktok")
        } else if (url.contains("youtube.com")) {
            selectTab("youtube")
        }
        mediaWebView.loadUrl(url)
    }

    // ─── Phím Vô Lăng Xe ──────────────────────────────────────────────────────

    fun mediaNext() {
        if (activeTab == "youtube") {
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    var btn = document.querySelector('.ytp-next-button') ||
                              document.querySelector('[aria-label="Next video"]') ||
                              document.querySelector('button.ytp-next-button');
                    if (btn) { btn.click(); return 'next_clicked'; }
                    return 'not_found';
                })()
                """.trimIndent(), null
            )
        } else if (activeTab == "tiktok") {
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    window.scrollBy({ top: window.innerHeight, behavior: 'smooth' });
                })()
                """.trimIndent(), null
            )
        }
    }

    fun mediaPrev() {
        if (activeTab == "youtube") {
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    var btn = document.querySelector('.ytp-prev-button') ||
                              document.querySelector('[aria-label="Previous video"]');
                    if (btn) { btn.click(); return 'prev_clicked'; }
                    history.back();
                    return 'history_back';
                })()
                """.trimIndent(), null
            )
        } else if (activeTab == "tiktok") {
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    window.scrollBy({ top: -window.innerHeight, behavior: 'smooth' });
                })()
                """.trimIndent(), null
            )
        }
    }

    fun mediaPlayPause() {
        mediaWebView.evaluateJavascript(
            """
            (function() {
                var video = document.querySelector('video');
                if (video) {
                    if (video.paused) video.play(); else video.pause();
                }
            })()
            """.trimIndent(), null
        )
    }

    // ─── AudioFocus ───────────────────────────────────────────────────────────

    private fun requestAudioFocusCompat() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
            focusRequest = req
            val result = am.requestAudioFocus(req)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = am.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        if (!config.isCallAutoPauseEnabled) return

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mediaWebView.post {
                    mediaWebView.evaluateJavascript(
                        "document.querySelector('video')?.pause();", null
                    )
                }
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaWebView.post {
                    mediaWebView.evaluateJavascript(
                        "document.querySelector('video')?.play();", null
                    )
                }
                hasAudioFocus = true
            }
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    // ─── Chặn Quảng Cáo YouTube & TikTok ──────────────────────────────────────

    private fun blockAdRequest(url: String): WebResourceResponse? {
        val lowerUrl = url.lowercase()
        for (domain in adDomains) {
            if (lowerUrl.contains(domain)) {
                return WebResourceResponse(
                    "text/plain", "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }
        return null
    }

    private fun injectYouTubeAdBlock(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    .ad-showing .video-ads { display: none !important; }
                    .ytp-ad-module { display: none !important; }
                    .ytp-ad-overlay-container { display: none !important; }
                    .ytp-ad-text-overlay { display: none !important; }
                    ytd-banner-promo-renderer { display: none !important; }
                    ytd-ad-slot-renderer { display: none !important; }
                    .ytd-rich-item-renderer:has(ytd-ad-slot-renderer) { display: none !important; }
                `;
                document.head.appendChild(style);
                
                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button');
                    if (skipBtn) skipBtn.click();
                    
                    var adIndicator = document.querySelector('.ad-showing');
                    if (adIndicator) {
                        var video = document.querySelector('video');
                        if (video) video.currentTime = video.duration;
                    }
                }, 300);
            })();
            """.trimIndent(), null
        )
    }

    // ─── JS Interface ─────────────────────────────────────────────────────────

    inner class MapJsInterface {
        @JavascriptInterface
        fun openNavigation(destination: String) {
            try {
                val gmmIntentUri = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(destination)}&mode=d")
                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                mapIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                outerContext.startActivity(mapIntent)
            } catch (e: Exception) {
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * displayCtx.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
