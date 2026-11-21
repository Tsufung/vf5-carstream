package com.vf5plus.carstream

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class VF5Config(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "vf5_carstream_prefs"

        // Screen ratio options
        const val RATIO_SPLIT_2_3 = "split_2_3"   // 2/3 Maps, 1/3 Media (Default Grab/XanhSM)
        const val RATIO_SPLIT_1_2 = "split_1_2"   // 50/50 Balanced
        const val RATIO_FULL_MAP = "full_map"     // 100% Maps
        const val RATIO_FULL_MEDIA = "full_media" // 100% Media

        // Theme options
        const val THEME_AUTO = "auto"
        const val THEME_PHONE = "phone"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"

        // Toolbar options
        const val TOOLBAR_FIXED = "fixed"
        const val TOOLBAR_FLOATING = "floating"
        const val TOOLBAR_AUTO_HIDE = "auto_hide"

        // File names
        private const val QR_FILE_NAME = "payment_qr.png"
        private const val CAR_FILE_NAME = "car_avatar.png"
    }

    var screenRatio: String
        get() = prefs.getString("screen_ratio", RATIO_SPLIT_2_3) ?: RATIO_SPLIT_2_3
        set(value) = prefs.edit().putString("screen_ratio", value).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", THEME_PHONE) ?: THEME_PHONE
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var toolbarMode: String
        get() = prefs.getString("toolbar_mode", TOOLBAR_FIXED) ?: TOOLBAR_FIXED
        set(value) = prefs.edit().putString("toolbar_mode", value).apply()

    var isSpeedHudEnabled: Boolean
        get() = prefs.getBoolean("speed_hud_enabled", true)
        set(value) = prefs.edit().putBoolean("speed_hud_enabled", value).apply()

    var speedOffsetPercent: Int
        get() = prefs.getInt("speed_offset_percent", 0)
        set(value) = prefs.edit().putInt("speed_offset_percent", value).apply()

    var isCallAutoPauseEnabled: Boolean
        get() = prefs.getBoolean("call_auto_pause", true)
        set(value) = prefs.edit().putBoolean("call_auto_pause", value).apply()

    var isCallRingtonePhone: Boolean
        get() = prefs.getBoolean("call_ringtone_phone", true)
        set(value) = prefs.edit().putBoolean("call_ringtone_phone", value).apply()

    // Apps selection
    var isYouTubeEnabled: Boolean
        get() = prefs.getBoolean("app_youtube_enabled", true)
        set(value) = prefs.edit().putBoolean("app_youtube_enabled", value).apply()

    var isTikTokEnabled: Boolean
        get() = prefs.getBoolean("app_tiktok_enabled", true)
        set(value) = prefs.edit().putBoolean("app_tiktok_enabled", value).apply()

    var isVtvGoEnabled: Boolean
        get() = prefs.getBoolean("app_vtvgo_enabled", true)
        set(value) = prefs.edit().putBoolean("app_vtvgo_enabled", value).apply()

    var isFptPlayEnabled: Boolean
        get() = prefs.getBoolean("app_fptplay_enabled", false)
        set(value) = prefs.edit().putBoolean("app_fptplay_enabled", value).apply()

    // ─── Image management ─────────────────────────────────────────────────────

    fun saveQrImageFromUri(uri: Uri): Boolean {
        return try {
            val file = File(context.filesDir, QR_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putBoolean("has_custom_qr", true).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasCustomQr(): Boolean {
        val file = File(context.filesDir, QR_FILE_NAME)
        return file.exists() && file.length() > 0 && prefs.getBoolean("has_custom_qr", false)
    }

    fun getQrBitmap(): Bitmap? {
        val file = File(context.filesDir, QR_FILE_NAME)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    fun deleteQrImage() {
        val file = File(context.filesDir, QR_FILE_NAME)
        if (file.exists()) file.delete()
        prefs.edit().putBoolean("has_custom_qr", false).apply()
    }

    fun saveCarImageFromUri(uri: Uri): Boolean {
        return try {
            val file = File(context.filesDir, CAR_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putBoolean("has_custom_car", true).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasCustomCar(): Boolean {
        val file = File(context.filesDir, CAR_FILE_NAME)
        return file.exists() && file.length() > 0 && prefs.getBoolean("has_custom_car", false)
    }

    fun getCarBitmap(): Bitmap? {
        val file = File(context.filesDir, CAR_FILE_NAME)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    fun deleteCarImage() {
        val file = File(context.filesDir, CAR_FILE_NAME)
        if (file.exists()) file.delete()
        prefs.edit().putBoolean("has_custom_car", false).apply()
    }
}
