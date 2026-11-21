package com.vf5plus.carstream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

private const val TAG = "CarStreamScreen"

class CarStreamScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, LocationListener {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: SplitScreenPresentation? = null
    private var mediaSession: MediaSessionCompat? = null
    private var locationManager: LocationManager? = null
    private val config: VF5Config by lazy { VF5Config(carContext.applicationContext) }

    init {
        // Đăng ký SurfaceCallback
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(this)
        } catch (e: Exception) {
            Log.e(TAG, "setSurfaceCallback failed: ${e.message}")
        }

        // Khởi tạo MediaSession cho nút vô lăng
        setupMediaSession()

        // Khởi tạo GPS đo tốc độ cho HUD
        setupLocationUpdates()

        // Dọn dẹp khi Screen bị hủy
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                releaseLocationUpdates()
                releaseDisplay()
                releaseMediaSession()
            }
        })
    }

    // ─── GPS Speedometer ──────────────────────────────────────────────────────

    private fun setupLocationUpdates() {
        if (!config.isSpeedHudEnabled) return

        val hasFine = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val provider = if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                    LocationManager.GPS_PROVIDER
                } else {
                    LocationManager.NETWORK_PROVIDER
                }
                locationManager?.requestLocationUpdates(provider, 1000L, 1.0f, this)
                Log.d(TAG, "Location updates requested from $provider")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request location updates: ${e.message}")
            }
        }
    }

    private fun releaseLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
            locationManager = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove location updates: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        val rawSpeedKmh = location.speed * 3.6f
        val offset = config.speedOffsetPercent
        val adjustedSpeed = (rawSpeedKmh * (1f + offset / 100f)).toInt().coerceAtLeast(0)

        carContext.mainExecutor.execute {
            presentation?.updateSpeed(adjustedSpeed)
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ─── MediaSession vô lăng ─────────────────────────────────────────────────

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
                        carContext.mainExecutor.execute { presentation?.mediaNext() }
                    }

                    override fun onSkipToPrevious() {
                        carContext.mainExecutor.execute { presentation?.mediaPrev() }
                    }

                    override fun onPlay() {
                        carContext.mainExecutor.execute { presentation?.mediaPlayPause() }
                    }

                    override fun onPause() {
                        carContext.mainExecutor.execute { presentation?.mediaPlayPause() }
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
                    .setTitle("💳 Mã QR")
                    .setOnClickListener { onToggleQrClicked() }
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
        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width.takeIf { it > 0 } ?: 1280
        val height = surfaceContainer.height.takeIf { it > 0 } ?: 720
        val dpi = surfaceContainer.dpi.takeIf { it > 0 } ?: 213

        releaseDisplay()

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

            val display = virtualDisplay?.display ?: return

            carContext.mainExecutor.execute {
                try {
                    val p = SplitScreenPresentation(
                        outerContext = carContext.applicationContext,
                        display = display
                    )
                    p.show()
                    presentation = p
                } catch (e: Exception) {
                    Log.e(TAG, "Presentation.show() failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createVirtualDisplay failed: ${e.message}", e)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        releaseDisplay()
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {}
    override fun onStableAreaChanged(stableArea: android.graphics.Rect) {}

    // ─── External Controls ───────────────────────────────────────────────────

    fun loadUrl(url: String) {
        carContext.mainExecutor.execute {
            presentation?.loadMediaUrl(url)
        }
    }

    // ─── Action handlers ──────────────────────────────────────────────────────

    private fun onHomeClicked() {
        carContext.mainExecutor.execute {
            presentation?.loadGoogleMaps()
            presentation?.loadYouTube()
        }
    }

    private fun onToggleQrClicked() {
        carContext.mainExecutor.execute {
            presentation?.togglePaymentQr()
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
    }
}
