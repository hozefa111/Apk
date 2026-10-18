package com.orbitplayer.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.AsyncListDiffer

/** Album thumbnails are loaded and cached off the main thread. */
class FolderAdapter(
    private val context: Context,
    private val onOpen: (FolderAlbum) -> Unit,
    private val thumbnails: ThumbnailLoader,
    private val onOptions: (FolderAlbum) -> Unit
) : RecyclerView.Adapter<FolderAdapter.Holder>() {
    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<FolderAlbum>() {
        override fun areItemsTheSame(oldItem: FolderAlbum, newItem: FolderAlbum) =
            oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: FolderAlbum, newItem: FolderAlbum) =
            oldItem.count == newItem.count && oldItem.pinned == newItem.pinned &&
                oldItem.cover == newItem.cover &&
                oldItem.displayName == newItem.displayName
    })
    private val items: List<FolderAlbum> get() = differ.currentList
    private var disposed = false
    init { setHasStableIds(false) }
    override fun getItemId(position: Int) = items[position].path.hashCode().toLong()
    override fun getItemCount() = items.size
    fun submit(next: List<FolderAlbum>, committed: () -> Unit = {}) {
        if (!disposed) differ.submitList(next.toList(), committed)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_folder, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = items[position]
        holder.title.text = album.displayName
        holder.count.text = "${album.count} ${if (album.count == 1) "video" else "videos"}"
        holder.pin.visibility = if (album.pinned) View.VISIBLE else View.GONE
        holder.path.text = album.path.substringBeforeLast('/', "Internal storage")
        holder.itemView.contentDescription = "${album.displayName}, ${album.count} videos, ${album.path}"
        holder.glyph.visibility = View.VISIBLE
        thumbnails.load(holder.thumb, album.cover) { holder.glyph.visibility = View.GONE }
        holder.more.contentDescription = "Options for ${album.displayName}"
        holder.more.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it in items.indices }?.let { onOptions(items[it]) }
        }
        holder.itemView.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it in items.indices }?.let { onOpen(items[it]) }
        }
        holder.itemView.setOnLongClickListener {
            holder.bindingAdapterPosition.takeIf { it in items.indices }?.let { onOptions(items[it]) }
            true
        }
    }
    override fun onViewRecycled(holder: Holder) {
        thumbnails.clear(holder.thumb)
        super.onViewRecycled(holder)
    }
    fun dispose() { disposed = true }
    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val thumb: ImageView = root.findViewById(R.id.folderThumb)
        val title: TextView = root.findViewById(R.id.folderTitle)
        val count: TextView = root.findViewById(R.id.folderCount)
        val pin: TextView = root.findViewById(R.id.folderPinned)
        val glyph: View = root.findViewById(R.id.folderGlyph)
        val path: TextView = root.findViewById(R.id.folderPath)
        val more: View = root.findViewById(R.id.folderMore)
    }
}
