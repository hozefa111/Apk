package com.orbitplayer.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Privacy-first user preferences. Stored on-device and applied on the next playback.
 * Never changes or moves the user's original videos.
 */
class OrbitSettings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("orbit_preferences_v2", Context.MODE_PRIVATE)
    var autoRefresh: Boolean
        get() = prefs.getBoolean("auto_refresh", true)
        set(value) = prefs.edit().putBoolean("auto_refresh", value).apply()
    var defaultTab: String
        get() = prefs.getString("default_tab", "Folders") ?: "Folders"
        set(value) = prefs.edit().putString("default_tab", value).apply()
    var amoled: Boolean
        get() = prefs.getBoolean("amoled", true)
        set(value) = prefs.edit().putBoolean("amoled", value).apply()
    var gallery: Boolean
        get() = prefs.getBoolean("gallery", true)
        set(value) = prefs.edit().putBoolean("gallery", value).apply()
    var folderColumns: Int
        get() = prefs.getInt("folder_columns", 3).coerceIn(2,4)
        set(value) = prefs.edit().putInt("folder_columns", value.coerceIn(2,4)).apply()
    var sort: Int
        get() = prefs.getInt("sort", 0).coerceIn(0,3)
        set(value) = prefs.edit().putInt("sort", value.coerceIn(0,3)).apply()
    var resume: Boolean
        get() = prefs.getBoolean("resume", true)
        set(value) = prefs.edit().putBoolean("resume", value).apply()
    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(value) = prefs.edit().putBoolean("autoplay", value).apply()
    var repeat: Boolean
        get() = prefs.getBoolean("repeat", false)
        set(value) = prefs.edit().putBoolean("repeat", value).apply()
    var seekSeconds: Int
        get() = prefs.getInt("seek_seconds", 10).coerceIn(5,30)
        set(value) = prefs.edit().putInt("seek_seconds", value).apply()
    var controlsTimeout: Int
        get() = prefs.getInt("controls_timeout", 4).coerceIn(2,10)
        set(value) = prefs.edit().putInt("controls_timeout", value).apply()
    var gestureVolume: Boolean
        get() = prefs.getBoolean("gesture_volume", true)
        set(value) = prefs.edit().putBoolean("gesture_volume", value).apply()
    var gestureBrightness: Boolean
        get() = prefs.getBoolean("gesture_brightness", true)
        set(value) = prefs.edit().putBoolean("gesture_brightness", value).apply()
    var gestureDoubleTap: Boolean
        get() = prefs.getBoolean("gesture_double_tap", true)
        set(value) = prefs.edit().putBoolean("gesture_double_tap", value).apply()
    var gestureHoldSpeed: Boolean
        get() = prefs.getBoolean("gesture_hold_speed", true)
        set(value) = prefs.edit().putBoolean("gesture_hold_speed", value).apply()
    var subtitleScale: Float
        get() = prefs.getFloat("subtitle_scale", 1f).coerceIn(0.75f,1.6f)
        set(value) = prefs.edit().putFloat("subtitle_scale", value.coerceIn(0.75f,1.6f)).apply()
    var preferredAudio: String
        get() = prefs.getString("preferred_audio", "") ?: ""
        set(value) = prefs.edit().putString("preferred_audio", value).apply()
    var preferredSubtitle: String
        get() = prefs.getString("preferred_subtitle", "") ?: ""
        set(value) = prefs.edit().putString("preferred_subtitle", value).apply()
    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1f).coerceIn(0.5f,2f)
        set(value) = prefs.edit().putFloat("playback_speed", value.coerceIn(0.5f,2f)).apply()
    var defaultOrientation: String
        get() = prefs.getString("default_orientation", "Automatic") ?: "Automatic"
        set(value) = prefs.edit().putString("default_orientation", value).apply()
    var lastTab: String
        get() = prefs.getString("last_tab", "") ?: ""
        set(value) = prefs.edit().putString("last_tab", value).apply()
    var lastFolder: String
        get() = prefs.getString("last_folder", "All folders") ?: "All folders"
        set(value) = prefs.edit().putString("last_folder", value).apply()

    private fun setKey(name: String): String = "folders_" + name
    fun folders(name: String): Set<String> = prefs.getStringSet(setKey(name), emptySet())?.toSet() ?: emptySet()
    fun hasFolder(name: String, path: String) = path in folders(name)
    fun setFolder(name: String, path: String, enabled: Boolean) {
        val values = folders(name).toMutableSet()
        if (enabled) values.add(path) else values.remove(path)
        prefs.edit().putStringSet(setKey(name), values).apply()
    }
}
