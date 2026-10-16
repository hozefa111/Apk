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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * A genuine offline video library. No fake navigation, static media cards or forced downloads.
 * Every chip/button changes the selected media, folder, view or user-approved permission.
 */
class MainActivity : Activity() {
    private enum class Filter { ALL, CONTINUE, RECENT }
    private enum class Sort { NEWEST, OLDEST, NAME, SIZE }

    private val requestLibrary = 501
    private val requestFile = 502
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val progress by lazy { getSharedPreferences("playback_positions_v1", MODE_PRIVATE) }

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
        adapter = VideoAdapter(this) { open(it) }
        gridManager = GridLayoutManager(this, 2)
        videos.layoutManager = gridManager
        videos.adapter = adapter
        videos.setHasFixedSize(true)
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
        findViewById<View>(R.id.navHome).setOnClickListener {
            chosenFolder = "All folders"
            filter = Filter.ALL
            search.setText("")
            videos.scrollToPosition(0)
            filterVideos()
        }
        findViewById<View>(R.id.navFolders).setOnClickListener { showFolderDialog() }
        findViewById<View>(R.id.navOpen).setOnClickListener { openDocument() }
        findViewById<View>(R.id.navUpdates).setOnClickListener { UpdateManager.check(this) }
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
        if (hasLibraryPermission()) scan() else filterVideos()
        showPreviousCrashReport()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsUi()
        if (::adapter.isInitialized) {
            adapter.refreshProgress()
            refreshContinueCard()
            if (hasLibraryPermission() && allVideos.isEmpty()) scan()
            else if (filter == Filter.CONTINUE) filterVideos()
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
        adapter.useGallery(gallery)
        gridManager.spanCount = if (gallery) 2 else 1
        findViewById<ImageView>(R.id.btnView).setImageResource(
            if (gallery) R.drawable.orbit_nav_list else R.drawable.orbit_nav_grid
        )
        findViewById<ImageView>(R.id.btnView).contentDescription =
            if (gallery) "Switch to compact video list" else "Switch to two-column gallery"
    }

    private fun showSortDialog() {
        val values = arrayOf("Newest first", "Oldest first", "Name A to Z", "Largest first")
        AlertDialog.Builder(this).setTitle("Sort videos")
            .setSingleChoiceItems(values, sort.ordinal) { dialog, selected ->
                sort = Sort.values()[selected]
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
    }

    private fun savedPosition(item: VideoItem): Long =
        progress.getLong("position_${item.uri.toString().hashCode()}", 0L)

    private fun hasProgress(item: VideoItem): Boolean {
        val pos = savedPosition(item)
        return item.durationMs > 0L && pos >= 3_000L && pos < item.durationMs - 5_000L
    }

    private fun filterVideos() {
        val nowSeconds = System.currentTimeMillis() / 1000
        val cutoff = nowSeconds - 30L * 24L * 60L * 60L
        val filtered = allVideos.asSequence()
            .filter { chosenFolder == "All folders" || it.folder == chosenFolder }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.folder.contains(query, ignoreCase = true) }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.CONTINUE -> hasProgress(it)
                    Filter.RECENT -> it.modifiedSeconds >= cutoff
                }
            }
        visibleVideos = when (sort) {
            Sort.NEWEST -> filtered.sortedByDescending { it.modifiedSeconds }
            Sort.OLDEST -> filtered.sortedBy { it.modifiedSeconds }
            Sort.NAME -> filtered.sortedBy { it.title.lowercase() }
            Sort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }.toList()
        adapter.submit(visibleVideos)
        empty.visibility = if (visibleVideos.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when {
            !hasLibraryPermission() -> "Enable library access or tap Open to choose a video."
            allVideos.isEmpty() -> "No videos found.\nTap Open to choose a video."
            filter == Filter.CONTINUE -> "No unfinished videos.\nVideos you start watching will appear here."
            filter == Filter.RECENT -> "No videos added in the last 30 days."
            else -> "Nothing matches this folder or search."
        }
        status.text = "${visibleVideos.size} videos  ·  ${allVideos.size} total"
        findViewById<TextView>(R.id.sectionTitle).text = when (filter) {
            Filter.ALL -> "Your Library"
            Filter.CONTINUE -> "Keep Watching"
            Filter.RECENT -> "Recently Added"
        }
        folderChip.text = if (chosenFolder == "All folders") "Folders  ▾"
                          else chosenFolder.substringAfterLast('/').take(18) + "  ▾"
        for ((id, selected) in arrayOf(
            R.id.filterAll to (filter == Filter.ALL),
            R.id.filterProgress to (filter == Filter.CONTINUE),
            R.id.filterRecent to (filter == Filter.RECENT),
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
        continueCard.visibility = if (item == null) View.GONE else View.VISIBLE
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

    private fun open(item: VideoItem) {
        progress.edit().putString("last_played_uri", item.uri.toString()).apply()
        PlaybackQueue.replace(visibleVideos)
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
            val display = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            progress.edit().putString("last_played_uri", uri.toString()).apply()
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

    override fun onDestroy() {
        destroyed = true
        ++scanGeneration
        executor.shutdownNow()
        adapter.dispose()
        super.onDestroy()
    }
}
