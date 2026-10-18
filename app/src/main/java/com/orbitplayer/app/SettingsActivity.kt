package com.orbitplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
    private lateinit var root: LinearLayout
    private lateinit var search: EditText
    private lateinit var categoryScroller: HorizontalScrollView
    private lateinit var searchBar: LinearLayout
    private lateinit var empty: TextView
    private var selectedCategory: String? = null
    private var category = ""
    private data class SettingRow(val category: String, val text: String, val view: View)
    private val rows = mutableListOf<SettingRow>()
    private val headings = linkedMapOf<String, View>()
    private val chips = linkedMapOf<String, TextView>()
    private val white get() = getColor(R.color.text_main)
    private val muted get() = getColor(R.color.text_secondary)
    private val accent get() = getColor(R.color.accent_cyan)
    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()
    private fun surface(): GradientDrawable = GradientDrawable().apply {
        setColor(getColor(R.color.surface))
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), getColor(R.color.divider))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = OrbitSettings(this)
        selectedCategory = savedInstanceState?.getString("settings_category")
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isFocusableInTouchMode = true }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        toolbar.addView(ImageView(this).apply {
            setImageResource(R.drawable.orbit_ic_back)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = "Back to library"
            background = getDrawable(R.drawable.orbit_touch_feedback)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(label("Settings", 22f, true), LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(ImageView(this).apply {
            id = R.id.btnSettingsSearch
            setImageResource(R.drawable.orbit_nav_search)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.search_settings)
            background = getDrawable(R.drawable.orbit_touch_feedback)
            setOnClickListener {
                searchBar.visibility = View.VISIBLE
                search.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar)
        searchBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (savedInstanceState?.getBoolean("settings_search_open") == true) View.VISIBLE else View.GONE
            setPadding(dp(18), 0, dp(10), 0)
        }
        search = EditText(this).apply {
            id = R.id.settingsSearch
            isSaveEnabled = false // Query/category are restored together from our own Bundle.
            hint = getString(R.string.search_settings)
            textSize = 15f
            isSingleLine = true
            setTextColor(white)
            setText(savedInstanceState?.getString("settings_query", "") ?: "")
        }
        searchBar.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        searchBar.addView(label("×", 26f).apply {
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.close_settings_search)
            background = getDrawable(R.drawable.orbit_touch_feedback)
            setOnClickListener {
                search.setText("")
                search.clearFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(search.windowToken, 0)
                searchBar.visibility = View.GONE
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(searchBar)
        val categories = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        for (name in listOf("All", "General", "Playback", "Library", "Audio & subtitles", "Gestures", "Appearance", "Privacy", "About")) {
            val chip = label(name, 13f).apply {
                minimumHeight = dp(48)
                gravity = Gravity.CENTER
                setPadding(dp(15), 0, dp(15), 0)
                contentDescription = "$name settings"
                setOnClickListener {
                    search.setText("")
                    selectedCategory = name.takeUnless { it == "All" }
                    filterRows(true)
                    search.clearFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(search.windowToken, 0)
                }
            }
            chips[name] = chip
            categories.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        categoryScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(categories)
        }
        root.addView(categoryScroller)
        categoryScroller.post { categoryScroller.scrollTo(savedInstanceState?.getInt("settings_categories_scroll", 0) ?: 0, 0) }
        scroll = ScrollView(this).apply { id = R.id.settingsScroll; isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        applyContentInsets(root)
        applyAppearance()
        build()
        scroll.post { scroll.scrollTo(0, savedInstanceState?.getInt("settings_scroll", 0) ?: 0) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                selectedCategory = null
                filterRows(true)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("settings_category", selectedCategory)
        outState.putString("settings_query", search.text.toString())
        outState.putBoolean("settings_search_open", searchBar.visibility == View.VISIBLE)
        outState.putInt("settings_scroll", scroll.scrollY)
        outState.putInt("settings_categories_scroll", categoryScroller.scrollX)
        super.onSaveInstanceState(outState)
    }

    private fun applyAppearance() {
        val background = if (settings.amoled) Color.BLACK else getColor(R.color.background)
        root.setBackgroundColor(background)
        window.statusBarColor = background
        window.navigationBarColor = background
    }

    private fun filterRows(resetScroll: Boolean) {
        val query = search.text.toString().trim()
        val visible = mutableSetOf<String>()
        rows.forEach { row ->
            val matches = (selectedCategory == null || row.category == selectedCategory) &&
                (query.isBlank() || row.text.contains(query, true) || row.category.contains(query, true))
            row.view.visibility = if (matches) View.VISIBLE else View.GONE
            if (matches) visible.add(row.category)
        }
        headings.forEach { (name, view) -> view.visibility = if (name in visible) View.VISIBLE else View.GONE }
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        chips.forEach { (name, chip) ->
            val active = name == (selectedCategory ?: "All")
            chip.isSelected = active
            chip.setBackgroundResource(if (active) R.drawable.orbit_filter_active else R.drawable.orbit_filter_idle)
            chip.setTextColor(if (active) white else muted)
        }
        if (resetScroll) scroll.scrollTo(0, 0)
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
        rows.clear()
        headings.clear()
        section("General")
        choose("Default opening screen", settings.defaultTab,
            listOf("Folders", "Videos", "Continue", "Search")) { settings.defaultTab = it }
        section("Appearance")
        toggle("AMOLED black", "Pure black background for dark viewing", settings.amoled) {
            settings.amoled = it
            applyAppearance()
        }

        section("Playback")
        choose("Default video orientation", settings.defaultOrientation,
            listOf("Automatic", "Landscape", "Portrait")) { settings.defaultOrientation = it }
        choose("Default video fit", settings.defaultAspect,
            listOf("Fit", "Crop", "Stretch")) { settings.defaultAspect = it }
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

        section("Gestures")
        toggle("Swipe for volume", "Adjust volume from the right side of the video",
            settings.gestureVolume) { settings.gestureVolume = it }
        toggle("Swipe for brightness", "Adjust brightness from the left side of the video",
            settings.gestureBrightness) { settings.gestureBrightness = it }
        toggle("Double tap to seek", "Seek backward or forward on the video",
            settings.gestureDoubleTap) { settings.gestureDoubleTap = it }
        toggle("Hold for 2× speed", "Speed up temporarily while holding a finger",
            settings.gestureHoldSpeed) { settings.gestureHoldSpeed = it }

        section("Audio & subtitles")
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

        section("Library")
        choose("Folder gallery columns", "${settings.folderColumns} columns",
            listOf("2 columns", "3 columns", "4 columns")) {
            settings.folderColumns = it.substringBefore(' ').toInt()
        }
        toggle("Video thumbnail grid", "Turn off for a compact list view", settings.gallery) {
            settings.gallery = it
        }
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
        section("Privacy")
        row("Clear watch history", "Manage watch history and resume positions separately") {
            val options = arrayOf("Clear watch history only",
                "Clear resume positions only", "Clear both history and resume positions")
            AlertDialog.Builder(this).setTitle("Clear playback data")
                .setItems(options) { _, selection ->
                    AlertDialog.Builder(this)
                        .setTitle("Confirm clear?")
                        .setMessage("Your video files, favorites and playlists will not be deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear") { _, _ ->
                            val store = getSharedPreferences("playback_positions_v1", MODE_PRIVATE)
                            val editor = store.edit()
                            if (selection != 1) {
                                editor.remove("recently_watched")
                                editor.remove("last_played_uri")
                            }
                            if (selection != 0) {
                                store.all.keys.filter { it.startsWith("position_") }
                                    .forEach(editor::remove)
                            }
                            editor.apply()
                            settings.prefs.edit().putBoolean("needs_rescan", true).apply()
                            android.widget.Toast.makeText(this, "Playback data cleared",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }.show()
                }.setNegativeButton("Cancel", null).show()
        }

        section("About")
        row("Check for updates", "Look for the latest signed Orbit Pro release") {
            UpdateManager.check(this)
        }
        row("About Orbit Player Pro", "Version ${BuildConfig.VERSION_NAME} · Offline video player") {
            AlertDialog.Builder(this).setTitle("Orbit Player Pro")
                .setMessage("Version ${BuildConfig.VERSION_NAME}\nNative Android Media3 video player.\nNo ads, no analytics. Videos and preferences stay on your phone.\nUpdates are checked only when you request them.")
                .setPositiveButton("Close", null).show()
        }
        empty = label(getString(R.string.no_settings_match), 15f, color = muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }
        content.addView(empty)
        filterRows(false)
        scroll.post { scroll.scrollTo(0, position) }
    }

    private fun section(text: String) {
        category = text
        val heading = label(text, 12f, true, accent).apply {
            setPadding(dp(4), dp(20), 0, dp(10))
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }
        headings[text] = heading
        content.addView(heading)
    }
    private fun makeRow(title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(11), dp(13), dp(11))
            background = surface()
            minimumHeight = dp(64)
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(title, 14.5f, true))
        info.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        info.addView(label(subtitle, 12f, color = muted).apply {
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.contentDescription = "$title, $subtitle"
        row.foreground = getDrawable(R.drawable.orbit_touch_feedback)
        rows.add(SettingRow(category, "$title $subtitle", row))
        content.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        return row
    }

    private fun toggle(title: String, desc: String, initial: Boolean, change: (Boolean) -> Unit) {
        val row = makeRow(title, desc)
        val control = Switch(this).apply {
            contentDescription = "$title, $desc"
            minHeight = dp(48)
            minWidth = dp(48)
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> change(checked) }
        }
        row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.addView(control)
        row.setOnClickListener { control.isChecked = !control.isChecked }
    }
    private fun row(title: String, desc: String, click: () -> Unit) {
        val r = makeRow(title, desc)
        r.addView(label("›", 27f, color = muted).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(dp(8),0,0,0)
        })
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
                "No folders in this list. Use a folder’s ⋮ menu to pin or hide it."
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
