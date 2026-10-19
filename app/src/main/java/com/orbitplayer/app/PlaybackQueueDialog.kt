package com.orbitplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/** Virtualized queue UI. Only immutable metadata is displayed; media files/playlists are untouched. */
internal class PlaybackQueueDialog(
    private val owner: Activity,
    private val currentUri: () -> String,
    private val repeatOne: () -> Boolean,
    private val play: (VideoItem) -> Unit,
    private val changed: () -> Unit,
    private val restoredScroll: Parcelable? = null
) : Dialog(owner) {
    private lateinit var list: RecyclerView
    private lateinit var adapter: QueueAdapter
    private var menu: AlertDialog? = null
    private data class QueueRow(val video: VideoItem, val current: Boolean, val number: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.orbit_queue_sheet)
        setCanceledOnTouchOutside(true)
        setTitle(R.string.playing_queue)
        findViewById<View>(R.id.queueClose).setOnClickListener { dismiss() }
        list = findViewById(R.id.queueItems)
        list.layoutManager = LinearLayoutManager(owner)
        adapter = QueueAdapter()
        list.adapter = adapter
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.52f)
            decorView.setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, 0, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        refresh(initial = true)
    }

    override fun show() {
        super.show()
        resize()
    }

    fun resize() {
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, (owner.resources.displayMetrics.heightPixels * 0.78f).toInt())
    }

    fun scrollState(): Parcelable? = if (::list.isInitialized) list.layoutManager?.onSaveInstanceState() else null

    fun refresh(initial: Boolean = false) {
        if (!::adapter.isInitialized) return
        val videos = PlaybackQueue.snapshot()
        val current = currentUri()
        findViewById<TextView>(R.id.queueCount).text = owner.resources.getQuantityString(R.plurals.video_count, videos.size, videos.size)
        findViewById<Switch>(R.id.queueAutoplay).apply {
            setOnCheckedChangeListener(null)
            isChecked = PlaybackQueue.autoAdvance
            visibility = if (videos.size > 1) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, enabled ->
                PlaybackQueue.enableAutoAdvance(enabled)
                PlaybackQueue.persist(owner)
                changed()
            }
        }
        findViewById<View>(R.id.queueRepeatHint).visibility = if (repeatOne() && videos.size > 1) View.VISIBLE else View.GONE
        adapter.submitList(videos.mapIndexed { index, video -> QueueRow(video, video.uri.toString() == current, index + 1) }) {
            if (initial) {
                if (restoredScroll != null) list.layoutManager?.onRestoreInstanceState(restoredScroll)
                else list.scrollToPosition(videos.indexOfFirst { it.uri.toString() == current }.coerceAtLeast(0))
            }
        }
    }

    private fun edit(item: VideoItem) {
        val videos = PlaybackQueue.snapshot()
        val index = videos.indexOfFirst { it.uri == item.uri }
        if (index < 0) return
        val titles = mutableListOf(owner.getString(R.string.queue_play_now))
        val actions = mutableListOf<() -> Unit>({ dismiss(); play(item) })
        fun mutation(work: () -> Boolean) {
            if (work()) {
                PlaybackQueue.persist(owner)
                refresh()
                changed()
            }
        }
        if (index > 0) {
            titles.add(owner.getString(R.string.queue_move_up))
            actions.add { mutation { PlaybackQueue.move(item.uri.toString(), -1) } }
        }
        if (index < videos.lastIndex) {
            titles.add(owner.getString(R.string.queue_move_down))
            actions.add { mutation { PlaybackQueue.move(item.uri.toString(), 1) } }
        }
        if (item.uri.toString() != currentUri()) {
            titles.add(owner.getString(R.string.queue_remove))
            actions.add { mutation { PlaybackQueue.remove(item.uri.toString(), currentUri()) } }
        }
        menu?.dismiss()
        menu = AlertDialog.Builder(owner).setTitle(item.title)
            .setItems(titles.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    override fun dismiss() {
        menu?.dismiss()
        menu = null
        super.dismiss()
    }

    private inner class QueueAdapter : ListAdapter<QueueRow, QueueHolder>(object : DiffUtil.ItemCallback<QueueRow>() {
        override fun areItemsTheSame(oldItem: QueueRow, newItem: QueueRow) = oldItem.video.uri == newItem.video.uri
        override fun areContentsTheSame(oldItem: QueueRow, newItem: QueueRow) = oldItem == newItem
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            QueueHolder(LayoutInflater.from(parent.context).inflate(R.layout.row_queue, parent, false))
        override fun onBindViewHolder(holder: QueueHolder, position: Int) {
            val row = getItem(position)
            holder.title.text = row.video.title
            holder.title.setTextColor(owner.getColor(if (row.current) R.color.accent_cyan else R.color.text_main))
            holder.number.text = if (row.current) "▶" else row.number.toString()
            holder.details.text = listOfNotNull(
                owner.getString(R.string.queue_playing).takeIf { row.current },
                formatDuration(row.video.durationMs), row.video.folder.takeIf(String::isNotBlank)
            ).joinToString(" · ")
            holder.itemView.contentDescription = if (row.current) owner.getString(R.string.queue_item_current, row.video.title)
                else "${row.video.title}, ${formatDuration(row.video.durationMs)}, ${row.video.folder}"
            holder.itemView.setOnClickListener {
                val item = currentList.getOrNull(holder.bindingAdapterPosition) ?: return@setOnClickListener
                dismiss()
                play(item.video)
            }
            holder.more.contentDescription = owner.getString(R.string.queue_item_options, row.video.title)
            holder.more.setOnClickListener {
                currentList.getOrNull(holder.bindingAdapterPosition)?.let { edit(it.video) }
            }
        }
    }

    private class QueueHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.queueTitle)
        val details: TextView = view.findViewById(R.id.queueDetails)
        val number: TextView = view.findViewById(R.id.queueNumber)
        val more: View = view.findViewById(R.id.queueMore)
    }
}
