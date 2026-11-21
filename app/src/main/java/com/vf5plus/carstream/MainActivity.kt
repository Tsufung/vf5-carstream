package com.vf5plus.carstream

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vf5plus.carstream.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: VF5Config

    // Photo pickers
    private val pickQrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val success = config.saveQrImageFromUri(uri)
            if (success) {
                updateQrPreview()
                Toast.makeText(this, "Đã lưu mã QR thanh toán!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Không thể lưu ảnh QR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickCarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val success = config.saveCarImageFromUri(uri)
            if (success) {
                updateCarPreview()
                Toast.makeText(this, "Đã lưu ảnh xe!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Không thể lưu ảnh xe", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission request for GPS speed
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            config.isSpeedHudEnabled = true
            binding.switchSpeedHud.isChecked = true
            Toast.makeText(this, "Đã cấp quyền GPS thành công!", Toast.LENGTH_SHORT).show()
        } else {
            config.isSpeedHudEnabled = false
            binding.switchSpeedHud.isChecked = false
            Toast.makeText(this, "Cần quyền GPS để đo tốc độ xe", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = VF5Config(this)

        setupRatioButtons()
        setupThemeButtons()
        setupToolbarButtons()
        setupImages()
        setupSpeedHud()
        setupCallSettings()
        setupAppToggles()
        setupClearCache()
    }

    // ─── 1. Ratio buttons ─────────────────────────────────────────────────────

    private fun setupRatioButtons() {
        updateRatioUi(config.screenRatio)

        binding.btnRatio23.setOnClickListener {
            config.screenRatio = VF5Config.RATIO_SPLIT_2_3
            updateRatioUi(VF5Config.RATIO_SPLIT_2_3)
        }
        binding.btnRatio12.setOnClickListener {
            config.screenRatio = VF5Config.RATIO_SPLIT_1_2
            updateRatioUi(VF5Config.RATIO_SPLIT_1_2)
        }
        binding.btnRatioFullMap.setOnClickListener {
            config.screenRatio = VF5Config.RATIO_FULL_MAP
            updateRatioUi(VF5Config.RATIO_FULL_MAP)
        }
        binding.btnRatioFullMedia.setOnClickListener {
            config.screenRatio = VF5Config.RATIO_FULL_MEDIA
            updateRatioUi(VF5Config.RATIO_FULL_MEDIA)
        }
    }

    private fun updateRatioUi(selectedRatio: String) {
        val activeColor = ContextCompat.getColor(this, R.color.colorAccentWarm)
        val inactiveColor = ContextCompat.getColor(this, R.color.pillInactive)
        val activeTextColor = Color.WHITE
        val inactiveTextColor = ContextCompat.getColor(this, R.color.pillInactiveText)

        val buttons = mapOf(
            VF5Config.RATIO_SPLIT_2_3 to binding.btnRatio23,
            VF5Config.RATIO_SPLIT_1_2 to binding.btnRatio12,
            VF5Config.RATIO_FULL_MAP to binding.btnRatioFullMap,
            VF5Config.RATIO_FULL_MEDIA to binding.btnRatioFullMedia
        )

        buttons.forEach { (ratio, btn) ->
            if (ratio == selectedRatio) {
                btn.setBackgroundColor(activeColor)
                btn.setTextColor(activeTextColor)
            } else {
                btn.setBackgroundColor(inactiveColor)
                btn.setTextColor(inactiveTextColor)
            }
        }
    }

    // ─── 2. Theme buttons ─────────────────────────────────────────────────────

    private fun setupThemeButtons() {
        updateThemeUi(config.themeMode)

        binding.btnThemeAuto.setOnClickListener {
            config.themeMode = VF5Config.THEME_AUTO
            updateThemeUi(VF5Config.THEME_AUTO)
        }
        binding.btnThemePhone.setOnClickListener {
            config.themeMode = VF5Config.THEME_PHONE
            updateThemeUi(VF5Config.THEME_PHONE)
        }
        binding.btnThemeDark.setOnClickListener {
            config.themeMode = VF5Config.THEME_DARK
            updateThemeUi(VF5Config.THEME_DARK)
        }
    }

    private fun updateThemeUi(selectedTheme: String) {
        val activeColor = ContextCompat.getColor(this, R.color.colorAccentWarm)
        val inactiveColor = ContextCompat.getColor(this, R.color.pillInactive)
        val activeTextColor = Color.WHITE
        val inactiveTextColor = ContextCompat.getColor(this, R.color.pillInactiveText)

        val buttons = mapOf(
            VF5Config.THEME_AUTO to binding.btnThemeAuto,
            VF5Config.THEME_PHONE to binding.btnThemePhone,
            VF5Config.THEME_DARK to binding.btnThemeDark
        )

        buttons.forEach { (theme, btn) ->
            if (theme == selectedTheme) {
                btn.setBackgroundColor(activeColor)
                btn.setTextColor(activeTextColor)
            } else {
                btn.setBackgroundColor(inactiveColor)
                btn.setTextColor(inactiveTextColor)
            }
        }
    }

    // ─── 3. Toolbar buttons ───────────────────────────────────────────────────

    private fun setupToolbarButtons() {
        updateToolbarUi(config.toolbarMode)

        binding.btnToolbarFixed.setOnClickListener {
            config.toolbarMode = VF5Config.TOOLBAR_FIXED
            updateToolbarUi(VF5Config.TOOLBAR_FIXED)
        }
        binding.btnToolbarFloating.setOnClickListener {
            config.toolbarMode = VF5Config.TOOLBAR_FLOATING
            updateToolbarUi(VF5Config.TOOLBAR_FLOATING)
        }
        binding.btnToolbarAutoHide.setOnClickListener {
            config.toolbarMode = VF5Config.TOOLBAR_AUTO_HIDE
            updateToolbarUi(VF5Config.TOOLBAR_AUTO_HIDE)
        }
    }

    private fun updateToolbarUi(selectedMode: String) {
        val activeColor = ContextCompat.getColor(this, R.color.colorAccentWarm)
        val inactiveColor = ContextCompat.getColor(this, R.color.pillInactive)
        val activeTextColor = Color.WHITE
        val inactiveTextColor = ContextCompat.getColor(this, R.color.pillInactiveText)

        val buttons = mapOf(
            VF5Config.TOOLBAR_FIXED to binding.btnToolbarFixed,
            VF5Config.TOOLBAR_FLOATING to binding.btnToolbarFloating,
            VF5Config.TOOLBAR_AUTO_HIDE to binding.btnToolbarAutoHide
        )

        buttons.forEach { (mode, btn) ->
            if (mode == selectedMode) {
                btn.setBackgroundColor(activeColor)
                btn.setTextColor(activeTextColor)
            } else {
                btn.setBackgroundColor(inactiveColor)
                btn.setTextColor(inactiveTextColor)
            }
        }
    }

    // ─── 4. Images (QR & Car) ─────────────────────────────────────────────────

    private fun setupImages() {
        updateQrPreview()
        updateCarPreview()

        binding.btnPickQr.setOnClickListener {
            pickQrLauncher.launch("image/*")
        }
        binding.btnResetQr.setOnClickListener {
            config.deleteQrImage()
            updateQrPreview()
            Toast.makeText(this, "Đã xóa ảnh QR", Toast.LENGTH_SHORT).show()
        }

        binding.btnPickCar.setOnClickListener {
            pickCarLauncher.launch("image/*")
        }
        binding.btnResetCar.setOnClickListener {
            config.deleteCarImage()
            updateCarPreview()
            Toast.makeText(this, "Đã xóa ảnh xe", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateQrPreview() {
        val bitmap = config.getQrBitmap()
        if (bitmap != null) {
            binding.ivQrPreview.setImageBitmap(bitmap)
        } else {
            binding.ivQrPreview.setImageDrawable(null)
            binding.ivQrPreview.setBackgroundColor(ContextCompat.getColor(this, R.color.pillInactive))
        }
    }

    private fun updateCarPreview() {
        val bitmap = config.getCarBitmap()
        if (bitmap != null) {
            binding.ivCarPreview.setImageBitmap(bitmap)
        } else {
            binding.ivCarPreview.setImageDrawable(null)
            binding.ivCarPreview.setBackgroundColor(ContextCompat.getColor(this, R.color.pillInactive))
        }
    }

    // ─── 5. Speed HUD ─────────────────────────────────────────────────────────

    private fun setupSpeedHud() {
        binding.switchSpeedHud.isChecked = config.isSpeedHudEnabled
        binding.switchSpeedHud.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Kiểm tra quyền GPS
                val hasFine = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasFine) {
                    requestLocationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    config.isSpeedHudEnabled = true
                }
            } else {
                config.isSpeedHudEnabled = false
            }
        }

        // SeekBar bù tốc độ: 0 -> 20 tương ứng -10% -> +10%
        val currentOffset = config.speedOffsetPercent
        binding.seekBarSpeedOffset.progress = currentOffset + 10
        updateSpeedOffsetText(currentOffset)

        binding.seekBarSpeedOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 10
                config.speedOffsetPercent = offset
                updateSpeedOffsetText(offset)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateSpeedOffsetText(offset: Int) {
        val sign = if (offset > 0) "+$offset%" else "$offset%"
        binding.tvSpeedOffset.text = "Bù tốc độ theo đồng hồ xe: $sign"
    }

    // ─── 6. Call settings ─────────────────────────────────────────────────────

    private fun setupCallSettings() {
        binding.switchCallAutoPause.isChecked = config.isCallAutoPauseEnabled
        binding.switchCallAutoPause.setOnCheckedChangeListener { _, isChecked ->
            config.isCallAutoPauseEnabled = isChecked
        }

        binding.switchCallRingtone.isChecked = config.isCallRingtonePhone
        binding.switchCallRingtone.setOnCheckedChangeListener { _, isChecked ->
            config.isCallRingtonePhone = isChecked
        }
    }

    // ─── 7. App toggles ───────────────────────────────────────────────────────

    private fun setupAppToggles() {
        binding.switchAppYouTube.isChecked = config.isYouTubeEnabled
        binding.switchAppYouTube.setOnCheckedChangeListener { _, isChecked ->
            config.isYouTubeEnabled = isChecked
        }

        binding.switchAppTikTok.isChecked = config.isTikTokEnabled
        binding.switchAppTikTok.setOnCheckedChangeListener { _, isChecked ->
            config.isTikTokEnabled = isChecked
        }

        binding.switchAppVtvGo.isChecked = config.isVtvGoEnabled
        binding.switchAppVtvGo.setOnCheckedChangeListener { _, isChecked ->
            config.isVtvGoEnabled = isChecked
        }

        binding.switchAppFptPlay.isChecked = config.isFptPlayEnabled
        binding.switchAppFptPlay.setOnCheckedChangeListener { _, isChecked ->
            config.isFptPlayEnabled = isChecked
        }
    }

    // ─── 8. Clear cache ───────────────────────────────────────────────────────

    private fun setupClearCache() {
        binding.btnClearCache.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Xóa Cache & Đăng xuất")
                .setMessage("Bạn có chắc muốn xóa toàn bộ cookies và cache? Bạn sẽ cần đăng nhập lại YouTube và TikTok.")
                .setPositiveButton("Xóa") { _, _ ->
                    try {
                        val webView = WebView(this)
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().apply {
                            removeAllCookies(null)
                            flush()
                        }
                        Toast.makeText(this, "Đã xóa toàn bộ cache và cookies!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }
}
