package com.vf5plus.carstream

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * SplitScreenPresentation — Màn hình chia đôi:
 *   LEFT  2/3: Google Maps WebView (maps.google.com embedded, full navigation)
 *   RIGHT 1/3: YouTube + TikTok WebView với tab chuyển đổi
 *
 * Điều khiển vô lăng (MediaNext/Prev) → gọi mediaNext() / mediaPrev()
 * Chặn quảng cáo toàn bộ
 * Lưu cookie đăng nhập tài khoản cá nhân
 */
@SuppressLint("SetJavaScriptEnabled")
class SplitScreenPresentation(
    context: Context,
    display: Display,
    private val lastMediaUrl: String?,
    private val onNavigationRequested: (String) -> Unit
) : Presentation(context, display) {

    enum class Panel { MAP, YOUTUBE, TIKTOK }

    companion object {
        private const val TAG = "SplitPresentation"

        // Chrome 124 Mobile UA — đủ tính năng YouTube/TikTok
        private const val USER_AGENT_MOBILE =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

        // Desktop UA cho Google Maps (renders full navigation UI)
        private const val USER_AGENT_MAPS =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

        private val AD_BLOCK_HOSTS = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "pagead2.googlesyndication.com", "adservice.google.com", "ads.youtube.com",
            "ad.youtube.com", "innovid.com", "imrworldwide.com", "moatads.com",
            "2mdn.net", "ads-twitter.com", "analytics.tiktok.com",
            "ad.tiktok.com", "ads.tiktok.com", "byteoversea.com",
            "log.byteoversea.com", "mssdk-oversea.byteoversea.com"
        )

        private val AD_BLOCK_URL_KEYWORDS = setOf(
            "/api/stats/ads", "/ptracking", "/pagead/", "/adview",
            "googlevideo.com/videoplayback?.*&ctier=L", // YouTube pre-roll ads
            "/ad_break_", "prebid.org"
        )

        const val URL_YOUTUBE = "https://m.youtube.com"
        const val URL_TIKTOK  = "https://www.tiktok.com"
        const val URL_MAPS    = "https://maps.google.com/maps?q=&layer=c&output=embed"
    }

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var rootLayout: LinearLayout
    private lateinit var mapContainer: FrameLayout
    private lateinit var mediaContainer: FrameLayout
    private lateinit var mapWebView: WebView
    private lateinit var youtubeWebView: WebView
    private lateinit var tiktokWebView: WebView
    private lateinit var tabBar: LinearLayout
    private lateinit var btnYtTab: android.widget.Button
    private lateinit var btnTikTokTab: android.widget.Button

    // ── State ────────────────────────────────────────────────────────────────
    private var activePanel: Panel = Panel.YOUTUBE
    private var audioFocusRequest: AudioFocusRequest? = null
    private var fullscreenView: View? = null
    private var originalSystemUiVisibility: Int = 0

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // ── Presentation Lifecycle ────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            setBackgroundDrawableResource(android.R.color.black)
        }
        buildLayout()
    }

    // ── Build UI: LinearLayout horizontal, 2/3 Map + 1/3 Media ──────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildLayout() {
        // Root: horizontal split
        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF000000.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── LEFT: Google Maps (weight 2 = 2/3) ───────────────────────────────
        mapContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 2f)
            setBackgroundColor(0xFF111111.toInt())
        }
        mapWebView = createMapWebView()
        mapContainer.addView(mapWebView)

        // ── RIGHT: Media panel (weight 1 = 1/3) ──────────────────────────────
        val mediaPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(0xFF0a0a0a.toInt())
        }

        // Tab bar: YouTube | TikTok
        tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0f).also {
                it.height = dpToPx(44)
            }
            setBackgroundColor(0xFF1a1a2e.toInt())
        }

        btnYtTab = android.widget.Button(context).apply {
            text = "▶ YouTube"
            setTextColor(0xFF00bfff.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt())
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            textSize = 11f
            setOnClickListener { switchMediaTab(Panel.YOUTUBE) }
        }
        btnTikTokTab = android.widget.Button(context).apply {
            text = "TikTok"
            setTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt())
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            textSize = 11f
            setOnClickListener { switchMediaTab(Panel.TIKTOK) }
        }

        tabBar.addView(btnYtTab)
        // Divider
        tabBar.addView(android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(2,
                ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF333355.toInt())
        })
        tabBar.addView(btnTikTokTab)

        // WebView container
        mediaContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        youtubeWebView = createMediaWebView(URL_YOUTUBE)
        tiktokWebView  = createMediaWebView(URL_TIKTOK).also { it.visibility = View.GONE }

        mediaContainer.addView(youtubeWebView)
        mediaContainer.addView(tiktokWebView)

        mediaPanel.addView(tabBar)
        mediaPanel.addView(mediaContainer)

        // Thin separator line between map and media
        rootLayout.addView(mapContainer)
        rootLayout.addView(android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(3,
                ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF00bfff.toInt())
        })
        rootLayout.addView(mediaPanel)

        setContentView(rootLayout)

        // Load initial URLs
        mapWebView.loadUrl(URL_MAPS)
        val mediaStartUrl = if (!lastMediaUrl.isNullOrBlank() && lastMediaUrl.startsWith("http"))
            lastMediaUrl else URL_YOUTUBE
        youtubeWebView.loadUrl(mediaStartUrl)
        tiktokWebView.loadUrl(URL_TIKTOK)
    }

    // ── Google Maps WebView ───────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun createMapWebView(): WebView {
        return WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            configureSettings(settings, USER_AGENT_MAPS)
            webViewClient = MapWebViewClient()
            webChromeClient = BaseWebChromeClient(this@SplitScreenPresentation.rootLayout)
            addJavascriptInterface(MapJsInterface(), "VF5Map")
        }
    }

    // ── YouTube / TikTok WebView ──────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun createMediaWebView(initialUrl: String): WebView {
        return WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            configureSettings(settings, USER_AGENT_MOBILE)
            webViewClient = MediaWebViewClient()
            webChromeClient = FullscreenWebChromeClient(this@SplitScreenPresentation.rootLayout)
        }
    }

    // ── Shared WebSettings ────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings(s: WebSettings, ua: String) {
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.userAgentString = ua
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.safeBrowsingEnabled = false
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────
    private fun switchMediaTab(panel: Panel) {
        activePanel = panel
        when (panel) {
            Panel.YOUTUBE -> {
                youtubeWebView.visibility = View.VISIBLE
                tiktokWebView.visibility  = View.GONE
                btnYtTab.setTextColor(0xFF00bfff.toInt())
                btnTikTokTab.setTextColor(0xFF888888.toInt())
                requestAudioFocus()
            }
            Panel.TIKTOK -> {
                youtubeWebView.visibility = View.GONE
                tiktokWebView.visibility  = View.VISIBLE
                btnYtTab.setTextColor(0xFF888888.toInt())
                btnTikTokTab.setTextColor(0xFFEE1D52.toInt())
                requestAudioFocus()
            }
            Panel.MAP -> {
                // Map is always visible on the left; just focus it (do nothing special)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun focusPanel(panel: Panel) = switchMediaTab(panel)

    fun reloadActive() {
        when (activePanel) {
            Panel.YOUTUBE -> youtubeWebView.post { youtubeWebView.reload() }
            Panel.TIKTOK  -> tiktokWebView.post  { tiktokWebView.reload() }
            Panel.MAP     -> mapWebView.post { mapWebView.reload() }
        }
    }

    fun loadMediaUrl(url: String) {
        youtubeWebView.post { youtubeWebView.loadUrl(url) }
        switchMediaTab(Panel.YOUTUBE)
    }

    fun getActiveMediaUrl(): String? {
        return when (activePanel) {
            Panel.YOUTUBE -> youtubeWebView.url
            Panel.TIKTOK  -> tiktokWebView.url
            Panel.MAP     -> mapWebView.url
        }
    }

    // ── Steering wheel: Next / Prev / Play-Pause ──────────────────────────────
    fun mediaNext() {
        val js = """
            (function() {
                // YouTube: click next button
                var ytNext = document.querySelector('.ytp-next-button, [aria-label*="next" i], [aria-label*="tiếp" i]');
                if (ytNext) { ytNext.click(); return; }
                // TikTok: scroll to next video
                window.scrollBy(0, window.innerHeight);
            })();
        """.trimIndent()
        activeMediaWebView()?.post { activeMediaWebView()?.evaluateJavascript(js, null) }
    }

    fun mediaPrev() {
        val js = """
            (function() {
                var ytPrev = document.querySelector('.ytp-prev-button, [aria-label*="previous" i], [aria-label*="trước" i]');
                if (ytPrev) { ytPrev.click(); return; }
                window.scrollBy(0, -window.innerHeight);
            })();
        """.trimIndent()
        activeMediaWebView()?.post { activeMediaWebView()?.evaluateJavascript(js, null) }
    }

    fun mediaPlayPause() {
        val js = """
            (function() {
                var v = document.querySelector('video');
                if (v) { if (v.paused) v.play(); else v.pause(); }
            })();
        """.trimIndent()
        activeMediaWebView()?.post { activeMediaWebView()?.evaluateJavascript(js, null) }
    }

    private fun activeMediaWebView(): WebView? = when (activePanel) {
        Panel.YOUTUBE -> youtubeWebView
        Panel.TIKTOK  -> tiktokWebView
        Panel.MAP     -> null
    }

    // ── AudioFocus ────────────────────────────────────────────────────────────
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { change ->
                    handleAudioFocusChange(change)
                }.build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { handleAudioFocusChange(it) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun handleAudioFocusChange(change: Int) {
        val pauseJs = "try{document.querySelectorAll('video,audio').forEach(function(m){m.pause();});}catch(e){}"
        val resumeJs = "try{var v=document.querySelector('video');if(v&&v.paused)v.play();}catch(e){}"
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                activeMediaWebView()?.post {
                    activeMediaWebView()?.evaluateJavascript(pauseJs, null)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                activeMediaWebView()?.post {
                    activeMediaWebView()?.evaluateJavascript(resumeJs, null)
                }
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    // ── Ad Blocking shared logic ──────────────────────────────────────────────
    private fun shouldBlockRequest(request: WebResourceRequest): Boolean {
        val host = request.url.host ?: ""
        val url  = request.url.toString()
        for (h in AD_BLOCK_HOSTS) if (host.contains(h) || url.contains(h)) return true
        for (kw in AD_BLOCK_URL_KEYWORDS) if (url.contains(kw)) return true
        return false
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8",
            java.io.ByteArrayInputStream(ByteArray(0)))

    // ── Google Maps WebViewClient ─────────────────────────────────────────────
    private inner class MapWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
            if (shouldBlockRequest(r)) return emptyResponse()
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // Intercept Google Maps navigation intent URIs
            if (url.startsWith("geo:") || url.contains("maps.google.com") ||
                url.contains("google.com/maps")) {
                // If it's a navigation/directions request, hand off to native Google Maps
                if (url.contains("navigate") || url.contains("dir=") || url.contains("?q=")) {
                    onNavigationRequested(url)
                    return true
                }
            }
            // Block non-maps navigation attempts
            if (!url.startsWith("http://") && !url.startsWith("https://")) return true
            return false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // Inject JS to intercept navigation clicks → send to native Google Maps
            val js = """
                (function() {
                    document.addEventListener('click', function(e) {
                        var target = e.target.closest('a[href]');
                        if (!target) return;
                        var href = target.href;
                        if (href && (href.includes('navigate=to') || href.includes('dir='))) {
                            e.preventDefault();
                            window.VF5Map.onNavigate(href);
                        }
                    }, true);
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        }
    }

    // ── Media WebViewClient (YouTube / TikTok) ────────────────────────────────
    private inner class MediaWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
            if (shouldBlockRequest(r)) {
                Log.d(TAG, "Blocked: ${r.url.host}")
                return emptyResponse()
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (url?.startsWith("http") == true) requestAudioFocus()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                view.loadUrl(url)
                return true
            }
            return true // Block intents (market://, intent://, etc.)
        }
    }

    // ── Base WebChromeClient ──────────────────────────────────────────────────
    private open inner class BaseWebChromeClient(
        protected val root: ViewGroup
    ) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {}
        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage) = true
    }

    // ── Fullscreen WebChromeClient for Media ──────────────────────────────────
    private inner class FullscreenWebChromeClient(
        root: ViewGroup
    ) : BaseWebChromeClient(root) {

        private var customView: View? = null
        private var customViewCallback: CustomViewCallback? = null

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }
            customView = view
            customViewCallback = callback
            fullscreenView = view
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            (root as? FrameLayout)?.addView(view) ?: rootLayout.addView(view)
            activeMediaWebView()?.visibility = View.INVISIBLE
            window?.decorView?.let {
                originalSystemUiVisibility = it.systemUiVisibility
                @Suppress("DEPRECATION")
                it.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        }

        override fun onHideCustomView() {
            customView?.let { (root as? FrameLayout)?.removeView(it) ?: rootLayout.removeView(it) }
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            fullscreenView = null
            activeMediaWebView()?.visibility = View.VISIBLE
            window?.decorView?.let {
                @Suppress("DEPRECATION")
                it.systemUiVisibility = originalSystemUiVisibility
            }
        }
    }

    // ── JavaScript Interface for Maps navigation ──────────────────────────────
    private inner class MapJsInterface {
        @JavascriptInterface
        fun onNavigate(url: String) {
            Log.d(TAG, "Map navigate request: $url")
            onNavigationRequested(url)
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    override fun onStop() {
        super.onStop()
        try {
            youtubeWebView.onPause()
            tiktokWebView.onPause()
            mapWebView.onPause()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        try {
            youtubeWebView.onResume()
            tiktokWebView.onResume()
            mapWebView.onResume()
        } catch (_: Exception) {}
    }
}
