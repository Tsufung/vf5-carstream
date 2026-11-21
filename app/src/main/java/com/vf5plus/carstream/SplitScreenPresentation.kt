package com.vf5plus.carstream

import android.app.Presentation
import android.content.Context
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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream

/**
 * SplitScreenPresentation — hiển thị trên màn hình xe VF5 Plus 2025 (8 inch).
 *
 * Layout: Google Maps (trái, 2/3) | YouTube hoặc TikTok (phải, 1/3)
 *
 * FIX CRASH QUAN TRỌNG:
 * - Mọi View phải dùng [displayCtx] = this.context (Presentation.getContext()),
 *   KHÔNG dùng outerContext (applicationContext) để tạo View.
 * - outerContext chỉ dùng để khởi tạo super() và lấy system services ngoài Presentation.
 */
class SplitScreenPresentation(
    private val outerContext: Context,
    display: Display
) : Presentation(outerContext, display, android.R.style.Theme_Material_NoActionBar) {

    // Context aware với Display — phải lấy trong onCreate()
    private lateinit var displayCtx: Context

    // WebViews
    lateinit var mapWebView: WebView
        private set
    lateinit var mediaWebView: WebView
        private set

    // Tab state: true = YouTube, false = TikTok
    private var isYouTubeActive = true

    // AudioFocus
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Phát hiện tab đang active để steering wheel biết gửi lệnh đến đâu
    var activeTab: String = "youtube"
        private set

    // Các domain quảng cáo cần chặn
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

        // ⚠️ CRITICAL: lấy display-aware context từ Presentation, KHÔNG phải outerContext
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
        // Root: ngang, full màn hình
        val root = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }

        // Cột trái: Google Maps — 2/3 màn hình
        val mapContainer = FrameLayout(displayCtx).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
        }
        mapWebView = buildMapWebView()
        mapContainer.addView(
            mapWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Cột phải: YouTube/TikTok + tab bar — 1/3 màn hình
        val rightColumn = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        // Tab bar (YouTube | TikTok)
        val tabBar = buildTabBar()
        rightColumn.addView(tabBar)

        // Media WebView
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

        root.addView(mapContainer)
        root.addView(rightColumn)

        setContentView(root)

        // Tải trang mặc định
        loadYouTube()
        loadGoogleMaps()
    }

    private fun buildTabBar(): LinearLayout {
        val tabBar = LinearLayout(displayCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            setBackgroundColor(0xFF1A1A2E.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        val ytBtn = TextView(displayCtx).apply {
            text = "▶ YouTube"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(0xFF0F3460.toInt())
            setPadding(4, 0, 4, 0)
            tag = "yt_tab"
        }

        val tkBtn = TextView(displayCtx).apply {
            text = "♪ TikTok"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(0xFF0D0D0D.toInt())
            setPadding(4, 0, 4, 0)
            tag = "tk_tab"
        }

        ytBtn.setOnClickListener {
            isYouTubeActive = true
            activeTab = "youtube"
            ytBtn.setBackgroundColor(0xFF0F3460.toInt())
            ytBtn.setTextColor(0xFFFFFFFF.toInt())
            tkBtn.setBackgroundColor(0xFF0D0D0D.toInt())
            tkBtn.setTextColor(0xFFCCCCCC.toInt())
            loadYouTube()
        }

        tkBtn.setOnClickListener {
            isYouTubeActive = false
            activeTab = "tiktok"
            tkBtn.setBackgroundColor(0xFF69C9D0.toInt())
            tkBtn.setTextColor(0xFF000000.toInt())
            ytBtn.setBackgroundColor(0xFF0D0D0D.toInt())
            ytBtn.setTextColor(0xFFCCCCCC.toInt())
            loadTikTok()
        }

        tabBar.addView(ytBtn)
        tabBar.addView(tkBtn)
        return tabBar
    }

    // ─── WebView builders ─────────────────────────────────────────────────────

    private fun buildMapWebView(): WebView {
        val wv = WebView(displayCtx)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
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
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Mở Google Maps native khi bấm điều hướng
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
            // Cho phép lưu cookie đăng nhập
            allowFileAccess = true
            allowContentAccess = true
        }
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Bật cookie toàn cục để lưu đăng nhập YouTube/TikTok
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return blockAdRequest(request.url.toString())
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Inject script chặn quảng cáo YouTube
                if (url.contains("youtube.com")) {
                    injectYouTubeAdBlock(view)
                }
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                // Fullscreen video — chiếm toàn Presentation
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

    // ─── Load URLs ────────────────────────────────────────────────────────────

    private fun loadGoogleMaps() {
        // Google Maps embed tương tác đầy đủ
        mapWebView.loadUrl("https://www.google.com/maps/@?api=1&map_action=map")
    }

    private fun loadYouTube() {
        mediaWebView.loadUrl("https://m.youtube.com")
    }

    private fun loadTikTok() {
        mediaWebView.loadUrl("https://www.tiktok.com")
    }

    fun loadMediaUrl(url: String) {
        if (url.contains("tiktok.com")) {
            isYouTubeActive = false
            activeTab = "tiktok"
        } else {
            isYouTubeActive = true
            activeTab = "youtube"
        }
        mediaWebView.loadUrl(url)
    }

    // ─── Steering wheel controls ──────────────────────────────────────────────

    /**
     * Gọi từ CarStreamScreen khi người dùng bấm nút KEYCODE_MEDIA_NEXT
     */
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
        } else {
            // TikTok: vuốt lên để xem video tiếp theo
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    window.scrollBy({ top: window.innerHeight, behavior: 'smooth' });
                })()
                """.trimIndent(), null
            )
        }
    }

    /**
     * Gọi từ CarStreamScreen khi người dùng bấm nút KEYCODE_MEDIA_PREVIOUS
     */
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
        } else {
            // TikTok: vuốt xuống video trước
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    window.scrollBy({ top: -window.innerHeight, behavior: 'smooth' });
                })()
                """.trimIndent(), null
            )
        }
    }

    /**
     * Gọi khi bấm nút Play/Pause trên vô lăng
     */
    fun mediaPlayPause() {
        if (activeTab == "youtube") {
            mediaWebView.evaluateJavascript(
                """
                (function() {
                    var video = document.querySelector('video');
                    if (video) {
                        if (video.paused) video.play(); else video.pause();
                        return video.paused ? 'paused' : 'playing';
                    }
                    return 'no_video';
                })()
                """.trimIndent(), null
            )
        } else {
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
    }

    // ─── Touch forwarding ─────────────────────────────────────────────────────

    fun handleTouch(x: Float, y: Float) {
        val downTime = android.os.SystemClock.uptimeMillis()
        val eventDown = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val eventUp = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)

        val displayWidth = window?.decorView?.width ?: 1280
        val mapWidth = displayWidth * 2 / 3

        if (x < mapWidth) {
            mapWebView.dispatchTouchEvent(eventDown)
            mapWebView.dispatchTouchEvent(eventUp)
        } else {
            val adjustedX = x - mapWidth
            val eventDownAdj = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, adjustedX, y, 0)
            val eventUpAdj = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, adjustedX, y, 0)
            mediaWebView.dispatchTouchEvent(eventDownAdj)
            mediaWebView.dispatchTouchEvent(eventUpAdj)
            eventDownAdj.recycle()
            eventUpAdj.recycle()
        }
        eventDown.recycle()
        eventUp.recycle()
    }

    fun handleScroll(x: Float, y: Float, distanceX: Float, distanceY: Float) {
        val displayWidth = window?.decorView?.width ?: 1280
        val mapWidth = displayWidth * 2 / 3
        if (x < mapWidth) {
            mapWebView.scrollBy(distanceX.toInt(), distanceY.toInt())
        } else {
            mediaWebView.scrollBy(distanceX.toInt(), distanceY.toInt())
        }
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
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Tạm dừng video khi có cuộc gọi / trợ lý ảo
                mediaWebView.post {
                    mediaWebView.evaluateJavascript(
                        "document.querySelector('video')?.pause();", null
                    )
                }
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Tiếp tục phát sau khi cuộc gọi kết thúc
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
                // Ẩn quảng cáo overlay
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
                
                // Tự skip quảng cáo
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
                // Fallback: mở URL maps thông thường
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int {
        return (dp * displayCtx.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
