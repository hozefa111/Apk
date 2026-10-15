package com.orbitplayer.app

/** In-memory queue for same-process Next/Previous; playback works without it after process restore. */
object PlaybackQueue {
    private var items: List<VideoItem> = emptyList()
    fun replace(newItems: List<VideoItem>) { items = newItems.toList() }
    fun adjacent(currentUri: String, delta: Int): VideoItem? {
        val index = items.indexOfFirst { it.uri.toString() == currentUri }
        if (index < 0) return null
        return items.getOrNull(index + delta)
    }
}
