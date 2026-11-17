package com.vf5plus.carstream

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vf5plus.carstream.databinding.ItemBookmarkBinding

class BookmarkAdapter(
    private val bookmarks: MutableList<Bookmark>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    inner class BookmarkViewHolder(val binding: ItemBookmarkBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding = ItemBookmarkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        holder.binding.tvBookmarkTitle.text = bookmark.title
        holder.binding.tvBookmarkUrl.text = bookmark.url
        holder.binding.btnDeleteBookmark.setOnClickListener {
            onDeleteClick(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = bookmarks.size
}
