package com.orbitplayer.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

/**
 * Orbit's offline gallery: two view modes, stable IDs, bounded thumbnails and diff-based updates.
 * No thumbnail decoding on the main/UI thread.
 */
class VideoAdapter(
    private val context: Context,
    private val onOpen: (VideoItem) -> Unit,
    private val onLongPress: (VideoItem) -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.VideoHolder>() {

    // DiffUtil runs in the differ's background executor, never during a UI tap/search.
    private val differ = AsyncListDiffer(this, object : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem) =
            oldItem.uri == newItem.uri
        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem) =
            oldItem == newItem
    })
    private val items: List<VideoItem> get() = differ.currentList
    private var disposed = false
    private var galleryMode = true
    private val prefs = context.getSharedPreferences("playback_positions_v1", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2)
    private val decoding = ConcurrentHashMap.newKeySet<String>()
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    init { setHasStableIds(true) }
    override fun getItemId(position: Int): Long = items[position].uri.toString().hashCode().toLong()
    override fun getItemViewType(position: Int) = if (galleryMode) 1 else 0
    override fun getItemCount(): Int = items.size

    fun useGallery(gallery: Boolean) {
        if (galleryMode == gallery) return
        galleryMode = gallery
        notifyDataSetChanged() // Different ViewHolder layout / type is intentional.
    }

    fun refreshProgress() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, "progress")
    }

    fun submit(videos: List<VideoItem>) {
        if (!disposed) differ.submitList(videos.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoHolder {
        val layout = if (viewType == 1) R.layout.row_video else R.layout.row_video_list
        return VideoHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.details.text = if (galleryMode) {
            "${formatSize(item.sizeBytes)}  ·  ${item.folder}"
        } else {
            "${formatSize(item.sizeBytes)}  ·  ${item.folder}"
        }
        holder.duration.text = formatDuration(item.durationMs)
        bindProgress(holder, item)
        val key = item.uri.toString()
        holder.thumb.tag = key
        holder.thumb.setImageDrawable(null)
        holder.glyph.visibility = View.VISIBLE
        synchronized(cache) { cache.get(key) }?.let { image ->
            holder.thumb.setImageBitmap(image)
            holder.glyph.visibility = View.GONE
        } ?: run {
            if (decoding.add(key)) executor.execute {
                val bitmap = try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        context.contentResolver.loadThumbnail(item.uri, Size(384, 216), null)
                    } else {
                        val id = item.uri.lastPathSegment?.toLongOrNull() ?: -1L
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null)
                    }
                } catch (_: Exception) { null }
                decoding.remove(key)
                if (bitmap != null) {
                    synchronized(cache) { cache.put(key, bitmap) }
                    mainHandler.post {
                        if (!disposed) {
                            // More than one holder can request the same thumbnail during decoding.
                            // Rebind from cache so every holder is updated, including recycled ones.
                            val position = items.indexOfFirst { it.uri.toString() == key }
                            if (position >= 0) notifyItemChanged(position, "thumbnail")
                        }
                    }
                }
            }
        }
        holder.more.contentDescription = "Options for ${item.title}"
        holder.more.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION && current < items.size) onLongPress(items[current])
        }
        holder.itemView.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION && current < items.size) onOpen(items[current])
        }
        holder.itemView.setOnLongClickListener {
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION && current < items.size) onLongPress(items[current])
            true
        }
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == "progress" }) {
            bindProgress(holder, items[position])
        } else super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindProgress(holder: VideoHolder, item: VideoItem) {
        val current = prefs.getLong("position_${item.uri.toString().hashCode()}", 0L)
        val duration = item.durationMs
        val valid = duration > 0 && current >= 3_000L && current < duration - 5_000L
        holder.watchProgress.visibility = if (valid) View.VISIBLE else View.GONE
        holder.watchProgress.progress = if (valid)
            (current * 1000L / duration).toInt().coerceIn(0, 1000) else 0
    }

    fun dispose() {
        disposed = true
        executor.shutdownNow()
        synchronized(cache) { cache.evictAll() }
        mainHandler.removeCallbacksAndMessages(null)
    }

    class VideoHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val details: TextView = view.findViewById(R.id.details)
        val duration: TextView = view.findViewById(R.id.duration)
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val glyph: TextView = view.findViewById(R.id.playGlyph)
        val watchProgress: ProgressBar = view.findViewById(R.id.watchProgress)
        val more: View = view.findViewById(R.id.moreOptions)
    }
}
