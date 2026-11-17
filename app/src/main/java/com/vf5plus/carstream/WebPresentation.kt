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

@SuppressLint("SetJavaScriptEnabled")
class WebPresentation(
    context: Context,
    display: Display,
    private val initialUrl: String?
) : Presentation(context, display) {

    companion object {
        private const val TAG = "WebPresentation"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        val HOME_HTML = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <title>VF5 CarStream</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        background: #0a0a0a;
                        font-family: 'Segoe UI', Arial, sans-serif;
                        color: #ffffff;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 20px;
                    }
                    h1 {
                        font-size: 28px;
                        color: #00bfff;
                        margin-bottom: 10px;
                        text-align: center;
                        text-shadow: 0 0 10px rgba(0,191,255,0.5);
                    }
                    .subtitle {
                        font-size: 14px;
                        color: #888;
                        margin-bottom: 30px;
                        text-align: center;
                    }
                    .grid {
                        display: grid;
                        grid-template-columns: repeat(2, 1fr);
                        gap: 16px;
                        width: 100%;
                        max-width: 700px;
                        margin-bottom: 24px;
                    }
                    .app-btn {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        background: #1a1a2e;
                        border: 1px solid #2a2a4e;
                        border-radius: 16px;
                        padding: 24px 16px;
                        text-decoration: none;
                        color: #ffffff;
                        font-size: 18px;
                        font-weight: bold;
                        transition: all 0.2s;
                        cursor: pointer;
                        min-height: 100px;
                    }
                    .app-btn:active {
                        background: #2a2a5e;
                        transform: scale(0.97);
                    }
                    .app-btn .icon { font-size: 36px; margin-bottom: 8px; }
                    .youtube { border-color: #ff0000; }
                    .youtube:active { background: #2a0000; }
                    .ytmusic { border-color: #ff4081; }
                    .ytmusic:active { background: #2a0010; }
                    .vtvgo { border-color: #0066cc; }
                    .vtvgo:active { background: #00102a; }
                    .zing { border-color: #9b59b6; }
                    .zing:active { background: #1a0030; }
                    .fpt { border-color: #e67e22; }
                    .fpt:active { background: #2a1000; }
                    .google { border-color: #4285f4; }
                    .google:active { background: #001a40; }
                    .url-bar {
                        display: flex;
                        width: 100%;
                        max-width: 700px;
                        gap: 10px;
                        margin-top: 8px;
                    }
                    input[type="url"] {
                        flex: 1;
                        background: #1a1a2e;
                        border: 1px solid #2a2a4e;
                        border-radius: 10px;
                        padding: 14px 18px;
                        color: #ffffff;
                        font-size: 16px;
                        outline: none;
                    }
                    input[type="url"]:focus {
                        border-color: #00bfff;
                        box-shadow: 0 0 8px rgba(0,191,255,0.3);
                    }
                    input[type="url"]::placeholder { color: #555; }
                    button.go-btn {
                        background: #00bfff;
                        color: #000;
                        border: none;
                        border-radius: 10px;
                        padding: 14px 24px;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                    }
                    button.go-btn:active { background: #0099cc; }
                    .version {
                        margin-top: 20px;
                        font-size: 12px;
                        color: #444;
                    }
                </style>
            </head>
            <body>
                <h1>🚗 VF5 CarStream</h1>
                <p class="subtitle">VinFast VF5 Plus 2025 · Android Auto</p>
                <div class="grid">
                    <a class="app-btn youtube" href="https://m.youtube.com">
                        <span class="icon">▶️</span>YouTube
                    </a>
                    <a class="app-btn ytmusic" href="https://music.youtube.com">
                        <span class="icon">🎵</span>YouTube Music
                    </a>
                    <a class="app-btn vtvgo" href="https://vtvgo.vn">
                        <span class="icon">📺</span>VTV Go
                    </a>
                    <a class="app-btn zing" href="https://zingmp3.vn">
                        <span class="icon">🎶</span>Zing MP3
                    </a>
                    <a class="app-btn fpt" href="https://fptplay.vn">
                        <span class="icon">🎬</span>FPT Play
                    </a>
                    <a class="app-btn google" href="https://www.google.com">
                        <span class="icon">🔍</span>Google
                    </a>
                </div>
                <div class="url-bar">
                    <input type="url" id="urlInput" placeholder="Nhập địa chỉ website..." />
                    <button class="go-btn" onclick="navigate()">Mở</button>
                </div>
                <p class="version">VF5 CarStream v1.0.0</p>
                <script>
                    function navigate() {
                        var url = document.getElementById('urlInput').value.trim();
                        if (!url) return;
                        if (!url.startsWith('http://') && !url.startsWith('https://')) {
                            url = 'https://' + url;
                        }
                        window.location.href = url;
                    }
                    document.getElementById('urlInput').addEventListener('keypress', function(e) {
                        if (e.key === 'Enter') navigate();
                    });
                    document.querySelectorAll('.app-btn').forEach(function(btn) {
                        btn.addEventListener('click', function(e) {
                            e.preventDefault();
                            window.location.href = this.href;
                        });
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        private val AD_BLOCK_LIST = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "ads.youtube.com",
            "pagead2.googlesyndication.com",
            "adservice.google.com",
            "adsense.google.com",
            "ad.youtube.com",
            "youtube.com/api/stats/ads",
            "youtube.com/ptracking",
            "innovid.com",
            "imrworldwide.com",
            "moatads.com",
            "2mdn.net"
        )
    }

    lateinit var webView: WebView
        private set

    private var fullscreenView: View? = null
    private var originalSystemUiVisibility: Int = 0
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        configureWebSettings(webView.settings)

        webView.webViewClient = VF5WebViewClient()
        webView.webChromeClient = VF5WebChromeClient(rootLayout)

        rootLayout.addView(webView)
        setContentView(rootLayout)

        if (!initialUrl.isNullOrBlank() && initialUrl.startsWith("http")) {
            webView.loadUrl(initialUrl)
        } else {
            webView.loadDataWithBaseURL(
                "https://vf5carstream.local",
                HOME_HTML,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = USER_AGENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }
    }

    fun loadUrl(url: String) {
        webView.post {
            if (url == HOME_HTML || url.isEmpty()) {
                webView.loadDataWithBaseURL(
                    "https://vf5carstream.local",
                    HOME_HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
            } else {
                val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }
                webView.loadUrl(finalUrl)
            }
            requestAudioFocus()
        }
    }

    fun goBack() {
        webView.post {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }
    }

    fun reload() {
        webView.post { webView.reload() }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()

            audioFocusRequest = focusRequest
            val result = audioManager.requestAudioFocus(focusRequest)
            Log.d(TAG, "AudioFocus request result: $result")
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "AudioFocus lost - pausing")
                webView.post {
                    webView.evaluateJavascript(
                        "try { document.querySelectorAll('video, audio').forEach(function(m) { m.pause(); }); } catch(e) {}",
                        null
                    )
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "AudioFocus lost transient - pausing")
                webView.post {
                    webView.evaluateJavascript(
                        "try { document.querySelectorAll('video, audio').forEach(function(m) { m.pause(); }); } catch(e) {}",
                        null
                    )
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AudioFocus gained - resuming")
                webView.post {
                    webView.evaluateJavascript(
                        "try { var v = document.querySelector('video'); if(v && v.paused) v.play(); } catch(e) {}",
                        null
                    )
                }
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    override fun onStop() {
        super.onStop()
        abandonAudioFocus()
        webView.onPause()
    }

    override fun onStart() {
        super.onStart()
        webView.onResume()
    }

    private inner class VF5WebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            val host = request.url.host ?: ""

            for (adDomain in AD_BLOCK_LIST) {
                if (host.contains(adDomain) || url.contains(adDomain)) {
                    Log.d(TAG, "Blocked ad request: $url")
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (url != null && url.startsWith("http")) {
                requestAudioFocus()
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                view.loadUrl(url)
                return true
            }
            return false
        }
    }

    private inner class VF5WebChromeClient(
        private val rootLayout: FrameLayout
    ) : WebChromeClient() {

        private var customView: View? = null
        private var customViewCallback: CustomViewCallback? = null

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            Log.d(TAG, "onShowCustomView - entering fullscreen video")
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            fullscreenView = view

            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            rootLayout.addView(view)
            webView.visibility = View.INVISIBLE

            window?.decorView?.let { decorView ->
                originalSystemUiVisibility = decorView.systemUiVisibility
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }

            requestAudioFocus()
        }

        override fun onHideCustomView() {
            Log.d(TAG, "onHideCustomView - exiting fullscreen video")
            customView?.let { cv ->
                rootLayout.removeView(cv)
            }
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            fullscreenView = null
            webView.visibility = View.VISIBLE

            window?.decorView?.let { decorView ->
                decorView.systemUiVisibility = originalSystemUiVisibility
            }
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            Log.d(TAG, "Page load progress: $newProgress%")
        }

        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
            Log.d(TAG, "WebConsole [${consoleMessage.messageLevel()}]: ${consoleMessage.message()}")
            return true
        }
    }
}
