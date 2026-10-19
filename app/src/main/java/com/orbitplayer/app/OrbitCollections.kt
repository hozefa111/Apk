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

    fun rename(name: String, newName: String): Boolean {
        val title = newName.trim().take(50)
        val data = readPlaylists()
        if (title.isBlank() || !data.has(name)) return false
        if (title == name) return true
        if (data.has(title)) return false
        data.put(title, data.getJSONArray(name))
        data.remove(name)
        prefs.edit().putString("playlists", data.toString()).apply()
        return true
    }

    fun delete(name: String) {
        val data = readPlaylists()
        data.remove(name)
        prefs.edit().putString("playlists", data.toString()).apply()
    }
}
