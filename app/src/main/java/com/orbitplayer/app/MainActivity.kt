package com.orbitplayer.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * A genuine offline video library. No fake navigation, static media cards or forced downloads.
 * Every chip/button changes the selected media, folder, view or user-approved permission.
 */
class MainActivity : Activity() {
    private enum class Filter { ALL, CONTINUE, RECENT, HISTORY, FAVORITES }
    private enum class Tab { VIDEOS, FOLDERS, CONTINUE, SEARCH }
    private enum class Sort { NEWEST, OLDEST, NAME, SIZE }

    private val requestLibrary = 501
    private val requestFile = 502
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val progress by lazy { getSharedPreferences("playback_positions_v1", MODE_PRIVATE) }
    private val preferences by lazy { OrbitSettings(this) }
    private val collections by lazy { OrbitCollections(this) }
    private var currentPlaylist: String? = null
    private var advanced = OrbitSearchFilter()
    private lateinit var albumAdapter: FolderAdapter
    private var tab = Tab.FOLDERS
    private var albums = emptyList<FolderAlbum>()
    private var albumSort = 0

    private lateinit var adapter: VideoAdapter
    private lateinit var gridManager: GridLayoutManager
    private lateinit var videos: RecyclerView
    private lateinit var status: TextView
    private lateinit var empty: TextView
    private lateinit var grant: TextView
    private lateinit var sortButton: TextView
    private lateinit var folderChip: TextView
    private lateinit var continueCard: View
    private lateinit var continueThumb: ImageView
    private lateinit var search: EditText
    private var allVideos: List<VideoItem> = emptyList()
    private var visibleVideos: List<VideoItem> = emptyList()
    private var folderOptions: List<String> = listOf("All folders")
    private var sort = Sort.NEWEST
    private var filter = Filter.ALL
    private var gallery = true
    private var query = ""
    private var chosenFolder = "All folders"
    private var continueVideo: VideoItem? = null
    private var scanGeneration = 0
    private var destroyed = false
    private var observerActive = false
    private val refreshLater = Runnable { if (!destroyed && hasLibraryPermission()) scan() }
    private val mediaObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (preferences.autoRefresh) {
                handler.removeCallbacks(refreshLater)
                handler.postDelayed(refreshLater, 1100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.count)
        empty = findViewById(R.id.empty)
        grant = findViewById(R.id.btnGrant)
        sortButton = findViewById(R.id.btnSort)
        folderChip = findViewById(R.id.folderFilter)
        continueCard = findViewById(R.id.continueCard)
        continueThumb = findViewById(R.id.continueThumb)
        search = findViewById(R.id.search)
        videos = findViewById(R.id.videos)
        adapter = VideoAdapter(this, { open(it) }, { showVideoOptions(it) })
        albumAdapter = FolderAdapter(this, { openAlbum(it) }, { folderOptions(it) })
        gallery = preferences.gallery
        sort = Sort.values()[preferences.sort]
        tab = when (preferences.defaultTab) {
            "Videos" -> Tab.VIDEOS
            "Continue" -> Tab.CONTINUE
            "Search" -> Tab.SEARCH
            else -> Tab.FOLDERS
        }
        if (tab == Tab.VIDEOS) chosenFolder = preferences.lastFolder
        gridManager = GridLayoutManager(this, if (gallery) 2 else 1)
        videos.layoutManager = gridManager
        videos.adapter = adapter
        videos.setHasFixedSize(true)
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnPick).setOnClickListener { showOpenSourceMenu() }
        findViewById<View>(R.id.btnPlaylists).setOnClickListener { showPlaylists() }
        findViewById<View>(R.id.btnAdvancedFilters).setOnClickListener { showAdvancedFilters() }
        videos.setItemViewCacheSize(8)

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            if (hasLibraryPermission()) scan() else requestVideoPermissions()
        }
        findViewById<View>(R.id.btnView).setOnClickListener { setGalleryMode(!gallery) }
        grant.setOnClickListener { requestVideoPermissions() }
        sortButton.setOnClickListener { showSortDialog() }
        folderChip.setOnClickListener { showFolderDialog() }
        findViewById<View>(R.id.filterAll).setOnClickListener { chooseFilter(Filter.ALL) }
        findViewById<View>(R.id.filterProgress).setOnClickListener { chooseFilter(Filter.CONTINUE) }
        findViewById<View>(R.id.filterRecent).setOnClickListener { chooseFilter(Filter.RECENT) }
        findViewById<View>(R.id.filterHistory).setOnClickListener { chooseFilter(Filter.HISTORY) }
        findViewById<View>(R.id.filterFavorites).setOnClickListener { currentPlaylist = null; chooseFilter(Filter.FAVORITES) }
        findViewById<View>(R.id.navHome).setOnClickListener { setTab(Tab.VIDEOS) }
        findViewById<View>(R.id.navFolders).setOnClickListener { setTab(Tab.FOLDERS) }
        findViewById<View>(R.id.navContinue).setOnClickListener { setTab(Tab.CONTINUE) }
        findViewById<View>(R.id.navSearch).setOnClickListener { setTab(Tab.SEARCH) }
        continueCard.setOnClickListener { continueVideo?.let { open(it) } }
        findViewById<View>(R.id.clearSearch).setOnClickListener { search.setText("") }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                findViewById<View>(R.id.clearSearch).visibility =
                    if (query.isBlank()) View.GONE else View.VISIBLE
                filterVideos()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updatePermissionsUi()
        applyAppearance()
        if (hasLibraryPermission()) scan() else filterVideos()
        showPreviousCrashReport()
    }

    override fun onStart() {
        super.onStart()
        if (!observerActive) {
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            observerActive = true
        }
    }

    override fun onStop() {
        handler.removeCallbacks(refreshLater)
        if (observerActive) {
            contentResolver.unregisterContentObserver(mediaObserver)
            observerActive = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsUi()
        if (::adapter.isInitialized) {
            applyAppearance()
            adapter.refreshProgress()
            refreshContinueCard()
            if (gallery != preferences.gallery && tab != Tab.FOLDERS) setGalleryMode(preferences.gallery)
            val rescan = preferences.prefs.getBoolean("needs_rescan", false)
            if (rescan) preferences.prefs.edit().putBoolean("needs_rescan", false).apply()
            if (hasLibraryPermission() && (allVideos.isEmpty() || rescan)) scan()
            else filterVideos()
        }
    }

    private fun hasLibraryPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        val full = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= 34 &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
        return full || partial
    }

    private fun updatePermissionsUi() {
        grant.text = if (hasLibraryPermission()) "ACCESS" else "ALLOW ACCESS"
    }

    private fun requestVideoPermissions() {
        val requested = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(requested, requestLibrary)
    }

    private fun showOpenSourceMenu() {
        val options = arrayOf("Choose local video", "Open secure video link (HTTPS)", "Browse a connected file provider", "Browse SMB2/SMB3 network share")
        AlertDialog.Builder(this).setTitle("Open a video")
            .setItems(options) { _, selected ->
                when (selected) {
                    0, 2 -> openDocument() // Android SAF supports installed SMB/drive document providers.
                    3 -> startActivity(Intent(this, SmbBrowserActivity::class.java))
                    1 -> {
                        val field = EditText(this).apply {
                            hint = "https://example.com/video.mp4"
                            setSingleLine(true)
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        }
                        AlertDialog.Builder(this).setTitle("Secure network video")
                            .setMessage("Supports direct HTTPS video files and HLS/DASH streams. An installed document provider can browse remote network folders.")
                            .setView(field)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Play") { _, _ ->
                                val url = NetworkVideoUrl.validated(field.text.toString())
                                if (url == null) {
                                    Toast.makeText(this, "Enter a valid HTTPS URL without login details", Toast.LENGTH_LONG).show()
                                } else {
                                    PlaybackQueue.replace(emptyList())
                                    PlaybackQueue.persist(this)
                                    val title = android.net.Uri.parse(url).lastPathSegment ?: "Network video"
                                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                                        putExtra(PlayerActivity.EXTRA_URI, url)
                                        putExtra(PlayerActivity.EXTRA_TITLE, title)
                                    })
                                }
                            }.show()
                    }
                }
            }.show()
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestFile)
    }

    private fun setGalleryMode(newValue: Boolean) {
        if (gallery == newValue) return
        gallery = newValue
        preferences.gallery = gallery
        adapter.useGallery(gallery)
        gridManager.spanCount = if (gallery) 2 else 1
        findViewById<ImageView>(R.id.btnView).setImageResource(
            if (gallery) R.drawable.orbit_nav_list else R.drawable.orbit_nav_grid
        )
        findViewById<ImageView>(R.id.btnView).contentDescription =
            if (gallery) "Switch to compact video list" else "Switch to two-column gallery"
    }

    private fun showSortDialog() {
        if (tab == Tab.FOLDERS) {
            val options = arrayOf("Name A to Z", "Most videos first", "Recently updated")
            AlertDialog.Builder(this).setTitle("Sort folders")
                .setSingleChoiceItems(options, albumSort) { dialog, selected ->
                    albumSort = selected
                    updateAlbums()
                    dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
            return
        }
        val values = arrayOf("Newest first", "Oldest first", "Name A to Z", "Largest first")
        AlertDialog.Builder(this).setTitle("Sort videos")
            .setSingleChoiceItems(values, sort.ordinal) { dialog, selected ->
                sort = Sort.values()[selected]
                preferences.sort = selected
                sortButton.text = arrayOf("Newest", "Oldest", "Name A–Z", "Largest")[selected] + "  ▾"
                filterVideos()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showFolderDialog() {
        AlertDialog.Builder(this).setTitle("Browse folders")
            .setSingleChoiceItems(folderOptions.toTypedArray(),
                folderOptions.indexOf(chosenFolder).coerceAtLeast(0)) { dialog, position ->
                chosenFolder = folderOptions[position]
                filter = Filter.ALL
                filterVideos()
                videos.scrollToPosition(0)
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseFilter(value: Filter) {
        currentPlaylist = null
        filter = value
        filterVideos()
        videos.scrollToPosition(0)
    }

    private fun scan() {
        if (!hasLibraryPermission()) {
            allVideos = emptyList()
            rebuildFolders()
            filterVideos()
            return
        }
        val token = ++scanGeneration
        status.text = "Scanning accessible videos…"
        executor.execute {
            val result = runCatching { VideoRepository(applicationContext).load() }
            handler.post {
                if (destroyed || token != scanGeneration) return@post
                if (result.isSuccess) {
                    allVideos = result.getOrDefault(emptyList())
                    rebuildFolders()
                    filterVideos()
                    refreshContinueCard()
                } else {
                    allVideos = emptyList()
                    rebuildFolders()
                    filterVideos()
                    status.text = "Library could not be read. Open a video instead."
                }
            }
        }
    }

    private fun rebuildFolders() {
        folderOptions = listOf("All folders") +
            allVideos.map { it.folder }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (chosenFolder !in folderOptions) chosenFolder = "All folders"
        preferences.prefs.edit().putStringSet("known_folder_paths",
            folderOptions.drop(1).toSet()).apply()
    }

    private fun savedPosition(item: VideoItem): Long =
        progress.getLong("position_${item.uri.toString().hashCode()}", 0L)

    private fun hasProgress(item: VideoItem): Boolean {
        val pos = savedPosition(item)
        return item.durationMs > 0L && pos >= 3_000L && pos < item.durationMs - 5_000L
    }

    private fun filterVideos() {
        val nowSeconds = System.currentTimeMillis() / 1000
        val history = progress.getString("recently_watched", "")?.split("|")
            ?.filter { it.isNotBlank() } ?: emptyList()
        val historyOrder = history.withIndex().associate { it.value to it.index }
        val cutoff = nowSeconds - 30L * 24L * 60L * 60L
        val playlistOrder = currentPlaylist?.let { collections.items(it) }
        val favorites = if (filter == Filter.FAVORITES) collections.favorites() else emptySet()
        val filtered = allVideos.asSequence()
            .filter { !preferences.hasFolder("hidden", it.folder) }
            .filter { chosenFolder == "All folders" || it.folder == chosenFolder }
            .filter { playlistOrder == null || it.uri.toString() in playlistOrder }
            .filter { advanced.matches(it) }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.folder.contains(query, ignoreCase = true) }
            .filter {
                when {
                    filter == Filter.FAVORITES -> it.uri.toString() in favorites
                    tab == Tab.CONTINUE ->
                        if (filter == Filter.HISTORY) it.uri.toString() in history
                        else hasProgress(it)
                    filter == Filter.HISTORY -> it.uri.toString() in history
                    filter == Filter.ALL -> true
                    filter == Filter.CONTINUE -> hasProgress(it)
                    else -> it.modifiedSeconds >= cutoff
                }
            }
        visibleVideos = when {
            playlistOrder != null -> filtered.sortedBy { playlistOrder.indexOf(it.uri.toString()) }
            filter == Filter.HISTORY -> filtered.sortedBy {
                historyOrder[it.uri.toString()] ?: Int.MAX_VALUE
            }
            sort == Sort.NEWEST -> filtered.sortedByDescending { it.modifiedSeconds }
            sort == Sort.OLDEST -> filtered.sortedBy { it.modifiedSeconds }
            sort == Sort.NAME -> filtered.sortedBy { it.title.lowercase() }
            else -> filtered.sortedByDescending { it.sizeBytes }
        }.toList()
        adapter.submit(visibleVideos)
        if (tab == Tab.FOLDERS) {
            updateAlbums()
            return
        }
        videos.adapter = adapter
        gridManager.spanCount = if (gallery) 2 else 1
        empty.visibility = if (visibleVideos.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when {
            !hasLibraryPermission() -> "Enable library access or tap Open to choose a video."
            allVideos.isEmpty() -> "No videos found.\nTap Open to choose a video."
            filter == Filter.CONTINUE -> "No unfinished videos.\nVideos you start watching will appear here."
            filter == Filter.RECENT -> "No videos added in the last 30 days."
            filter == Filter.FAVORITES -> "No favorite videos yet. Long press any video to add one."
            else -> "Nothing matches this folder or search."
        }
        status.text = "${visibleVideos.size} videos  ·  ${allVideos.size} total" +
            if (advanced.active) "  ·  FILTERED" else ""
        findViewById<TextView>(R.id.sectionTitle).text = when {
            currentPlaylist != null -> "Playlist: $currentPlaylist"
            filter == Filter.FAVORITES -> "Favorite Videos"
            tab == Tab.CONTINUE ->
                if (filter == Filter.HISTORY) "Watch History" else "Continue Watching"
            tab == Tab.SEARCH -> "Search Library"
            chosenFolder != "All folders" -> chosenFolder.trim('/').substringAfterLast('/')
            filter == Filter.RECENT -> "Recently Added"
            filter == Filter.CONTINUE -> "Keep Watching"
            else -> "All Videos"
        }
        renderChrome()
        folderChip.text = if (chosenFolder == "All folders") "Folders  ▾"
                          else chosenFolder.substringAfterLast('/').take(18) + "  ▾"
        for ((id, selected) in arrayOf(
            R.id.filterAll to (filter == Filter.ALL),
            R.id.filterProgress to (filter == Filter.CONTINUE),
            R.id.filterRecent to (filter == Filter.RECENT),
            R.id.filterHistory to (filter == Filter.HISTORY),
            R.id.filterFavorites to (filter == Filter.FAVORITES),
            R.id.folderFilter to (chosenFolder != "All folders")
        )) {
            val view = findViewById<TextView>(id)
            view.setBackgroundResource(if (selected) R.drawable.orbit_filter_active
                else R.drawable.orbit_filter_idle)
            view.setTextColor(if (selected) 0xFFF5F2FF.toInt() else 0xFFC2D0E8.toInt())
        }
    }

    private fun refreshContinueCard() {
        val preferred = progress.getString("last_played_uri", null)
        continueVideo = allVideos.firstOrNull { it.uri.toString() == preferred && hasProgress(it) }
            ?: allVideos.filter(::hasProgress).maxByOrNull { it.modifiedSeconds }
        val item = continueVideo
        continueCard.visibility =
            if (item == null || tab != Tab.VIDEOS || chosenFolder != "All folders") View.GONE
            else View.VISIBLE
        if (item == null) return
        findViewById<TextView>(R.id.continueTitle).text = item.title
        findViewById<TextView>(R.id.continueDetails).text =
            "${formatDuration(savedPosition(item))} watched  ·  ${formatDuration(item.durationMs)} total"
        val key = item.uri.toString()
        continueThumb.tag = key
        continueThumb.setImageDrawable(null)
        executor.execute {
            val bmp = try {
                if (Build.VERSION.SDK_INT >= 29)
                    contentResolver.loadThumbnail(item.uri, Size(210, 130), null)
                else null
            } catch (_: Exception) { null }
            if (bmp != null) handler.post {
                if (!destroyed && continueThumb.tag == key) continueThumb.setImageBitmap(bmp)
            }
        }
    }

    private fun open(item: VideoItem, playAll: Boolean = false) {
        progress.edit().putString("last_played_uri", item.uri.toString()).apply()
        recordWatchHistory(item.uri.toString())
        PlaybackQueue.replace(visibleVideos, playAll)
        PlaybackQueue.persist(this)
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, item.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, item.title)
        })
    }

    @Deprecated("System picker result API; retained for small Android dependency footprint")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestFile && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PlaybackQueue.replace(emptyList())
            PlaybackQueue.persist(this)
            val display = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            progress.edit().putString("last_played_uri", uri.toString()).apply()
            recordWatchHistory(uri.toString())
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URI, uri.toString())
                putExtra(PlayerActivity.EXTRA_TITLE, display ?: uri.lastPathSegment ?: "Selected video")
            })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestLibrary) {
            updatePermissionsUi()
            scan()
        }
    }

    private fun showVideoOptions(item: VideoItem) {
        val uri = item.uri.toString()
        val favorite = collections.isFavorite(uri)
        val options = arrayOf(
            if (favorite) "★ Remove from favorites" else "☆ Add to favorites",
            "Add to playlist", "Select multiple videos", "Play from here"
        )
        AlertDialog.Builder(this).setTitle(item.title)
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> {
                        collections.toggleFavorite(uri)
                        if (filter == Filter.FAVORITES) filterVideos()
                        Toast.makeText(this, if (favorite) "Removed from favorites" else "Added to favorites", Toast.LENGTH_SHORT).show()
                    }
                    1 -> choosePlaylist(listOf(uri))
                    2 -> showMultiSelect()
                    3 -> open(item, true)
                }
            }.show()
    }

    private fun choosePlaylist(uris: List<String>) {
        val names = collections.names()
        val choices = (listOf("+ Create a new playlist") + names).toTypedArray()
        AlertDialog.Builder(this).setTitle("Add ${uris.size} video(s) to playlist")
            .setItems(choices) { _, position ->
                if (position == 0) {
                    val field = EditText(this).apply {
                        hint = "Playlist name"
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        isSingleLine = true
                    }
                    AlertDialog.Builder(this).setTitle("New playlist").setView(field)
                        .setPositiveButton("Create") { _, _ ->
                            val title = field.text.toString().trim()
                            if (title.isBlank()) {
                                Toast.makeText(this, "Please enter a playlist name", Toast.LENGTH_SHORT).show()
                            } else {
                                val count = collections.add(title, uris)
                                Toast.makeText(this, "Added $count video(s) to $title", Toast.LENGTH_SHORT).show()
                            }
                        }.setNegativeButton("Cancel", null).show()
                } else {
                    val count = collections.add(names[position - 1], uris)
                    Toast.makeText(this, "Added $count video(s)", Toast.LENGTH_SHORT).show()
                    if (currentPlaylist == names[position - 1]) filterVideos()
                }
            }.show()
    }

    private fun showMultiSelect() {
        val choices = visibleVideos
        if (choices.isEmpty()) return
        val selected = BooleanArray(choices.size)
        AlertDialog.Builder(this)
            .setTitle("Select videos · ${choices.size} available")
            .setMultiChoiceItems(choices.map { it.title }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setPositiveButton("Add to playlist") { _, _ ->
                val picked = choices.filterIndexed { index, _ -> selected[index] }.map { it.uri.toString() }
                if (picked.isNotEmpty()) choosePlaylist(picked)
                else Toast.makeText(this, "No videos selected", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Play selection") { _, _ ->
                val picked = choices.filterIndexed { index, _ -> selected[index] }
                if (picked.isNotEmpty()) {
                    visibleVideos = picked
                    open(picked.first(), true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlaylists() {
        val names = collections.names()
        val choices = (listOf("+ New playlist") + names.map {
            "$it  ·  ${collections.items(it).size} videos"
        }).toTypedArray()
        AlertDialog.Builder(this).setTitle("My playlists")
            .setItems(choices) { _, which ->
                if (which == 0) choosePlaylist(emptyList())
                else {
                    val title = names[which - 1]
                    val actions = arrayOf("Open playlist", "Play all", "Add videos", "Delete playlist")
                    AlertDialog.Builder(this).setTitle(title).setItems(actions) { _, action ->
                        when (action) {
                            0 -> openPlaylist(title)
                            1 -> {
                                openPlaylist(title)
                                visibleVideos.firstOrNull()?.let { open(it, true) }
                            }
                            2 -> {
                                openPlaylist(title)
                                showMultiSelectFromLibrary(title)
                            }
                            3 -> AlertDialog.Builder(this).setTitle("Delete $title?")
                                .setMessage("Only the playlist is removed. Video files stay untouched.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ ->
                                    collections.delete(title)
                                    if (currentPlaylist == title) {
                                        currentPlaylist = null
                                        filterVideos()
                                    }
                                }.show()
                        }
                    }.show()
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun showMultiSelectFromLibrary(title: String) {
        val choices = allVideos.filterNot { preferences.hasFolder("hidden", it.folder) }
        val selected = BooleanArray(choices.size)
        AlertDialog.Builder(this).setTitle("Add videos to $title")
            .setMultiChoiceItems(choices.map { it.title }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setPositiveButton("Add") { _, _ ->
                val picked = choices.filterIndexed { index, _ -> selected[index] }.map { it.uri.toString() }
                collections.add(title, picked)
                openPlaylist(title)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openPlaylist(name: String) {
        currentPlaylist = name
        tab = Tab.VIDEOS
        chosenFolder = "All folders"
        filter = Filter.ALL
        search.setText("")
        filterVideos()
        videos.scrollToPosition(0)
    }

    private fun showAdvancedFilters() {
        val fields = arrayOf(
            "File format · ${if (advanced.extension.isBlank()) "Any" else advanced.extension.uppercase()}",
            "Minimum resolution · ${if (advanced.minimumHeight == 0) "Any" else "${advanced.minimumHeight}p"}",
            "Duration · ${listOf("Any", "Under 5 min", "5–30 min", "30+ min")[advanced.durationGroup]}",
            "File size · ${listOf("Any", "Under 100 MB", "Over 1 GB")[advanced.sizeGroup]}",
            "Clear all filters"
        )
        AlertDialog.Builder(this).setTitle("Advanced video search")
            .setItems(fields) { _, index ->
                when (index) {
                    0 -> pickSearchOption("Video format", listOf("Any","MP4","MKV","MOV","AVI","WEBM","3GP")) { selected ->
                        advanced = advanced.copy(extension = if (selected == "Any") "" else selected.lowercase())
                    }
                    1 -> pickSearchOption("Minimum video resolution", listOf("Any","480p","720p","1080p","2160p")) {
                        advanced = advanced.copy(minimumHeight = if (it == "Any") 0 else it.removeSuffix("p").toInt())
                    }
                    2 -> pickSearchOption("Duration", listOf("Any","Under 5 min","5–30 min","30+ min")) {
                        advanced = advanced.copy(durationGroup = listOf("Any","Under 5 min","5–30 min","30+ min").indexOf(it))
                    }
                    3 -> pickSearchOption("File size", listOf("Any","Under 100 MB","Over 1 GB")) {
                        advanced = advanced.copy(sizeGroup = listOf("Any","Under 100 MB","Over 1 GB").indexOf(it))
                    }
                    else -> { advanced = OrbitSearchFilter(); filterVideos() }
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun pickSearchOption(title: String, options: List<String>, select: (String) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(options.toTypedArray()) { _, which ->
            select(options[which])
            filterVideos()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showPreviousCrashReport() {
        val crash = getSharedPreferences("orbit_diagnostics", MODE_PRIVATE)
        val report = crash.getString("last_error", null) ?: return
        crash.edit().remove("last_error").apply()
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Orbit Player Pro stopped unexpectedly")
            .setMessage("A crash occurred. Copy the report if you need help fixing it.\n\n" +
                report.take(2400))
            .setPositiveButton("Copy full error") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Orbit Player Pro crash", report))
                Toast.makeText(this, "Error copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }


    private fun recordWatchHistory(uri: String) {
        val old = progress.getString("recently_watched", "")?.split("|")
            ?.filter { it.isNotBlank() } ?: emptyList()
        progress.edit().putString("recently_watched",
            (listOf(uri) + old).distinct().take(100).joinToString("|")).apply()
    }

    private fun applyAppearance() {
        val dark = if (preferences.amoled) Color.BLACK else Color.rgb(8, 12, 23)
        findViewById<View>(R.id.mainRoot).setBackgroundColor(dark)
        window.statusBarColor = dark
        window.navigationBarColor = dark
    }

    private fun setTab(next: Tab) {
        tab = next
        preferences.lastTab = next.name
        currentPlaylist = null
        filter = if (next == Tab.CONTINUE) Filter.CONTINUE else Filter.ALL
        chosenFolder = if (next == Tab.VIDEOS) preferences.lastFolder
            .takeIf { it in folderOptions } ?: "All folders"
            else "All folders"
        search.setText("")
        filterVideos()
        videos.scrollToPosition(0)
        if (next == Tab.SEARCH) {
            search.requestFocus()
            val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        } else {
            search.clearFocus()
            val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            input.hideSoftInputFromWindow(search.windowToken, 0)
        }
    }

    private fun renderChrome() {
        val folders = tab == Tab.FOLDERS
        findViewById<View>(R.id.filterScroller).visibility =
            if (folders) View.GONE else View.VISIBLE
        findViewById<View>(R.id.filterAll).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.filterFavorites).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.filterRecent).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.folderFilter).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.filterProgress).text =
            if (tab == Tab.CONTINUE) "Unfinished" else "Continue"
        findViewById<View>(R.id.filterHistory).visibility =
            if (tab == Tab.CONTINUE) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnView).visibility = if (folders) View.GONE else View.VISIBLE
        if (folders) findViewById<TextView>(R.id.sectionTitle).text = "Albums"
        val bar = mapOf(
            Tab.VIDEOS to R.id.navHome, Tab.FOLDERS to R.id.navFolders,
            Tab.CONTINUE to R.id.navContinue, Tab.SEARCH to R.id.navSearch
        )
        bar.forEach { (item, id) ->
            val group = findViewById<android.widget.LinearLayout>(id)
            val active = item == tab
            val color = if (active) Color.rgb(178, 145, 255) else Color.rgb(151, 163, 188)
            group.getChildAt(0)?.let { child ->
                if (child is ImageView) child.setColorFilter(color)
            }
            group.getChildAt(1)?.let { child ->
                if (child is TextView) child.setTextColor(color)
            }
        }
        continueCard.visibility = if (tab == Tab.VIDEOS && continueVideo != null
            && chosenFolder == "All folders") View.VISIBLE else View.GONE
    }

    private fun updateAlbums() {
        albums = allVideos.asSequence()
            .filter { !preferences.hasFolder("hidden", it.folder) }
            .groupBy { it.folder }
            .map { (path, items) ->
                val recent = items.maxByOrNull { it.modifiedSeconds } ?: items.first()
                FolderAlbum(path, items.size, recent, items,
                    preferences.hasFolder("pinned", path))
            }.let { a ->
                when (albumSort) {
                    1 -> a.sortedWith(compareByDescending<FolderAlbum> { it.pinned }
                        .thenByDescending { it.count }.thenBy { it.displayName.lowercase() })
                    2 -> a.sortedWith(compareByDescending<FolderAlbum> { it.pinned }
                        .thenByDescending { it.cover.modifiedSeconds })
                    else -> a.sortedWith(compareByDescending<FolderAlbum> { it.pinned }
                        .thenBy { it.displayName.lowercase() })
                }
            }
        videos.adapter = albumAdapter
        gridManager.spanCount = preferences.folderColumns
        albumAdapter.submit(albums)
        empty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (hasLibraryPermission())
            "No visible video folders.\\nUnhide folders in Settings if needed."
            else "Allow video access to see your folders."
        status.text = "${albums.size} folders  ·  ${albums.sumOf { it.count }} videos"
        sortButton.text = arrayOf("A–Z  ▾", "Most  ▾", "Recent  ▾")[albumSort]
        renderChrome()
    }

    private fun openAlbum(album: FolderAlbum) {
        tab = Tab.VIDEOS
        chosenFolder = album.path
        preferences.lastFolder = album.path
        filter = Filter.ALL
        search.setText("")
        filterVideos()
        videos.scrollToPosition(0)
    }

    private fun folderOptions(album: FolderAlbum) {
        val pinLabel = if (album.pinned) "Unpin favorite" else "Pin as favorite"
        AlertDialog.Builder(this).setTitle(album.displayName)
            .setItems(arrayOf("Open folder", "Play all videos", pinLabel, "Hide folder")) { _, which ->
                when (which) {
                    0 -> openAlbum(album)
                    1 -> {
                        val ordered = album.items.sortedByDescending { it.modifiedSeconds }
                        if (ordered.isNotEmpty()) {
                            visibleVideos = ordered
                            open(ordered.first(), true)
                        }
                    }
                    2 -> {
                        preferences.setFolder("pinned", album.path, !album.pinned)
                        updateAlbums()
                    }
                    3 -> AlertDialog.Builder(this).setTitle("Hide ${album.displayName}?")
                        .setMessage("Videos stay on your phone. You can unhide the folder in Settings.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Hide") { _, _ ->
                            preferences.setFolder("hidden", album.path, true)
                            updateAlbums()
                        }.show()
                }
            }.show()
    }

    @Deprecated("Android back key is used to return from an opened album to the albums grid")
    override fun onBackPressed() {
        if (currentPlaylist != null) { currentPlaylist = null; filterVideos() }
        else if (tab == Tab.VIDEOS && chosenFolder != "All folders") setTab(Tab.FOLDERS)
        else if (tab == Tab.SEARCH || tab == Tab.CONTINUE) setTab(Tab.FOLDERS)
        else super.onBackPressed()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(refreshLater)
        ++scanGeneration
        executor.shutdownNow()
        adapter.dispose()
        albumAdapter.dispose()
        super.onDestroy()
    }
}
