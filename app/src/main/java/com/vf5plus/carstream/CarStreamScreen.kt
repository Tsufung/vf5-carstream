package com.vf5plus.carstream

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class CarStreamScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG = "CarStreamScreen"
        const val PREFS_NAME = "vf5_carstream_prefs"
        const val KEY_LAST_URL = "last_url"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_ACTIVE_PANEL = "active_panel"
        private const val VIRTUAL_DISPLAY_NAME = "VF5SplitDisplay"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var splitPresentation: SplitScreenPresentation? = null
    private var mediaSession: MediaSessionCompat? = null
    private var surfaceContainer: SurfaceContainer? = null

    private val prefs: SharedPreferences by lazy {
        carContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    // ── 1. Register SurfaceCallback & setup MediaSession ──────────────────────
    init {
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(this)
        } catch (e: Exception) {
            Log.e(TAG, "setSurfaceCallback failed: ${e.message}")
        }
        setupMediaSession()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                mediaSession?.release()
                mediaSession = null
            }
        })
    }

    // ── 2. Build NavigationTemplate (required for Surface to be granted) ──────
    override fun onGetTemplate(): Template {
        val mapAction = Action.Builder()
            .setTitle("🗺 Bản đồ")
            .setOnClickListener { splitPresentation?.focusPanel(SplitScreenPresentation.Panel.MAP) }
            .build()

        val ytAction = Action.Builder()
            .setTitle("▶ YouTube")
            .setOnClickListener { splitPresentation?.focusPanel(SplitScreenPresentation.Panel.YOUTUBE) }
            .build()

        val tiktokAction = Action.Builder()
            .setTitle("TikTok")
            .setOnClickListener { splitPresentation?.focusPanel(SplitScreenPresentation.Panel.TIKTOK) }
            .build()

        val reloadAction = Action.Builder()
            .setTitle("↺ Tải lại")
            .setOnClickListener { splitPresentation?.reloadActive() }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(mapAction)
            .addAction(ytAction)
            .addAction(tiktokAction)
            .addAction(reloadAction)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    // ── 3. Surface ready → create VirtualDisplay → show SplitScreenPresentation ─
    override fun onSurfaceAvailable(container: SurfaceContainer) {
        Log.d(TAG, "Surface available ${container.width}x${container.height} dpi=${container.dpi}")
        this.surfaceContainer = container

        try {
            val dm = carContext.getSystemService(android.content.Context.DISPLAY_SERVICE)
                    as DisplayManager

            virtualDisplay = dm.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                container.width.coerceAtLeast(1),
                container.height.coerceAtLeast(1),
                container.dpi.coerceAtLeast(160),
                container.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            )

            virtualDisplay?.display?.let { display ->
                val autoResume = prefs.getBoolean(KEY_AUTO_RESUME, true)
                val lastUrl = if (autoResume) prefs.getString(KEY_LAST_URL, null) else null
                splitPresentation = SplitScreenPresentation(
                    context = carContext,
                    display = display,
                    lastMediaUrl = lastUrl,
                    onNavigationRequested = { geoUri ->
                        openGoogleMapsNavigation(geoUri)
                    }
                )
                splitPresentation?.show()
                Log.d(TAG, "SplitScreenPresentation shown")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onSurfaceAvailable error: ${e.message}", e)
        }
    }

    // ── 4. Surface destroyed → save state & clean up ─────────────────────────
    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        Log.d(TAG, "Surface destroyed")
        try {
            splitPresentation?.getActiveMediaUrl()?.let { url ->
                if (url.startsWith("http")) {
                    prefs.edit().putString(KEY_LAST_URL, url).apply()
                }
            }
            splitPresentation?.dismiss()
            splitPresentation = null
            virtualDisplay?.release()
            virtualDisplay = null
            surfaceContainer = null
        } catch (e: Exception) {
            Log.e(TAG, "onSurfaceDestroyed error: ${e.message}", e)
        }
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {}
    override fun onStableAreaChanged(stableArea: android.graphics.Rect) {}

    // ── 5. MediaSession for steering wheel button (KEYCODE_MEDIA_NEXT / PREV) ─
    private fun setupMediaSession() {
        try {
            mediaSession = MediaSessionCompat(carContext, "VF5CarStream").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        )
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                        .build()
                )
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onSkipToNext() {
                        Log.d(TAG, "Steering wheel: NEXT")
                        splitPresentation?.mediaNext()
                    }
                    override fun onSkipToPrevious() {
                        Log.d(TAG, "Steering wheel: PREV")
                        splitPresentation?.mediaPrev()
                    }
                    override fun onPlay() {
                        splitPresentation?.mediaPlayPause()
                    }
                    override fun onPause() {
                        splitPresentation?.mediaPlayPause()
                    }
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                        val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_NEXT -> { splitPresentation?.mediaNext(); return true }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { splitPresentation?.mediaPrev(); return true }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { splitPresentation?.mediaPlayPause(); return true }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession setup error: ${e.message}", e)
        }
    }

    // ── 6. Open Google Maps native app for navigation ─────────────────────────
    private fun openGoogleMapsNavigation(geoUri: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(geoUri)
                `package` = "com.google.android.apps.maps"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            carContext.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open in browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://maps.google.com/?q=${geoUri.removePrefix("geo:")}"))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                carContext.startActivity(browserIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot open Maps: ${ex.message}")
            }
        }
    }

    fun loadUrl(url: String) {
        splitPresentation?.loadMediaUrl(url)
    }
}
