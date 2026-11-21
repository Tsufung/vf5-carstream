package com.vf5plus.carstream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
    private var locationManager: LocationManager? = null

    init {
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        } catch (e: Exception) {
            Log.e(TAG, "setSurfaceCallback failed: ${e.message}")
        }

        setupLocationUpdates()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                releaseLocationUpdates()
                releaseDisplay()
            }
        })
    }

    // ─── Template ─────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setTitle("🏠 Trang chủ")
                    .setOnClickListener {
                        Handler(Looper.getMainLooper()).post {
                            presentation?.mapWebView?.loadUrl("file:///android_asset/map.html")
                            presentation?.mediaWebView?.loadUrl("https://m.youtube.com")
                        }
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("💳 Mã QR")
                    .setOnClickListener {
                        Handler(Looper.getMainLooper()).post {
                            presentation?.toggleQr()
                        }
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setPanModeListener { isInPanMode ->
                Log.d(TAG, "PanMode: $isInPanMode")
            }
            .build()
    }

    // ─── SurfaceCallback ──────────────────────────────────────────────────────

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        val width = if (surfaceContainer.width > 0) surfaceContainer.width else 1280
        val height = if (surfaceContainer.height > 0) surfaceContainer.height else 720
        val dpi = if (surfaceContainer.dpi > 0) surfaceContainer.dpi else 213

        Handler(Looper.getMainLooper()).post {
            try {
                releaseDisplay()

                // ContextThemeWrapper bọc carContext
                val themedContext = ContextThemeWrapper(carContext, android.R.style.Theme_DeviceDefault_NoActionBar)
                val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

                val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY

                virtualDisplay = displayManager.createVirtualDisplay(
                    "VF5StreamDisplay",
                    width, height, dpi,
                    surface,
                    flags
                )

                val display = virtualDisplay?.display ?: return@post

                val p = SplitScreenPresentation(themedContext, display)
                p.show()
                presentation = p

                CarToast.makeText(carContext, "VF5 Stream: Đã kết nối thành công!", CarToast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.e(TAG, "Error initializing presentation: ${t.message}", t)
                try {
                    CarToast.makeText(carContext, "Lỗi khởi tạo màn hình: ${t.message}", CarToast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        releaseDisplay()
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {}
    override fun onStableAreaChanged(stableArea: android.graphics.Rect) {}

    // ─── Touch Events ─────────────────────────────────────────────────────────

    override fun onClick(x: Float, y: Float) {
        Handler(Looper.getMainLooper()).post {
            presentation?.dispatchClick(x, y)
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        Handler(Looper.getMainLooper()).post {
            presentation?.dispatchScroll(distanceX, distanceY)
        }
    }

    // ─── GPS Speed & Map Location ─────────────────────────────────────────────

    private fun setupLocationUpdates() {
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
            } catch (e: Exception) {
                Log.w(TAG, "GPS request failed: ${e.message}")
            }
        }
    }

    private fun releaseLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
            locationManager = null
        } catch (_: Exception) {}
    }

    override fun onLocationChanged(location: Location) {
        val rawSpeedKmh = (location.speed * 3.6f).toInt()
        Handler(Looper.getMainLooper()).post {
            presentation?.updateSpeed(rawSpeedKmh)
            presentation?.updateLocation(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.speed
            )
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ─── Display Cleanup ──────────────────────────────────────────────────────

    private fun releaseDisplay() {
        try {
            presentation?.dismiss()
            presentation = null
        } catch (_: Exception) {}
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (_: Exception) {}
    }
}
