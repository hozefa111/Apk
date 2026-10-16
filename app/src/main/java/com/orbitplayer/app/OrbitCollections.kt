package com.orbitplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User collections stored locally. Only content URIs are recorded; never move/delete media.
 * Ordered playlists use JSON arrays (SharedPreferences string sets would lose playback order).
 */
class OrbitCollections(context: Context) {
    private val prefs = context.getSharedPreferences("orbit_collections_v1", Context.MODE_PRIVATE)

    fun favorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun isFavorite(uri: String) = uri in favorites()

    fun toggleFavorite(uri: String): Boolean {
        val all = favorites().toMutableSet()
        val added = if (all.contains(uri)) { all.remove(uri); false } else { all.add(uri); true }
        prefs.edit().putStringSet("favorites", all).apply()
        return added
    }

    private fun readPlaylists(): JSONObject =
        try { JSONObject(prefs.getString("playlists", "{}") ?: "{}") }
        catch (_: Exception) { JSONObject() }

    fun names(): List<String> {
        val data = readPlaylists()
        return buildList {
            val keys = data.keys()
            while (keys.hasNext()) add(keys.next())
        }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun items(name: String): List<String> {
        val array = readPlaylists().optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    fun add(name: String, uris: List<String>): Int {
        val title = name.trim().take(50)
        if (title.isEmpty()) return 0
        val data = readPlaylists()
        val old = items(title)
        val combined = (old + uris.filter { it.isNotBlank() }).distinct().take(4000)
        val array = JSONArray()
        combined.forEach { array.put(it) }
        data.put(title, array)
        prefs.edit().putString("playlists", data.toString()).apply()
        return combined.size - old.size
    }

    fun remove(name: String, uri: String) {
        val data = readPlaylists()
        if (!data.has(name)) return
        val values = items(name).filterNot { it == uri }
        val array = JSONArray()
        values.forEach { array.put(it) }
        data.put(name, array)
        prefs.edit().putString("playlists", data.toString()).apply()
    }

    /** Export is explicit; media URIs/playlist names leave device only if user saves this JSON. */
    fun exportJson(): String = readPlaylists().toString(2)

    /** Merge imported named playlists. Invalid/unsupported entries are ignored. */
    fun importJson(text: String): Int {
        require(text.length <= 5_000_000) { "Playlist file is too large" }
        val imported = JSONObject(text)
        val keys = imported.keys()
        var added = 0
        var namesSeen = 0
        while (keys.hasNext() && namesSeen++ < 100) {
            val key = keys.next().trim().take(50)
            if (key.isBlank()) continue
            val arr = imported.optJSONArray(key) ?: continue
            val uris = (0 until arr.length().coerceAtMost(4000))
                .map { arr.optString(it) }
                .filter { it.startsWith("content://") || it.startsWith("https://") }
            added += add(key, uris)
        }
        return added
    }

    fun delete(name: String) {
        val data = readPlaylists()
        data.remove(name)
        prefs.edit().putString("playlists", data.toString()).apply()
    }
}
