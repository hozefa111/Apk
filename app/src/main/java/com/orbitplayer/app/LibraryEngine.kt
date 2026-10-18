package com.orbitplayer.app

import java.util.Locale

/** Immutable metadata for library computation and deterministic large-library tests. */
internal data class LibraryEntry(
    val key: String, val title: String, val folder: String,
    val durationMs: Long, val sizeBytes: Long, val modifiedSeconds: Long
)

internal data class LibraryQuery(
    val text: String = "", val folder: String = "All folders", val format: String = "",
    val filter: String = "ALL", val sort: String = "NEWEST",
    val hidden: Set<String> = emptySet(), val favorites: Set<String> = emptySet(),
    val history: List<String> = emptyList(), val playlist: List<String>? = null,
    val positions: Map<String, Long> = emptyMap(),
    val nowSeconds: Long = System.currentTimeMillis() / 1000
)

internal data class LibraryAlbum(val path: String, val items: List<LibraryEntry>, val pinned: Boolean) {
    val cover: LibraryEntry = items.maxBy { it.modifiedSeconds }
}

/** Call on a worker. Stable tie breakers make refreshes predictable. No Android I/O. */
internal object LibraryEngine {
    fun videos(source: List<LibraryEntry>, query: LibraryQuery): List<LibraryEntry> {
        val playlist = query.playlist?.withIndex()?.associate { it.value to it.index }
        val history = query.history.withIndex().associate { it.value to it.index }
        val filtered = source.asSequence().filter { entry ->
            entry.folder !in query.hidden &&
                (query.folder == "All folders" || entry.folder == query.folder) &&
                (query.format.isEmpty() || videoExtension(entry.title) == query.format) &&
                (playlist == null || entry.key in playlist) &&
                (query.text.isBlank() || entry.title.contains(query.text, true) || entry.folder.contains(query.text, true)) &&
                when (query.filter) {
                    "FAVORITES" -> entry.key in query.favorites
                    "HISTORY" -> entry.key in history
                    "CONTINUE" -> hasResumePosition(query.positions[entry.key] ?: 0, entry.durationMs)
                    "RECENT" -> entry.modifiedSeconds >= query.nowSeconds - 30L * 24 * 60 * 60
                    else -> true
                }
        }
        val order = when {
            playlist != null -> compareBy<LibraryEntry> { playlist[it.key] ?: Int.MAX_VALUE }
            query.filter == "HISTORY" -> compareBy { history[it.key] ?: Int.MAX_VALUE }
            query.sort == "OLDEST" -> compareBy { it.modifiedSeconds }
            query.sort == "NAME" -> compareBy { it.title.lowercase(Locale.ROOT) }
            query.sort == "SIZE" -> compareByDescending { it.sizeBytes }
            query.sort == "DURATION" -> compareByDescending { it.durationMs }
            else -> compareByDescending { it.modifiedSeconds }
        }
        return filtered.sortedWith(order.thenBy { it.key }).toList()
    }

    fun albums(source: List<LibraryEntry>, text: String, hidden: Set<String>, pinned: Set<String>, sort: Int): List<LibraryAlbum> {
        val groups = source.asSequence().filter { it.folder !in hidden }.groupBy { it.folder }
        val albums = groups.filter { (path, entries) -> text.isBlank() || path.contains(text, true) ||
            entries.any { it.title.contains(text, true) } }.map { (path, entries) -> LibraryAlbum(path, entries, path in pinned) }
        val pinnedFirst = compareByDescending<LibraryAlbum> { it.pinned }
        val order = when (sort) {
            1 -> pinnedFirst.thenByDescending { it.items.size }
            2 -> pinnedFirst.thenByDescending { it.cover.modifiedSeconds }
            else -> pinnedFirst.thenBy { it.path.substringAfterLast('/').lowercase(Locale.ROOT) }
        }
        return albums.sortedWith(order.thenBy { it.path })
    }
}

internal fun hasResumePosition(position: Long, duration: Long): Boolean =
    duration > 0 && position >= 3_000 && position < duration - 5_000

/** Keep primary-storage keys unchanged so existing hidden/pinned folders survive updates. */
internal fun mediaFolder(relativePath: String?, volume: String?, bucket: String?): String {
    val path = relativePath?.trim('/')?.takeIf { it.isNotBlank() }
        ?: bucket?.takeIf { it.isNotBlank() } ?: "Other"
    return if (volume.isNullOrBlank() || volume == "external" || volume == "external_primary") path
        else "$volume/$path"
}
