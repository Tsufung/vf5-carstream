package com.vf5plus.carstream

import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vf5plus.carstream.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "vf5_carstream_prefs"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var bookmarkAdapter: BookmarkAdapter
    private val gson = Gson()
    private val bookmarkList = mutableListOf<Bookmark>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupToolbar()
        setupBookmarkList()
        setupSwitches()
        setupButtons()
        loadSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "VF5 CarStream Settings"
    }

    private fun setupBookmarkList() {
        bookmarkAdapter = BookmarkAdapter(bookmarkList) { position ->
            showDeleteBookmarkDialog(position)
        }
        binding.recyclerBookmarks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bookmarkAdapter
        }
        loadBookmarks()
    }

    private fun setupSwitches() {
        binding.switchAutoResume.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_RESUME, isChecked).apply()
            val msg = if (isChecked) "Đã bật tự động mở lại video" else "Đã tắt tự động mở lại video"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, isChecked).apply()
            val msg = if (isChecked) "Đã bật giữ màn hình sáng" else "Đã tắt giữ màn hình sáng"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        binding.btnAddBookmark.setOnClickListener {
            showAddBookmarkDialog()
        }

        binding.btnClearCache.setOnClickListener {
            showClearCacheDialog()
        }

        binding.btnCopyAdbCommand.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(
                "ADB Install Command",
                """adb install -i "com.android.vending" app-debug.apk"""
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Đã copy lệnh ADB vào clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        binding.switchAutoResume.isChecked = prefs.getBoolean(KEY_AUTO_RESUME, true)
        binding.switchKeepScreenOn.isChecked = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
    }

    private fun loadBookmarks() {
        val json = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        val type = object : TypeToken<List<Bookmark>>() {}.type
        val list: List<Bookmark> = gson.fromJson(json, type) ?: emptyList()
        bookmarkList.clear()
        bookmarkList.addAll(list)
        bookmarkAdapter.notifyDataSetChanged()
    }

    private fun saveBookmarks() {
        val json = gson.toJson(bookmarkList)
        prefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    private fun showAddBookmarkDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_bookmark, null)
        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etBookmarkTitle)
        val etUrl = dialogView.findViewById<android.widget.EditText>(R.id.etBookmarkUrl)

        AlertDialog.Builder(this)
            .setTitle("Thêm Bookmark")
            .setView(dialogView)
            .setPositiveButton("Thêm") { _, _ ->
                val title = etTitle.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                        url
                    } else {
                        "https://$url"
                    }
                    bookmarkList.add(Bookmark(title, finalUrl))
                    bookmarkAdapter.notifyItemInserted(bookmarkList.size - 1)
                    saveBookmarks()
                    Toast.makeText(this, "Đã thêm bookmark: $title", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showDeleteBookmarkDialog(position: Int) {
        val bookmark = bookmarkList[position]
        AlertDialog.Builder(this)
            .setTitle("Xóa Bookmark")
            .setMessage("Bạn có chắc muốn xóa \"${bookmark.title}\"?")
            .setPositiveButton("Xóa") { _, _ ->
                bookmarkList.removeAt(position)
                bookmarkAdapter.notifyItemRemoved(position)
                saveBookmarks()
                Toast.makeText(this, "Đã xóa bookmark", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xóa Cache & Cookies")
            .setMessage("Bạn có chắc muốn xóa toàn bộ cache và cookies của WebView? Bạn sẽ phải đăng nhập lại các tài khoản.")
            .setPositiveButton("Xóa") { _, _ ->
                clearWebViewData()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun clearWebViewData() {
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
            Toast.makeText(this, "Đã xóa cache và cookies thành công!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khi xóa: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
