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

    private var items: List<VideoItem> = emptyList()
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
        val old = items
        val next = videos.toList()
        val delta = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(a: Int, b: Int) = old[a].uri == next[b].uri
            override fun areContentsTheSame(a: Int, b: Int) = old[a] == next[b]
        }, true)
        items = next
        delta.dispatchUpdatesTo(this)
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
                        if (holder.thumb.tag == key) {
                            holder.thumb.setImageBitmap(bitmap)
                            holder.glyph.visibility = View.GONE
                        }
                    }
                }
            }
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
    }
}
