package com.orbitplayer.app

import android.content.ContentResolver
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
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** Bounded thumbnail cache. Heavy video thumbnail decoding never blocks scrolling. */
class VideoAdapter(
    private val context: Context,
    private val onOpen: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {
    private var items = listOf<VideoItem>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun submit(videos: List<VideoItem>) {
        items = videos.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.details.text = "${formatDuration(item.durationMs)}  ·  ${formatSize(item.sizeBytes)}  ·  ${item.folder}"
        val key = item.uri.toString()
        holder.thumb.tag = key
        holder.thumb.setImageDrawable(null)
        holder.glyph.visibility = View.VISIBLE
        cache.get(key)?.let {
            holder.thumb.setImageBitmap(it)
            holder.glyph.visibility = View.GONE
        } ?: run {
            executor.execute {
                val bmp = try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        context.contentResolver.loadThumbnail(item.uri, Size(192, 108), null)
                    } else {
                        val id = item.uri.lastPathSegment?.toLongOrNull() ?: -1
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null)
                    }
                } catch (_: Exception) { null }
                if (bmp != null) {
                    cache.put(key, bmp)
                    mainHandler.post {
                        if (holder.thumb.tag == key) {
                            holder.thumb.setImageBitmap(bmp)
                            holder.glyph.visibility = View.GONE
                        }
                    }
                }
            }
        }
        holder.itemView.setOnClickListener { onOpen(item) }
    }

    fun dispose() { executor.shutdownNow(); cache.evictAll() }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val details: TextView = itemView.findViewById(R.id.details)
        val thumb: ImageView = itemView.findViewById(R.id.thumb)
        val glyph: TextView = itemView.findViewById(R.id.playGlyph)
    }
}
