package com.orbitplayer.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable playback queue. Playlist or "Play all" mode can advance on natural video end.
 * For normal file opening, next/previous remain available but auto advance is disabled.
 * Only video metadata and URIs are persisted, never the media bytes.
 */
object PlaybackQueue {
    private var items: List<VideoItem> = emptyList()
    private var restored = false
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    var autoAdvance = false
        private set

    fun replace(newItems: List<VideoItem>, playAll: Boolean = false) {
        restored = true
        items = newItems.toList()
        autoAdvance = playAll
    }

    fun persist(context: Context) {
        val snapshot = items
        val advance = autoAdvance
        val prefs = context.getSharedPreferences("orbit_queue_v1", Context.MODE_PRIVATE)
        writer.execute {
            val array = JSONArray()
            snapshot.forEach { item ->
                array.put(JSONObject().apply {
                    put("uri", item.uri.toString())
                    put("title", item.title)
                    put("duration", item.durationMs)
                    put("size", item.sizeBytes)
                    put("folder", item.folder)
                    put("date", item.modifiedSeconds)
                    put("width", item.width)
                    put("height", item.height)
                })
            }
            prefs.edit().putString("items", array.toString())
                .putBoolean("auto_advance", advance).apply()
        }
    }

    /** Only tests wait; production playback never blocks on queue serialization. */
    internal fun awaitPersistenceForTest() { writer.submit {}.get() }

    /** Called only after process recreation when the in-memory queue was lost. */
    fun ensureRestored(context: Context) {
        if (restored) return
        restored = true
        val prefs = context.getSharedPreferences("orbit_queue_v1", Context.MODE_PRIVATE)
        val json = prefs.getString("items", "[]") ?: "[]"
        try {
            val array = JSONArray(json)
            val loaded = ArrayList<VideoItem>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val raw = o.optString("uri")
                if (!raw.startsWith("content://") && !raw.startsWith("file://")) continue
                loaded.add(VideoItem(
                    Uri.parse(raw),
                    o.optString("title", "Video"),
                    o.optLong("duration", 0),
                    o.optLong("size", 0),
                    o.optString("folder", "Other"),
                    o.optLong("date", 0), o.optInt("width", 0), o.optInt("height", 0)
                ))
            }
            items = loaded
            autoAdvance = prefs.getBoolean("auto_advance", false)
        } catch (_: Exception) {
            items = emptyList()
            autoAdvance = false
        }
    }

    fun adjacent(currentUri: String, delta: Int): VideoItem? {
        val index = items.indexOfFirst { it.uri.toString() == currentUri }
        val next = QueueNavigation.adjacentIndex(index, items.size, delta)
        return if (next >= 0) items[next] else null
    }
}

/** Pure Kotlin navigation logic for deterministic local tests. */
object QueueNavigation {
    fun adjacentIndex(currentIndex: Int, total: Int, delta: Int): Int {
        if (currentIndex !in 0 until total) return -1
        val next = currentIndex.toLong() + delta
        return if (next >= 0 && next < total) next.toInt() else -1
    }

    fun shouldAutoAdvance(repeatOne: Boolean, autoAdvance: Boolean, hasNext: Boolean): Boolean =
        !repeatOne && autoAdvance && hasNext
}
