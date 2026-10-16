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
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

/** Album thumbnails are loaded and cached off the main thread. */
class FolderAdapter(
    private val context: Context,
    private val onOpen: (FolderAlbum) -> Unit,
    private val onOptions: (FolderAlbum) -> Unit
) : RecyclerView.Adapter<FolderAdapter.Holder>() {
    private var items = emptyList<FolderAlbum>()
    private val executor = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(10 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }
    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = items[position].path.hashCode().toLong()
    override fun getItemCount() = items.size
    fun submit(next: List<FolderAlbum>) {
        items = next
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_folder, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = items[position]
        holder.title.text = album.displayName
        holder.count.text = "${album.count} ${if (album.count == 1) "video" else "videos"}"
        holder.pin.visibility = if (album.pinned) View.VISIBLE else View.GONE
        holder.thumb.setImageDrawable(null)
        holder.thumb.tag = album.path
        synchronized(cache) { cache.get(album.path) }?.let(holder.thumb::setImageBitmap) ?: run {
            executor.execute {
                val bitmap = try {
                    if (Build.VERSION.SDK_INT >= 29)
                        context.contentResolver.loadThumbnail(album.cover.uri, Size(300, 300), null)
                    else {
                        val id = album.cover.uri.lastPathSegment?.toLongOrNull() ?: -1L
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null)
                    }
                } catch (_: Exception) { null }
                if (bitmap != null) {
                    synchronized(cache) { cache.put(album.path, bitmap) }
                    handler.post {
                        if (holder.thumb.tag == album.path) holder.thumb.setImageBitmap(bitmap)
                    }
                }
            }
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
        executor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        synchronized(cache) { cache.evictAll() }
    }
    class Holder(root: View) : RecyclerView.ViewHolder(root) {
        val thumb: ImageView = root.findViewById(R.id.folderThumb)
        val title: TextView = root.findViewById(R.id.folderTitle)
        val count: TextView = root.findViewById(R.id.folderCount)
        val pin: TextView = root.findViewById(R.id.folderPinned)
    }
}
