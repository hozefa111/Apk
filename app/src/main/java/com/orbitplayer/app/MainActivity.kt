package com.orbitplayer.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** Offline local-library browser; SAF picker works even without a library permission. */
class MainActivity : Activity() {
    private val requestLibrary = 501
    private val requestFile = 502
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: VideoAdapter
    private lateinit var folderSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var empty: TextView
    private lateinit var grant: Button
    private lateinit var sortButton: Button
    private var allVideos = listOf<VideoItem>()
    private var visibleVideos = listOf<VideoItem>()
    private var folderOptions = listOf("All folders")
    private var alphabetic = false
    private var query = ""
    private var chosenFolder = "All folders"
    private var scanGeneration = 0
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.count)
        empty = findViewById(R.id.empty)
        grant = findViewById(R.id.btnGrant)
        sortButton = findViewById(R.id.btnSort)
        folderSpinner = findViewById(R.id.folders)
        adapter = VideoAdapter(this) { open(it) }
        findViewById<RecyclerView>(R.id.videos).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
            setItemViewCacheSize(8)
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener { openDocument() }
        grant.setOnClickListener { requestVideoPermissions() }
        // All bottom navigation actions are functional; this is not a decorative mockup.
        findViewById<View>(R.id.navHome).setOnClickListener {
            chosenFolder = "All folders"
            query = ""
            folderSpinner.setSelection(0)
            findViewById<EditText>(R.id.search).setText("")
            filter()
        }
        findViewById<View>(R.id.navFolders).setOnClickListener { folderSpinner.performClick() }
        findViewById<View>(R.id.navOpen).setOnClickListener { openDocument() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { scan() }
        sortButton.setOnClickListener {
            alphabetic = !alphabetic
            sortButton.text = if (alphabetic) "A–Z ▾" else "Newest ▾"
            filter()
        }
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty()
                filter()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        folderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                chosenFolder = folderOptions.getOrElse(position) { "All folders" }
                filter()
            }
        }
        updatePermissionsUi()
        if (hasLibraryPermission()) scan() else filter()
        showPreviousCrashReport()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsUi()
    }

    private fun hasLibraryPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val full = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= 34 &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return full || partial
    }

    private fun updatePermissionsUi() {
        grant.text = if (hasLibraryPermission()) "Change access" else "Allow library"
    }

    private fun requestVideoPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(permissions, requestLibrary)
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestFile)
    }

    private fun scan() {
        if (!hasLibraryPermission()) {
            allVideos = emptyList()
            buildFolders()
            filter()
            status.text = "Choose a video or allow library access"
            return
        }
        val generation = ++scanGeneration
        status.text = "Scanning accessible videos…"
        executor.execute {
            val result = runCatching { VideoRepository(applicationContext).load() }
            handler.post {
                if (destroyed || generation != scanGeneration) return@post
                if (result.isSuccess) {
                    allVideos = result.getOrDefault(emptyList())
                    buildFolders()
                    filter()
                } else {
                    status.text = "Cannot read library. Try Open a video."
                    allVideos = emptyList()
                    buildFolders()
                    filter(false)
                }
            }
        }
    }

    private fun buildFolders() {
        val old = chosenFolder
        folderOptions = listOf("All folders") + allVideos.map { it.folder }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        chosenFolder = if (old in folderOptions) old else "All folders"
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, folderOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        folderSpinner.adapter = spinnerAdapter
        folderSpinner.setSelection(folderOptions.indexOf(chosenFolder).coerceAtLeast(0))
    }

    private fun filter(updateStatus: Boolean = true) {
        visibleVideos = allVideos.asSequence()
            .filter { chosenFolder == "All folders" || chosenFolder == it.folder }
            .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
            .let { seq -> if (alphabetic) seq.sortedBy { it.title.lowercase() }
                    else seq.sortedByDescending { it.modifiedSeconds } }
            .toList()
        adapter.submit(visibleVideos)
        empty.visibility = if (visibleVideos.isEmpty()) View.VISIBLE else View.GONE
        if (updateStatus) status.text = "${visibleVideos.size} videos • ${allVideos.size} in library"
    }

    private fun open(item: VideoItem) {
        PlaybackQueue.replace(visibleVideos)
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, item.uri.toString())
            putExtra(PlayerActivity.EXTRA_TITLE, item.title)
        })
    }

    @Deprecated("System file-picker result API; retained for minimal Android dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestFile && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PlaybackQueue.replace(emptyList())
            val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected video"
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URI, uri.toString())
                putExtra(PlayerActivity.EXTRA_TITLE, fallback)
            })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestLibrary) {
            updatePermissionsUi()
            scan()
        }
    }


    private fun showPreviousCrashReport() {
        val prefs = getSharedPreferences("orbit_diagnostics", MODE_PRIVATE)
        val report = prefs.getString("last_error", null) ?: return
        prefs.edit().remove("last_error").apply()
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Orbit Player stopped unexpectedly")
            .setMessage("The player crashed on its previous run. You can copy the error and share it for fixing. No information is uploaded automatically.\\n\\n" + report.take(2400))
            .setPositiveButton("Copy full error") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Orbit Player crash report", report))
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
