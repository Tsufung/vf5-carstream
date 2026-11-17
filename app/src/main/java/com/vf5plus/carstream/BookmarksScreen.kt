package com.vf5plus.carstream

import android.content.SharedPreferences
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BookmarksScreen(
    carContext: CarContext,
    private val streamScreen: CarStreamScreen
) : Screen(carContext) {

    companion object {
        const val PREFS_NAME = "vf5_carstream_prefs"
        const val KEY_BOOKMARKS = "bookmarks"
    }

    private val prefs: SharedPreferences by lazy {
        carContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    private fun getBookmarks(): List<Bookmark> {
        val json = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        val type = object : TypeToken<List<Bookmark>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    override fun onGetTemplate(): Template {
        val bookmarks = getBookmarks()

        val listBuilder = ItemList.Builder()

        if (bookmarks.isEmpty()) {
            listBuilder.setNoItemsMessage("Chưa có bookmark nào. Hãy thêm từ ứng dụng trên điện thoại.")
        } else {
            bookmarks.forEach { bookmark ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(bookmark.title)
                        .addText(bookmark.url)
                        .setOnClickListener {
                            streamScreen.loadUrl(bookmark.url)
                            screenManager.pop()
                        }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle("Bookmarks")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

data class Bookmark(
    val title: String,
    val url: String
)
