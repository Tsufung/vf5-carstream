package com.vf5plus.carstream

import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.core.graphics.drawable.IconCompat

class CarStreamScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG = "CarStreamScreen"
        private const val PREFS_NAME = "vf5_carstream_prefs"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_AUTO_RESUME = "auto_resume"
        private const val VIRTUAL_DISPLAY_NAME = "VF5CarStreamDisplay"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var webPresentation: WebPresentation? = null
    private val prefs: SharedPreferences by lazy {
        carContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    override fun onGetTemplate(): Template {
        val surfaceController = carContext.getCarService(androidx.car.app.SurfaceContainer::class.java.name)

        val homeAction = Action.Builder()
            .setTitle("Home")
            .setOnClickListener {
                webPresentation?.loadUrl(WebPresentation.HOME_HTML)
            }
            .build()

        val bookmarksAction = Action.Builder()
            .setTitle("Bookmarks")
            .setOnClickListener {
                screenManager.push(BookmarksScreen(carContext, this))
            }
            .build()

        val backAction = Action.Builder()
            .setTitle("Back")
            .setOnClickListener {
                webPresentation?.goBack()
            }
            .build()

        val reloadAction = Action.Builder()
            .setTitle("Reload")
            .setOnClickListener {
                webPresentation?.reload()
            }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(homeAction)
            .addAction(bookmarksAction)
            .addAction(backAction)
            .addAction(reloadAction)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Surface available: ${surfaceContainer.width}x${surfaceContainer.height} dpi=${surfaceContainer.dpi}")

        val displayManager = carContext.getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager

        virtualDisplay = displayManager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi,
            surfaceContainer.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        )

        virtualDisplay?.display?.let { display ->
            val autoResume = prefs.getBoolean(KEY_AUTO_RESUME, true)
            val lastUrl = if (autoResume) prefs.getString(KEY_LAST_URL, null) else null
            webPresentation = WebPresentation(carContext, display, lastUrl)
            webPresentation?.show()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Surface destroyed")
        webPresentation?.webView?.url?.let { url ->
            if (url.startsWith("http")) {
                prefs.edit().putString(KEY_LAST_URL, url).apply()
                Log.d(TAG, "Saved last URL: $url")
            }
        }
        webPresentation?.dismiss()
        webPresentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
        Log.d(TAG, "Visible area changed: $visibleArea")
    }

    override fun onStableAreaChanged(stableArea: android.graphics.Rect) {
        Log.d(TAG, "Stable area changed: $stableArea")
    }

    fun loadUrl(url: String) {
        webPresentation?.loadUrl(url)
    }
}
