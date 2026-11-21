package com.vf5plus.carstream

import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
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
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

private const val TAG = "CarStreamScreen"

class CarStreamScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: SplitScreenPresentation? = null
    private var mediaSession: MediaSessionCompat? = null

    init {
        // Đăng ký SurfaceCallback — PHẢI gọi trước onGetTemplate()
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(this)
        } catch (e: Exception) {
            Log.e(TAG, "setSurfaceCallback failed: ${e.message}")
        }

        // Khởi tạo MediaSessionCompat để bắt các nút bấm điều khiển trên vô lăng (Next/Prev/Play/Pause)
        setupMediaSession()

        // Dọn dẹp khi Screen bị hủy
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                releaseDisplay()
                releaseMediaSession()
            }
        })
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSessionCompat(carContext, "VF5CarStreamSession").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )

                val stateBuilder = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)

                setPlaybackState(stateBuilder.build())

                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onSkipToNext() {
                        Log.d(TAG, "MediaSession: onSkipToNext (Vô lăng chuyển bài tiếp)")
                        carContext.mainExecutor.execute {
                            presentation?.mediaNext()
                        }
                    }

                    override fun onSkipToPrevious() {
                        Log.d(TAG, "MediaSession: onSkipToPrevious (Vô lăng lùi bài)")
                        carContext.mainExecutor.execute {
                            presentation?.mediaPrev()
                        }
                    }

                    override fun onPlay() {
                        Log.d(TAG, "MediaSession: onPlay")
                        carContext.mainExecutor.execute {
                            presentation?.mediaPlayPause()
                        }
                    }

                    override fun onPause() {
                        Log.d(TAG, "MediaSession: onPause")
                        carContext.mainExecutor.execute {
                            presentation?.mediaPlayPause()
                        }
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent?): Boolean {
                        val keyEvent = mediaButtonIntent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    carContext.mainExecutor.execute { presentation?.mediaNext() }
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    carContext.mainExecutor.execute { presentation?.mediaPrev() }
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY,
                                KeyEvent.KEYCODE_MEDIA_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                KeyEvent.KEYCODE_HEADSETHOOK -> {
                                    carContext.mainExecutor.execute { presentation?.mediaPlayPause() }
                                    return true
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })

                isActive = true
            }
            Log.d(TAG, "MediaSessionCompat initialized and active")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaSessionCompat: ${e.message}", e)
        }
    }

    private fun releaseMediaSession() {
        try {
            mediaSession?.let {
                it.isActive = false
                it.release()
            }
            mediaSession = null
        } catch (e: Exception) {
            Log.w(TAG, "mediaSession release error: ${e.message}")
        }
    }

    // ─── Template ─────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🏠 Trang chủ")
                    .setOnClickListener { onHomeClicked() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🗺️ Bản đồ")
                    .setOnClickListener { onMapClicked() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⏮ Trước")
                    .setOnClickListener { onPrevClicked() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⏭ Tiếp")
                    .setOnClickListener { onNextClicked() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🔄 Tải lại")
                    .setOnClickListener { onReloadClicked() }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    // ─── SurfaceCallback ──────────────────────────────────────────────────────

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "onSurfaceAvailable: ${surfaceContainer.width}x${surfaceContainer.height} dpi=${surfaceContainer.dpi}")

        val surface = surfaceContainer.surface ?: run {
            Log.e(TAG, "Surface is null!")
            return
        }
        val width = surfaceContainer.width.takeIf { it > 0 } ?: 1280
        val height = surfaceContainer.height.takeIf { it > 0 } ?: 720
        val dpi = surfaceContainer.dpi.takeIf { it > 0 } ?: 213

        // Giải phóng VirtualDisplay cũ nếu có
        releaseDisplay()

        // Tạo VirtualDisplay gắn với Surface của màn hình xe
        val displayManager = carContext.applicationContext
            .getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager

        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY

            virtualDisplay = displayManager.createVirtualDisplay(
                "VF5CarStream",
                width, height, dpi,
                surface,
                flags
            )

            val display = virtualDisplay?.display ?: run {
                Log.e(TAG, "VirtualDisplay.display is null!")
                return
            }

            // Tạo Presentation trên main thread (UI thread)
            carContext.mainExecutor.execute {
                try {
                    val p = SplitScreenPresentation(
                        outerContext = carContext.applicationContext,
                        display = display
                    )
                    p.show()
                    presentation = p
                    Log.d(TAG, "Presentation shown successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Presentation.show() failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed: ${e.message}", e)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "onSurfaceDestroyed")
        releaseDisplay()
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
        Log.d(TAG, "onVisibleAreaChanged: $visibleArea")
    }

    override fun onStableAreaChanged(stableArea: android.graphics.Rect) {
        Log.d(TAG, "onStableAreaChanged: $stableArea")
    }

    // ─── External Controls ───────────────────────────────────────────────────

    fun loadUrl(url: String) {
        carContext.mainExecutor.execute {
            presentation?.loadMediaUrl(url)
        }
    }

    // ─── Action handlers ──────────────────────────────────────────────────────

    private fun onHomeClicked() {
        carContext.mainExecutor.execute {
            presentation?.mapWebView?.loadUrl("https://www.google.com/maps/@?api=1&map_action=map")
            presentation?.mediaWebView?.loadUrl("https://m.youtube.com")
        }
    }

    private fun onMapClicked() {
        carContext.mainExecutor.execute {
            presentation?.mapWebView?.loadUrl("https://www.google.com/maps/@?api=1&map_action=map")
        }
    }

    private fun onNextClicked() {
        carContext.mainExecutor.execute {
            presentation?.mediaNext()
        }
    }

    private fun onPrevClicked() {
        carContext.mainExecutor.execute {
            presentation?.mediaPrev()
        }
    }

    private fun onReloadClicked() {
        carContext.mainExecutor.execute {
            presentation?.mapWebView?.reload()
            presentation?.mediaWebView?.reload()
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private fun releaseDisplay() {
        try {
            carContext.mainExecutor.execute {
                presentation?.dismiss()
                presentation = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "presentation dismiss error: ${e.message}")
        }
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.w(TAG, "virtualDisplay release error: ${e.message}")
        }
        Log.d(TAG, "releaseDisplay done")
    }
}
