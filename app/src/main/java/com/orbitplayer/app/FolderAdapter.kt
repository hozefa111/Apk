package com.orbitplayer.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.AsyncListDiffer
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper

/** Album thumbnails are loaded and cached off the main thread. */
class FolderAdapter(
    private val context: Context,
    private val onOpen: (FolderAlbum) -> Unit,
    private val onOptions: (FolderAlbum) -> Unit
) : RecyclerView.Adapter<FolderAdapter.Holder>() {
    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<FolderAlbum>() {
        override fun areItemsTheSame(oldItem: FolderAlbum, newItem: FolderAlbum) =
            oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: FolderAlbum, newItem: FolderAlbum) =
            oldItem.count == newItem.count && oldItem.pinned == newItem.pinned &&
                oldItem.cover.uri == newItem.cover.uri &&
                oldItem.displayName == newItem.displayName
    })
    private val items: List<FolderAlbum> get() = differ.currentList
    private val decoding = ConcurrentHashMap.newKeySet<String>()
    private var disposed = false
    private val executor = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(10 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }
    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = items[position].path.hashCode().toLong()
    override fun getItemCount() = items.size
    fun submit(next: List<FolderAlbum>) {
        if (!disposed) differ.submitList(next.toList())
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_folder, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = items[position]
        holder.title.text = album.displayName
        holder.count.text = "${album.count} ${if (album.count == 1) "video" else "videos"}"
        holder.pin.visibility = if (album.pinned) View.VISIBLE else View.GONE
        // A folder's cover changes as media are added: cache by both path and cover URI.
        val key = album.path + "|" + album.cover.uri
        holder.thumb.setImageDrawable(null)
        holder.thumb.tag = key
        synchronized(cache) { cache.get(key) }?.let(holder.thumb::setImageBitmap) ?: run {
            if (decoding.add(key)) executor.execute {
                val bitmap = try {
                    if (Build.VERSION.SDK_INT >= 29)
                        context.contentResolver.loadThumbnail(album.cover.uri, Size(300, 300), null)
                    else {
                        val id = album.cover.uri.lastPathSegment?.toLongOrNull() ?: -1L
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null)
                    }
                } catch (_: Exception) { null }
                decoding.remove(key)
                if (bitmap != null) {
                    synchronized(cache) { cache.put(key, bitmap) }
                    handler.post {
                        if (!disposed) {
                            val position = items.indexOfFirst {
                                it.path == album.path && it.cover.uri == album.cover.uri
                            }
                            if (position >= 0) notifyItemChanged(position)
                        }
                    }
                }
            }
        }
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
    fun dispose() {
        disposed = true
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        synchronized(cache) { cache.evictAll() }
    }
    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val thumb: ImageView = root.findViewById(R.id.folderThumb)
        val title: TextView = root.findViewById(R.id.folderTitle)
        val count: TextView = root.findViewById(R.id.folderCount)
        val pin: TextView = root.findViewById(R.id.folderPinned)
        val more: View = root.findViewById(R.id.folderMore)
    }
}
