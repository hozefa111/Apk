package com.orbitplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Every displayed preference has a working local effect. Settings are kept private/on-device.
 * Explicit updates are the only user-initiated network action.
 */
class SettingsActivity : Activity() {
    private lateinit var settings: OrbitSettings
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private val white = Color.rgb(239, 243, 254)
    private val muted = Color.rgb(151, 169, 200)
    private val accent = Color.rgb(103, 213, 236)
    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()
    private fun surface(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(20, 30, 51))
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.rgb(46, 61, 91))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = OrbitSettings(this)
        window.statusBarColor = Color.rgb(8, 10, 16)
        window.navigationBarColor = Color.rgb(8, 10, 16)
        scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(25))
            setBackgroundColor(Color.rgb(8, 10, 16))
        }
        scroll.addView(content)
        setContentView(scroll)
        build()
    }

    private fun label(text: String, size: Float, bold: Boolean = false, color: Int = white) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun build() {
        val position = scroll.scrollY
        content.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(17))
        }
        val back = label("‹", 38f, color = white).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(43), dp(48))
            setOnClickListener { finish() }
        }
        header.addView(back)
        header.addView(label("Settings", 27f, true).apply {
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)
        content.addView(label("Customize Orbit Player Pro", 13f, color = muted).apply {
            setPadding(dp(5), 0, 0, dp(14))
        })
        section("APPEARANCE")
        toggle("AMOLED black", "Pure black background for dark viewing", settings.amoled) {
            settings.amoled = it
        }
        choose("Default opening screen", settings.defaultTab,
            listOf("Folders", "Videos", "Continue", "Search")) { settings.defaultTab = it }
        choose("Folder gallery columns", "${settings.folderColumns} columns",
            listOf("2 columns", "3 columns", "4 columns")) {
            settings.folderColumns = it.substringBefore(' ').toInt()
        }
        toggle("Video thumbnail grid", "Turn off for a compact list view", settings.gallery) {
            settings.gallery = it
        }

        section("VIDEO PLAYBACK")
        choose("Default video orientation", settings.defaultOrientation,
            listOf("Automatic", "Landscape", "Portrait")) { settings.defaultOrientation = it }
        toggle("Resume playback", "Continue videos from the saved position", settings.resume) {
            settings.resume = it
        }
        toggle("Autoplay", "Start playback immediately when opening a video", settings.autoplay) {
            settings.autoplay = it
        }
        toggle("Repeat current video", "Loop the video when it ends", settings.repeat) {
            settings.repeat = it
        }
        choose("Skip duration", "${settings.seekSeconds} seconds",
            listOf("5 seconds", "10 seconds", "15 seconds", "30 seconds")) {
            settings.seekSeconds = it.substringBefore(' ').toInt()
        }
        choose("Controls auto-hide", "${settings.controlsTimeout} seconds",
            listOf("2 seconds", "3 seconds", "4 seconds", "5 seconds", "7 seconds", "10 seconds")) {
            settings.controlsTimeout = it.substringBefore(' ').toInt()
        }
        choose("Default playback speed", "${settings.playbackSpeed}×",
            listOf("0.5×", "0.75×", "1.0×", "1.25×", "1.5×", "2.0×")) {
            settings.playbackSpeed = it.substringBefore('×').toFloat()
        }

        section("TOUCH GESTURES")
        toggle("Swipe for volume", "Adjust volume from the right side of the video",
            settings.gestureVolume) { settings.gestureVolume = it }
        toggle("Swipe for brightness", "Adjust brightness from the left side of the video",
            settings.gestureBrightness) { settings.gestureBrightness = it }
        toggle("Double tap to seek", "Seek backward or forward on the video",
            settings.gestureDoubleTap) { settings.gestureDoubleTap = it }
        toggle("Hold for 2× speed", "Speed up temporarily while holding a finger",
            settings.gestureHoldSpeed) { settings.gestureHoldSpeed = it }

        section("SUBTITLES & AUDIO")
        choose("Subtitle text size", "${(settings.subtitleScale * 100).toInt()}%",
            listOf("75%", "90%", "100%", "120%", "140%", "160%")) {
            settings.subtitleScale = it.removeSuffix("%").toFloat() / 100f
        }
        val languages = linkedMapOf("Auto" to "", "English" to "en", "Hindi" to "hi",
            "Gujarati" to "gu", "Arabic" to "ar", "French" to "fr", "Japanese" to "ja")
        choose("Preferred audio language",
            languages.entries.find { it.value == settings.preferredAudio }?.key ?: "Auto",
            languages.keys.toList()) { settings.preferredAudio = languages[it] ?: "" }
        choose("Preferred subtitle language",
            languages.entries.find { it.value == settings.preferredSubtitle }?.key ?: "Auto",
            languages.keys.toList()) { settings.preferredSubtitle = languages[it] ?: "" }

        section("LIBRARY & FOLDERS")
        row("Hidden folders", "${settings.folders("hidden").size} hidden · Manage visibility") {
            manageFolders("hidden", "Unhide folders")
        }
        row("Pinned folders", "${settings.folders("pinned").size} pinned · Manage favorites") {
            manageFolders("pinned", "Unpin folders")
        }
        toggle("Automatically refresh library", "Update Albums when videos are added or removed", settings.autoRefresh) {
            settings.autoRefresh = it
        }
        row("Rescan videos", "Refresh the accessible local video library") {
            settings.prefs.edit().putBoolean("needs_rescan", true).apply()
            finish()
        }
        row("Clear video history", "Erase saved playback positions (not your video files)") {
            AlertDialog.Builder(this).setTitle("Clear watch history?")
                .setMessage("This removes Orbit's saved video resume positions. Your actual files are not changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear history") { _, _ ->
                    getSharedPreferences("playback_positions_v1", MODE_PRIVATE).edit().clear().apply()
                    settings.prefs.edit().putBoolean("needs_rescan", true).apply()
                }.show()
        }

        section("APP & UPDATES")
        row("Check for updates", "Look for the latest signed Orbit Pro release") {
            UpdateManager.check(this)
        }
        row("About Orbit Player Pro", "Version ${BuildConfig.VERSION_NAME} · Offline video player") {
            AlertDialog.Builder(this).setTitle("Orbit Player Pro")
                .setMessage("Version ${BuildConfig.VERSION_NAME}\nNative Android Media3 video player.\nNo ads, no analytics. Videos and preferences stay on your phone.\nUpdates are checked only when you request them.")
                .setPositiveButton("Close", null).show()
        }
        scroll.post { scroll.scrollTo(0, position) }
    }

    private fun section(text: String) {
        content.addView(label(text, 11f, true, accent).apply {
            letterSpacing = 0.1f
            setPadding(dp(6), dp(25), 0, dp(12))
        })
    }
    private fun makeRow(title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(11), dp(13), dp(11))
            background = surface()
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(title, 14.5f, true))
        info.addView(label(subtitle, 11f, color = muted).apply {
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(7); minimumHeight() })
        return row
    }
    private fun LinearLayout.LayoutParams.minimumHeight() { /* row min height comes from padding */ }

    private fun toggle(title: String, desc: String, initial: Boolean, change: (Boolean) -> Unit) {
        val row = makeRow(title, desc)
        val control = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> change(checked) }
        }
        row.addView(control)
        row.setOnClickListener { control.isChecked = !control.isChecked }
    }
    private fun row(title: String, desc: String, click: () -> Unit) {
        val r = makeRow(title, desc)
        r.addView(label("›", 27f, color = muted).apply { setPadding(dp(8),0,0,0) })
        r.setOnClickListener { click() }
    }
    private fun choose(title: String, desc: String, options: List<String>, save: (String) -> Unit) {
        row(title, desc) {
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), options.indexOf(desc).coerceAtLeast(0)) { dialog, selected ->
                    save(options[selected])
                    dialog.dismiss()
                    build()
                }.setNegativeButton("Cancel", null).show()
        }
    }
    private fun manageFolders(kind: String, title: String) {
        val items = settings.folders(kind).sorted()
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setTitle(title).setMessage(
                "No folders in this list. Long press any folder in the Albums screen to pin or hide it."
            ).setPositiveButton("OK", null).show()
            return
        }
        val captions = items.map { it.substringAfterLast('/') + "  ·  " + it }
        AlertDialog.Builder(this).setTitle(title)
            .setItems(captions.toTypedArray()) { _, index ->
                val path = items[index]
                settings.setFolder(kind, path, false)
                settings.prefs.edit().putBoolean("needs_rescan", true).apply()
                build()
            }.setNegativeButton("Close", null).show()
    }
}
