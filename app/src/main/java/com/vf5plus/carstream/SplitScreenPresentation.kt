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
import android.view.View
import android.view.ViewGroup
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
 * SplitScreenPresentation — hiển thị trên màn hình xe VF5 Plus 2025 (8 inch).
 *
 * Tính năng nổi bật:
 * - Google Maps với dữ liệu giao thông thời gian thực (Traffic Layer: xanh/cam/đỏ/đỏ sẫm)
 * - Tự động đồng bộ GPS hiển thị vị trí xe trực tiếp trên bản đồ
 * - Chuyển tiếp mượt sang Google Maps Native khi bấm bắt đầu điều hướng
 * - Tùy biến tỉ lệ chia màn hình: 2/3 + 1/3, 50/50, Toàn màn hình Map, Toàn màn hình Media
 * - HUD tốc độ GPS thực tế ở góc trên màn hình xe
 * - Modal Overlay Mã QR thanh toán cước xe cho khách Grab/XanhSM
 * - Tab bar động hỗ trợ YouTube (chặn quảng cáo), TikTok (vô lăng), VTV Go, FPT Play
 * - Quản lý AudioFocus tự ngắt video khi có cuộc gọi và phát lại sau khi cúp máy
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

    // HUD & QR Overlays
    private lateinit var speedHudBadge: TextView
    private lateinit var qrOverlayLayout: FrameLayout
    private lateinit var qrImageView: ImageView
    private lateinit var qrInstructionText: TextView

    // Tab buttons map
    private val tabButtons = mutableMapOf<String, TextView>()
    var activeTab: String = "youtube"
        private set

    // AudioFocus
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Domain chặn quảng cáo
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

        // 1. Map container
        mapContainer = FrameLayout(displayCtx)
        mapWebView = buildMapWebView()
        mapContainer.addView(
            mapWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 2. Right column (Media + TabBar)
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

        // 3. HUD Tốc độ GPS (Góc trên trái)
        speedHudBadge = buildSpeedHudBadge()
        rootLayout.addView(speedHudBadge)

        // 4. Modal Overlay Mã QR thanh toán
        qrOverlayLayout = buildQrOverlay()
        rootLayout.addView(qrOverlayLayout)

        setContentView(rootLayout)

        // Nạp Google Maps với lớp giao thông realtime
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

    // ─── Tab Bar ──────────────────────────────────────────────────────────────

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

        if (config.isYouTubeEnabled) {
            val btn = createTabButton("▶ YouTube", "youtube") {
                selectTab("youtube")
                loadYouTube()
            }
            tabBar.addView(btn)
        }

        if (config.isTikTokEnabled) {
            val btn = createTabButton("♪ TikTok", "tiktok") {
                selectTab("tiktok")
                loadTikTok()
            }
            tabBar.addView(btn)
        }

        if (config.isVtvGoEnabled) {
            val btn = createTabButton("📺 VTV Go", "vtvgo") {
                selectTab("vtvgo")
                mediaWebView.loadUrl("https://vtvgo.vn")
            }
            tabBar.addView(btn)
        }

        if (config.isFptPlayEnabled) {
            val btn = createTabButton("⚽ FPT Play", "fptplay") {
                selectTab("fptplay")
                mediaWebView.loadUrl("https://fptplay.vn")
            }
            tabBar.addView(btn)
        }

        // Nút QR Thu Tiền 1 chạm
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

    // ─── HUD Tốc độ GPS ──────────────────────────────────────────────────────

    private fun buildSpeedHudBadge(): TextView {
        return TextView(displayCtx).apply {
            text = "0 km/h"
            setTextColor(0xFF00FF7F.toInt())
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val padH = dpToPx(12)
            val padV = dpToPx(6)
            setPadding(padH, padV, padH, padV)
            setBackgroundColor(0xDD000000.toInt())

            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dpToPx(10)
                marginStart = dpToPx(10)
            }
            layoutParams = lp
            visibility = if (config.isSpeedHudEnabled) View.VISIBLE else View.GONE
        }
    }

    fun updateSpeed(speedKmh: Int) {
        if (!config.isSpeedHudEnabled) {
            speedHudBadge.visibility = View.GONE
            return
        }
        speedHudBadge.visibility = View.VISIBLE
        speedHudBadge.text = "$speedKmh km/h"

        if (speedKmh > 80) {
            speedHudBadge.setTextColor(0xFFFF3333.toInt())
        } else {
            speedHudBadge.setTextColor(0xFF00FF7F.toInt())
        }
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
            text = "✕ ĐÓNG"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
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
                qrInstructionText.text = "Chưa có ảnh mã QR. Hãy mở app VF5 CarStream trên điện thoại để chọn ảnh QR ngân hàng của bạn."
            }
            qrOverlayLayout.visibility = View.VISIBLE
        }
    }

    // ─── WebViews: Google Maps với Dữ Liệu Giao Thông Realtime ─────────────────

    private fun buildMapWebView(): WebView {
        val wv = WebView(displayCtx)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true) // BẬT ĐỊNH VỊ GPS CHO GOOGLE MAPS
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = CHROME_UA
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // WebChromeClient tự động cấp quyền vị trí cho Google Maps
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
                // Mở Google Maps native khi tài xế bấm bắt đầu điều hướng
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
                // Tự động kích hoạt lớp giao thông thời gian thực (Live Traffic)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
            allowFileAccess = true
            allowContentAccess = true
        }
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

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

    // ─── Google Maps: Dữ liệu giao thông Realtime ─────────────────────────────

    fun loadGoogleMaps() {
        // Tham số layer=traffic mở trực tiếp lớp tình trạng kẹt xe xanh/cam/đỏ của Google
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

    // ─── Steering Wheel ───────────────────────────────────────────────────────

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

    // ─── Ad blocking ──────────────────────────────────────────────────────────

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
