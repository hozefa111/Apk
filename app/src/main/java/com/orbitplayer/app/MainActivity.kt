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
import android.os.Parcelable
import android.os.Looper
import android.database.ContentObserver
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
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
import java.util.concurrent.Future

/**
 * A genuine offline video library. No fake navigation, static media cards or forced downloads.
 * Every chip/button changes the selected media, folder, view or user-approved permission.
 */
class MainActivity : Activity() {
    private enum class Filter { ALL, CONTINUE, RECENT, HISTORY, FAVORITES }
    private enum class Tab { VIDEOS, FOLDERS, CONTINUE, SEARCH }
    private enum class Sort { NEWEST, OLDEST, NAME, SIZE, DURATION }

    private val requestLibrary = 501
    private val requestFile = 502
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val progress by lazy { getSharedPreferences("playback_positions_v1", MODE_PRIVATE) }
    private val preferences by lazy { OrbitSettings(this) }
    private val thumbnails by lazy { ThumbnailLoader(applicationContext) }
    private val collections by lazy { OrbitCollections(this) }
    private var currentPlaylist: String? = null
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
    private lateinit var formatChip: TextView
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
    private var selectedFormat = "" // Empty means all file types.
    private var continueVideo: VideoItem? = null
    private var scanGeneration = 0
    private var scanInFlight = false
    private val filterExecutor = Executors.newSingleThreadExecutor()
    private var filterTask: Future<*>? = null
    private var filterGeneration = 0
    private var albumScroll: Parcelable? = null
    private var returningToAlbums = false
    private var albumQuery = ""
    private var openingPlayer = false
    private fun compactLibrary() = resources.configuration.screenHeightDp < 430
    private fun compactChrome() = resources.configuration.screenHeightDp < 600
    // Android can revoke video access while the app is in the background.
    private var previousLibraryAccess: Int? = null
    private var destroyed = false
    private var observerActive = false
    private var needsBackgroundScan = false
    // Never sort thousands of videos repeatedly while the user is still typing.
    private val searchLater = Runnable { if (!destroyed) filterVideos() }
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
        applyContentInsets(findViewById(R.id.mainRoot))
        status = findViewById(R.id.count)
        empty = findViewById(R.id.empty)
        grant = findViewById(R.id.btnGrant)
        sortButton = findViewById(R.id.btnSort)
        folderChip = findViewById(R.id.folderFilter)
        formatChip = findViewById(R.id.formatFilter)
        continueCard = findViewById(R.id.continueCard)
        continueThumb = findViewById(R.id.continueThumb)
        search = findViewById(R.id.search)
        videos = findViewById(R.id.videos)
        adapter = VideoAdapter(this, { open(it) }, { showVideoOptions(it) }, thumbnails)
        albumAdapter = FolderAdapter(this, { openAlbum(it) }, thumbnails, { folderOptions(it) })
        gallery = preferences.gallery
        adapter.useGallery(gallery)
        sort = Sort.values()[preferences.sort]
        tab = when (preferences.defaultTab) {
            "Videos" -> Tab.VIDEOS
            "Continue" -> Tab.CONTINUE
            "Search" -> Tab.SEARCH
            else -> Tab.FOLDERS
        }
        if (tab == Tab.VIDEOS) chosenFolder = preferences.lastFolder
        // Android may recreate this screen on rotation or after process eviction.
        // Restore the user's live context rather than dumping them into default Albums.
        savedInstanceState?.let { state ->
            tab = Tab.values().firstOrNull { it.name == state.getString("orbit_tab") } ?: tab
            filter = Filter.values().firstOrNull { it.name == state.getString("orbit_filter") } ?: filter
            sort = Sort.values().getOrNull(state.getInt("orbit_sort", sort.ordinal)) ?: sort
            chosenFolder = state.getString("orbit_folder", chosenFolder) ?: chosenFolder
            albumSort = state.getInt("orbit_album_sort", 0).coerceIn(0, 2)
            gallery = state.getBoolean("orbit_gallery", gallery)
            currentPlaylist = state.getString("orbit_playlist")?.takeIf {
                it in collections.names()
            }
            query = state.getString("orbit_query", "") ?: ""
            selectedFormat = state.getString("orbit_format", "") ?: ""
        }
        // Let RecyclerView restore its scroll offset after asynchronous MediaStore results.
        adapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        albumAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        gridManager = GridLayoutManager(this, if (gallery) 2 else 1)
        videos.layoutManager = gridManager
        videos.adapter = adapter
        videos.setHasFixedSize(true)
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.libraryBack).setOnClickListener { onBackPressed() }
        findViewById<View>(R.id.btnPick).setOnClickListener { openDocument() }
        findViewById<View>(R.id.btnFilters).setOnClickListener { showCompactFilters() }
        findViewById<View>(R.id.btnPlaylists).setOnClickListener { showPlaylists() }
        videos.setItemViewCacheSize(8)

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            if (hasLibraryPermission()) scan() else requestVideoPermissions()
        }
        findViewById<View>(R.id.btnView).setOnClickListener { setGalleryMode(!gallery) }
        grant.setOnClickListener { requestVideoPermissions() }
        sortButton.setOnClickListener { showSortDialog() }
        folderChip.setOnClickListener { showFolderDialog() }
        formatChip.setOnClickListener { showFormatDialog() }
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
                handler.removeCallbacks(searchLater)
                handler.postDelayed(searchLater, 180)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        albumScroll = savedInstanceState?.getParcelable("orbit_album_scroll")
        albumQuery = savedInstanceState?.getString("orbit_album_query", "") ?: ""
        if (query.isNotBlank()) search.setText(query)
        updatePermissionsUi()
        applyAppearance()
        renderChrome()
        previousLibraryAccess = libraryPermissionState()
        if (previousLibraryAccess != 0) scan() else filterVideos()
        showPreviousCrashReport()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("orbit_album_query", if (tab == Tab.FOLDERS) query else albumQuery)
        outState.putParcelable("orbit_album_scroll", if (tab == Tab.FOLDERS)
            gridManager.onSaveInstanceState() else albumScroll)
        outState.putString("orbit_tab", tab.name)
        outState.putString("orbit_filter", filter.name)
        outState.putInt("orbit_sort", sort.ordinal)
        outState.putString("orbit_folder", chosenFolder)
        outState.putInt("orbit_album_sort", albumSort)
        outState.putBoolean("orbit_gallery", gallery)
        outState.putString("orbit_playlist", currentPlaylist)
        outState.putString("orbit_query", query)
        outState.putString("orbit_format", selectedFormat)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        observeMedia()
    }

    private fun observeMedia() {
        if (observerActive || !hasLibraryPermission()) return
        try {
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            observerActive = true
        } catch (_: SecurityException) { /* Access changed while registering; onResume reconciles. */ }
    }

    override fun onStop() {
        // MediaStore observer is unregistered while another app/player is foreground.
        // Mark the next foreground entry for a real rescan instead of showing stale files.
        needsBackgroundScan = true
        handler.removeCallbacks(refreshLater)
        if (observerActive) {
            contentResolver.unregisterContentObserver(mediaObserver)
            observerActive = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        openingPlayer = false
        val hasAccess = hasLibraryPermission()
        val permissionState = libraryPermissionState()
        val accessChanged = previousLibraryAccess != null && previousLibraryAccess != permissionState
        previousLibraryAccess = permissionState
        if (accessChanged) ++scanGeneration
        updatePermissionsUi()
        observeMedia()
        if (::adapter.isInitialized) {
            applyAppearance()
            adapter.refreshProgress()
            if (gallery != preferences.gallery && tab != Tab.FOLDERS) setGalleryMode(preferences.gallery)
            val rescan = preferences.prefs.getBoolean("needs_rescan", false)
            if (rescan) preferences.prefs.edit().putBoolean("needs_rescan", false).apply()
            if (!hasAccess) {
                // Forget the stale on-screen MediaStore rows, not any saved user favorites.
                ++scanGeneration
                allVideos = emptyList()
                continueVideo = null
                rebuildFolders()
                filterVideos()
            } else if (accessChanged || (allVideos.isEmpty() && !scanInFlight) || rescan ||
                ((preferences.autoRefresh || permissionState == 1) && needsBackgroundScan)) {
                needsBackgroundScan = false
                scan()
            } else {
                filterVideos()
            }
            refreshContinueCard()
        }
    }

    private fun hasLibraryPermission() = libraryPermissionState() != 0

    private fun libraryPermissionState(): Int {
        if (Build.VERSION.SDK_INT < 33) {
            return if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED) 2 else 0
        }
        val full = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= 34 &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
        return if (full) 2 else if (partial) 1 else 0
    }

    private fun updatePermissionsUi() {
        grant.visibility = if (libraryPermissionState() == 2) View.GONE else View.VISIBLE
        findViewById<View>(R.id.libraryStatusRow).layoutParams = findViewById<View>(R.id.libraryStatusRow).layoutParams.apply {
            height = ((if (libraryPermissionState() == 2) { if (compactChrome()) 20 else 28 } else 48) * resources.displayMetrics.density).toInt()
        }
        grant.text = when (libraryPermissionState()) { 2 -> "ACCESS"; 1 -> "ADD VIDEOS"; else -> "ALLOW ACCESS" }
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
        val values = arrayOf("Newest first", "Oldest first", "Name A to Z",
            "Largest file first", "Longest video first")
        AlertDialog.Builder(this).setTitle("Sort videos")
            .setSingleChoiceItems(values, sort.ordinal) { dialog, selected ->
                sort = Sort.values()[selected]
                preferences.sort = selected
                sortButton.text = arrayOf("Newest", "Oldest", "Name A–Z",
                    "Largest", "Longest")[selected] + "  ▾"
                filterVideos()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showCompactFilters() {
        if (tab == Tab.FOLDERS) {
            AlertDialog.Builder(this).setTitle("Folder options")
                .setItems(arrayOf("Sort folders", "Refresh library")) { _, which ->
                    if (which == 0) showSortDialog()
                    else if (hasLibraryPermission()) scan() else requestVideoPermissions()
                }.show()
            return
        }
        if (tab == Tab.CONTINUE) {
            AlertDialog.Builder(this).setTitle("Continue watching")
                .setItems(arrayOf("Unfinished videos", "Watch history", "Sort videos", "Refresh library")) { _, which ->
                    when (which) {
                        0 -> chooseFilter(Filter.CONTINUE)
                        1 -> chooseFilter(Filter.HISTORY)
                        2 -> showSortDialog()
                        3 -> if (hasLibraryPermission()) scan() else requestVideoPermissions()
                    }
                }.show()
            return
        }
        AlertDialog.Builder(this).setTitle("Library filters")
            .setItems(arrayOf("Sort videos", "File type", "Folder", "All videos", "Favorites", "Continue watching", "Recently added", "Watch history", "Refresh library")) { _, which ->
                when (which) {
                    0 -> showSortDialog()
                    1 -> showFormatDialog()
                    2 -> showFolderDialog()
                    3 -> chooseFilter(Filter.ALL)
                    4 -> chooseFilter(Filter.FAVORITES)
                    5 -> chooseFilter(Filter.CONTINUE)
                    6 -> chooseFilter(Filter.RECENT)
                    7 -> chooseFilter(Filter.HISTORY)
                    8 -> if (hasLibraryPermission()) scan() else requestVideoPermissions()
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun showFormatDialog() {
        val formats = allVideos.asSequence().map { videoExtension(it.title) }
            .filter { it.isNotBlank() }.distinct().sorted().toList()
        val entries = listOf("All formats") + formats.map { it.uppercase(java.util.Locale.ROOT) }
        val selection = (formats.indexOf(selectedFormat) + 1).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Video file type")
            .setSingleChoiceItems(entries.toTypedArray(), selection) { dialog, index ->
                selectedFormat = if (index == 0) "" else formats[index - 1]
                filterVideos()
                videos.scrollToPosition(0)
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
        if (scanInFlight) return
        scanInFlight = true
        findViewById<View>(R.id.libraryLoading).visibility = if (allVideos.isEmpty()) View.VISIBLE else View.GONE
        if (allVideos.isEmpty()) empty.visibility = View.GONE
        val token = ++scanGeneration
        status.text = "Scanning accessible videos…"
        executor.execute {
            val result = runCatching { VideoRepository(applicationContext).load() }
            handler.post {
                if (destroyed) return@post
                scanInFlight = false
                findViewById<View>(R.id.libraryLoading).visibility = View.GONE
                if (token != scanGeneration) {
                    if (hasLibraryPermission()) scan()
                    return@post
                }
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
        // Albums have their own search/grouping logic. Avoid diffing and sorting the
        // entire video list invisibly whenever the user browses default Folders.
        if (tab == Tab.FOLDERS) {
            updateAlbums()
            return
        }
        val source = allVideos
        val history = progress.getString("recently_watched", "")?.split("|")
            ?.filter { it.isNotBlank() }.orEmpty()
        val stored = progress.all
        val request = LibraryQuery(
            text = query, folder = chosenFolder, format = selectedFormat,
            filter = if (tab == Tab.CONTINUE && filter != Filter.HISTORY) "CONTINUE" else filter.name,
            sort = sort.name, hidden = preferences.folders("hidden"),
            favorites = collections.favorites(), history = history,
            playlist = currentPlaylist?.let(collections::items))
        val token = ++filterGeneration
        filterTask?.cancel(true)
        filterTask = filterExecutor.submit {
            val byKey = source.associateBy { it.uri.toString() }
            val snapshot = request.copy(positions = source.associate { it.uri.toString() to
                (stored["position_${it.uri.toString().hashCode()}"] as? Long ?: 0L) })
            val result = LibraryEngine.videos(source.map { it.entry() }, snapshot).mapNotNull { byKey[it.key] }
            handler.post {
                if (destroyed || token != filterGeneration) return@post
                visibleVideos = result
                renderVideos()
            }
        }
    }

    private fun VideoItem.entry() = LibraryEntry(uri.toString(), title, folder, durationMs, sizeBytes, modifiedSeconds)

    private fun renderVideos() {
        adapter.submit(visibleVideos)
        if (videos.adapter !== adapter) videos.adapter = adapter
        adapter.useGallery(gallery && !compactLibrary())
        gridManager.spanCount = if (gallery && !compactLibrary()) 2 else 1
        sortButton.text = arrayOf("Newest", "Oldest", "Name A–Z", "Largest", "Longest")[sort.ordinal] + "  ▾"
        empty.visibility = if (visibleVideos.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when {
            !hasLibraryPermission() -> "Enable library access or tap Open to choose a video."
            allVideos.isEmpty() -> "No videos found.\nTap Open to choose a video."
            filter == Filter.CONTINUE -> "No unfinished videos.\nVideos you start watching will appear here."
            filter == Filter.RECENT -> "No videos added in the last 30 days."
            filter == Filter.FAVORITES -> "No favorites yet. Use a video’s ⋮ menu to add one."
            else -> "Nothing matches this folder or search."
        }
        status.text = "${visibleVideos.size} videos  ·  ${allVideos.size} total"
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
        formatChip.text = if (selectedFormat.isEmpty()) "Format  ▾"
            else "${selectedFormat.uppercase(java.util.Locale.ROOT)}  ▾"
        for ((id, selected) in arrayOf(
            R.id.filterAll to (filter == Filter.ALL),
            R.id.filterProgress to (filter == Filter.CONTINUE),
            R.id.filterRecent to (filter == Filter.RECENT),
            R.id.filterHistory to (filter == Filter.HISTORY),
            R.id.filterFavorites to (filter == Filter.FAVORITES),
            R.id.folderFilter to (chosenFolder != "All folders"),
            R.id.formatFilter to selectedFormat.isNotEmpty()
        )) {
            val view = findViewById<TextView>(id)
            view.setBackgroundResource(if (selected) R.drawable.orbit_filter_active
                else R.drawable.orbit_filter_idle)
            view.setTextColor(if (selected) 0xFFF5F2FF.toInt() else 0xFFC2D0E8.toInt())
        }
    }

    private fun refreshContinueCard() {
        val preferred = progress.getString("last_played_uri", null)
        val hidden = preferences.folders("hidden")
        val accessible = allVideos.filterNot { it.folder in hidden }
        continueVideo = accessible.firstOrNull { it.uri.toString() == preferred && hasProgress(it) }
            ?: accessible.filter(::hasProgress).maxByOrNull { it.modifiedSeconds }
        val item = continueVideo
        continueCard.visibility =
            if (item == null || tab != Tab.VIDEOS || chosenFolder != "All folders") View.GONE
            else View.VISIBLE
        if (item == null) return
        findViewById<TextView>(R.id.continueTitle).text = item.title
        findViewById<TextView>(R.id.continueDetails).text =
            "${formatDuration(savedPosition(item))} watched  ·  ${formatDuration(item.durationMs)} total"
        thumbnails.load(continueThumb, item)
    }

    private fun open(item: VideoItem, playAll: Boolean = false) {
        if (openingPlayer) return
        openingPlayer = true
        progress.edit().putString("last_played_uri", item.uri.toString()).apply()
        recordWatchHistory(item.uri.toString())
        val queue = visibleVideos.takeIf { list -> list.any { it.uri == item.uri } } ?: listOf(item)
        PlaybackQueue.replace(queue, playAll)
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
            executor.execute {
                val display = runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()
                handler.post {
                    if (destroyed) return@post
                    progress.edit().putString("last_played_uri", uri.toString()).apply()
                    recordWatchHistory(uri.toString())
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_URI, uri.toString())
                        putExtra(PlayerActivity.EXTRA_TITLE, display ?: uri.lastPathSegment ?: "Selected video")
                    })
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestLibrary) {
            updatePermissionsUi()
            observeMedia()
            scan()
        }
    }

    private fun showVideoOptions(item: VideoItem) {
        val uri = item.uri.toString()
        val favorite = collections.isFavorite(uri)
        val options = arrayOf(
            if (favorite) "★ Remove from favorites" else "☆ Add to favorites",
            "Add to playlist", "Select multiple videos", "Play from here"
        ) + if (currentPlaylist != null) arrayOf("Remove from playlist") else emptyArray()
        AlertDialog.Builder(this).setTitle(item.title)
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> {
                        collections.toggleFavorite(uri)
                        filterVideos()
                        adapter.refreshProgress()
                        Toast.makeText(this, if (favorite) "Removed from favorites" else "Added to favorites", Toast.LENGTH_SHORT).show()
                    }
                    1 -> choosePlaylist(listOf(uri))
                    2 -> showMultiSelect()
                    3 -> open(item, true)
                    4 -> currentPlaylist?.let { name -> collections.remove(name, uri); filterVideos() }
                }
            }.show()
    }

    private fun choosePlaylist(uris: List<String>) {
        val names = collections.names()
        val choices = (listOf("+ Create a new playlist") + names).toTypedArray()
        AlertDialog.Builder(this).setTitle("Add ${uris.size} video(s) to playlist")
            .setItems(choices) { _, position ->
                if (position == 0) {
                    createPlaylist(uris)
                } else {
                    val count = collections.add(names[position - 1], uris)
                    Toast.makeText(this, "Added $count video(s)", Toast.LENGTH_SHORT).show()
                    if (currentPlaylist == names[position - 1]) filterVideos()
                }
            }.show()
    }

    private fun createPlaylist(uris: List<String>) {
        val field = EditText(this).apply {
            hint = "Playlist name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(50))
        }
        val dialog = AlertDialog.Builder(this).setTitle("New playlist").setView(field)
            .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = field.text.toString().trim()
                if (title.isBlank() || title in collections.names()) field.error = "Enter a unique playlist name"
                else {
                    val count = collections.add(title, uris)
                    Toast.makeText(this, "Created $title · $count video(s)", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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
                if (which == 0) createPlaylist(emptyList())
                else {
                    val title = names[which - 1]
                    val actions = arrayOf("Open playlist", "Play all", "Add videos", "Delete playlist", "Rename playlist")
                    AlertDialog.Builder(this).setTitle(title).setItems(actions) { _, action ->
                        when (action) {
                            0 -> openPlaylist(title)
                            1 -> {
                                playPlaylist(title)
                            }
                            2 -> {
                                openPlaylist(title)
                                showMultiSelectFromLibrary(title)
                            }
                            4 -> renamePlaylist(title)
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
        val hidden = preferences.folders("hidden")
        val choices = allVideos.filterNot { it.folder in hidden }
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

    private fun playPlaylist(name: String) {
        val hidden = preferences.folders("hidden")
        val byUri = allVideos.filterNot { it.folder in hidden }
            .associateBy { it.uri.toString() }
        val ordered = collections.items(name).mapNotNull(byUri::get)
        if (ordered.isEmpty()) {
            Toast.makeText(this, "No accessible videos in this playlist", Toast.LENGTH_SHORT).show()
        } else {
            visibleVideos = ordered
            open(ordered.first(), true)
        }
    }

    private fun renamePlaylist(name: String) {
        val field = EditText(this).apply {
            setText(name); isSingleLine = true; selectAll()
            filters = arrayOf(android.text.InputFilter.LengthFilter(50))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Rename playlist").setView(field)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (collections.rename(name, field.text.toString())) {
                    if (currentPlaylist == name) currentPlaylist = field.text.toString().trim().take(50)
                    filterVideos()
                    dialog.dismiss()
                } else field.error = "Enter a unique playlist name"
            }
        }
        dialog.show()
    }

    private fun openPlaylist(name: String) {
        currentPlaylist = name
        selectedFormat = ""
        tab = Tab.VIDEOS
        chosenFolder = "All folders"
        filter = Filter.ALL
        search.setText("")
        handler.removeCallbacks(searchLater)
        filterVideos()
        videos.scrollToPosition(0)
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
        if (tab == Tab.FOLDERS) {
            albumScroll = gridManager.onSaveInstanceState()
            albumQuery = query
        }
        returningToAlbums = next == Tab.FOLDERS && albumScroll != null
        tab = next
        selectedFormat = ""
        preferences.lastTab = next.name
        currentPlaylist = null
        filter = if (next == Tab.CONTINUE) Filter.CONTINUE else Filter.ALL
        chosenFolder = if (next == Tab.VIDEOS) preferences.lastFolder
            .takeIf { it in folderOptions } ?: "All folders"
            else "All folders"
        search.setText(if (next == Tab.FOLDERS) albumQuery else "")
        handler.removeCallbacks(searchLater)
        renderChrome()
        filterVideos()
        if (!returningToAlbums) videos.scrollToPosition(0)
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
        val compact = compactChrome()
        val focusedSearch = tab == Tab.SEARCH || query.isNotBlank()
        findViewById<View>(R.id.brandBar).apply {
            visibility = if (compact && focusedSearch) View.GONE else View.VISIBLE
            layoutParams = layoutParams.apply { height = ((if (compact) 48 else 67) * resources.displayMetrics.density).toInt() }
        }
        findViewById<View>(R.id.librarySearchBar).visibility = if (compact && !focusedSearch) View.GONE else View.VISIBLE
        findViewById<View>(R.id.librarySectionRow).layoutParams =
            (findViewById<View>(R.id.librarySectionRow).layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                height = ((if (compact) 48 else 51) * resources.displayMetrics.density).toInt()
                topMargin = ((if (compact) 0 else 7) * resources.displayMetrics.density).toInt()
            }
        findViewById<View>(R.id.libraryNavigation).layoutParams =
            (findViewById<View>(R.id.libraryNavigation).layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                height = ((if (compact) 48 else 63) * resources.displayMetrics.density).toInt()
                bottomMargin = ((if (compact) 0 else 10) * resources.displayMetrics.density).toInt()
            }
        findViewById<View>(R.id.filterScroller).visibility =
            if (folders || compact) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnFilters).visibility = if (compact) View.VISIBLE else View.GONE
        sortButton.visibility = if (compact) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnRefresh).visibility = if (compact) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnFilters).contentDescription = if (folders) "Folder options" else "Library filters and sort"
        findViewById<View>(R.id.filterAll).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.filterFavorites).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.filterRecent).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.folderFilter).visibility =
            if (tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        formatChip.visibility =
            if (folders || tab == Tab.CONTINUE) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.filterProgress).text =
            if (tab == Tab.CONTINUE) "Unfinished" else "Continue"
        findViewById<View>(R.id.filterHistory).visibility =
            if (tab == Tab.CONTINUE) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnView).visibility = if (folders || compactLibrary()) View.GONE else View.VISIBLE
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
        findViewById<View>(R.id.libraryBack).visibility = if (tab == Tab.VIDEOS &&
            (chosenFolder != "All folders" || currentPlaylist != null)) View.VISIBLE else View.GONE
        continueCard.visibility = if (!compact && tab == Tab.VIDEOS && currentPlaylist == null && continueVideo != null
            && chosenFolder == "All folders") View.VISIBLE else View.GONE
    }

    private fun updateAlbums() {
        val hiddenFolders = preferences.folders("hidden")
        val pinnedFolders = preferences.folders("pinned")
        val source = allVideos
        val text = query
        val sorting = albumSort
        val token = ++filterGeneration
        filterTask?.cancel(true)
        filterTask = filterExecutor.submit {
            val byKey = source.associateBy { it.uri.toString() }
            val result = LibraryEngine.albums(source.map { it.entry() }, text, hiddenFolders, pinnedFolders, sorting)
                .map { album ->
                    val items = album.items.mapNotNull { byKey[it.key] }
                    FolderAlbum(album.path, items.size, byKey.getValue(album.cover.key), items, album.pinned)
                }
            handler.post {
                if (destroyed || token != filterGeneration) return@post
                albums = result
                renderAlbums()
            }
        }
    }

    private fun renderAlbums() {
        if (videos.adapter !== albumAdapter) videos.adapter = albumAdapter
        albumAdapter.useCompact(compactLibrary())
        gridManager.spanCount = if (compactLibrary()) (resources.configuration.screenWidthDp / 260).coerceIn(1, 4)
            else preferences.folderColumns.coerceAtMost(
                (resources.configuration.screenWidthDp / (100 * resources.configuration.fontScale)).toInt().coerceAtLeast(2))
        albumAdapter.submit(albums) {
            if (returningToAlbums && tab == Tab.FOLDERS) {
                albumScroll?.let(gridManager::onRestoreInstanceState)
                returningToAlbums = false
            }
        }
        empty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (hasLibraryPermission())
            "No visible video folders.\nTry another search or manage hidden folders in Settings."
            else "Allow video access to see your folders."
        val count = albums.sumOf { it.count }
        status.text = "${albums.size} ${if (albums.size == 1) "folder" else "folders"}  ·  $count ${if (count == 1) "video" else "videos"}"
        sortButton.text = arrayOf("A–Z  ▾", "Most  ▾", "Recent  ▾")[albumSort]
        renderChrome()
    }

    private fun openAlbum(album: FolderAlbum) {
        albumQuery = query
        albumScroll = gridManager.onSaveInstanceState()
        tab = Tab.VIDEOS
        selectedFormat = ""
        chosenFolder = album.path
        preferences.lastFolder = album.path
        filter = Filter.ALL
        search.setText("")
        handler.removeCallbacks(searchLater)
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
        handler.removeCallbacks(searchLater)
        ++scanGeneration
        ++filterGeneration
        filterTask?.cancel(true)
        filterExecutor.shutdownNow()
        executor.shutdownNow()
        adapter.dispose()
        albumAdapter.dispose()
        thumbnails.dispose()
        super.onDestroy()
    }
}
