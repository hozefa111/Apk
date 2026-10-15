package com.orbitplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

/** No Internet needed. Plays accessible content URIs through the Android Media3/ExoPlayer engine. */
class PlayerActivity : Activity() {
    companion object {
        const val EXTRA_URI = "video_uri"
        const val EXTRA_TITLE = "video_title"
        private const val PICK_SUBTITLE = 821
    }

    private lateinit var playerView: PlayerView
    private lateinit var playerTitle: TextView
    private lateinit var notice: TextView
    private lateinit var speedButton: TextView
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("playback_positions_v1", MODE_PRIVATE) }
    private var player: ExoPlayer? = null
    private var videoUri = ""
    private var videoTitle = "Video"
    private var subtitleUri: Uri? = null
    private var lastSavedPosition = 0L
    private var repeatOne = false
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var brightness = 0.5f
    private var toastHide: Runnable? = null
    private val progressSaver = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        playerTitle = findViewById(R.id.playerTitle)
        notice = findViewById(R.id.gestureNotice)
        speedButton = findViewById(R.id.btnSpeed)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        videoUri = intent?.data?.toString()
            ?: intent?.getStringExtra(EXTRA_URI).orEmpty()
        videoTitle = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: intent?.data?.lastPathSegment?.substringAfterLast('/') ?: "Video"
        if (videoUri.isBlank()) {
            Toast.makeText(this, "No video was selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        playerTitle.text = videoTitle
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.btnTracks).setOnClickListener { showTrackChooser() }
        findViewById<View>(R.id.btnSubs).setOnClickListener { chooseSubtitle() }
        speedButton.setOnClickListener { showSpeedChooser() }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMoreOptions() }
        findViewById<View>(R.id.btnPrevious).setOnClickListener { switchQueue(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { switchQueue(1) }
        attachGestureZones()
        if (savedInstanceState != null) {
            lastSavedPosition = savedInstanceState.getLong("position", 0L)
            videoUri = savedInstanceState.getString("uri", videoUri)
            videoTitle = savedInstanceState.getString("title", videoTitle)
            playerTitle.text = videoTitle
        } else {
            lastSavedPosition = savedProgress(videoUri)
        }
    }

    override fun onStart() {
        super.onStart()
        if (videoUri.isNotBlank()) preparePlayer()
    }

    private fun preparePlayer() {
        if (player != null) return
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        playerView.resizeMode = resizeMode
        exo.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isFinishing || isDestroyed) return
                AlertDialog.Builder(this@PlayerActivity)
                    .setTitle("Unable to play this video")
                    .setMessage("Decoder or file problem: ${error.errorCodeName}.\n\n" +
                        "This Lite build uses Android-supported codecs. Some formats need a different decoder.")
                    .setPositiveButton("OK", null)
                    .show()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    prefs.edit().remove(positionKey(videoUri)).apply()
                    lastSavedPosition = 0L
                }
            }
        })
        try {
            exo.setMediaItem(buildMediaItem(), lastSavedPosition.coerceAtLeast(0L))
            exo.prepare()
            exo.playWhenReady = true
        } catch (ex: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Could not open video")
                .setMessage(ex.message ?: "Unknown file error")
                .setPositiveButton("Close") { _, _ -> finish() }
                .show()
        }
        handler.removeCallbacks(progressSaver)
        handler.postDelayed(progressSaver, 5000)
    }

    private fun buildMediaItem(): MediaItem {
        val item = MediaItem.Builder().setUri(Uri.parse(videoUri))
        subtitleUri?.let { uri ->
            item.setSubtitleConfigurations(
                listOf(MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(subtitleMime(uri))
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build())
            )
        }
        return item.build()
    }

    private fun positionKey(uri: String) = "position_${uri.hashCode()}"
    private fun savedProgress(uri: String) = prefs.getLong(positionKey(uri), 0L)

    private fun savePosition() {
        val exo = player ?: return
        if (exo.playbackState == Player.STATE_ENDED) return
        val duration = exo.duration
        val pos = exo.currentPosition.coerceAtLeast(0L)
        val save = if (duration > 0 && pos >= duration - 8000) 0L else pos
        prefs.edit().putLong(positionKey(videoUri), save).apply()
        lastSavedPosition = save
    }

    private fun switchQueue(direction: Int) {
        val adjacent = PlaybackQueue.adjacent(videoUri, direction)
        if (adjacent == null) {
            Toast.makeText(this, "No ${if (direction > 0) "next" else "previous"} video in this list", Toast.LENGTH_SHORT).show()
            return
        }
        savePosition()
        videoUri = adjacent.uri.toString()
        videoTitle = adjacent.title
        playerTitle.text = videoTitle
        subtitleUri = null
        lastSavedPosition = savedProgress(videoUri)
        try {
            player?.apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                setMediaItem(buildMediaItem(), lastSavedPosition)
                prepare()
                playWhenReady = true
            }
        } catch (ex: Exception) {
            Toast.makeText(this, ex.message ?: "Cannot open this video", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSpeedChooser() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val captions = arrayOf("0.5×", "0.75×", "1× (Normal)", "1.25×", "1.5×", "2×")
        AlertDialog.Builder(this).setTitle("Playback speed").setItems(captions) { _, selected ->
            player?.setPlaybackSpeed(speeds[selected])
            speedButton.text = captions[selected].substringBefore(' ')
        }.show()
    }

    private data class TrackOption(val group: Tracks.Group, val index: Int, val label: String)

    private fun showTrackChooser() {
        val exo = player ?: return
        val audio = mutableListOf<TrackOption>()
        val subs = mutableListOf<TrackOption>()
        for (group in exo.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_TEXT) continue
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
                    ?: "Track ${index + 1}"
                val suffix = if (group.isTrackSelected(index)) "  ✓" else ""
                val option = TrackOption(group, index, label + suffix)
                if (group.type == C.TRACK_TYPE_AUDIO) audio.add(option) else subs.add(option)
            }
        }
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        labels.add("Subtitles off")
        actions.add {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }
        audio.forEach { option ->
            labels.add("Audio: ${option.label}")
            actions.add { selectTrack(exo, option, C.TRACK_TYPE_AUDIO) }
        }
        subs.forEach { option ->
            labels.add("Subtitle: ${option.label}")
            actions.add { selectTrack(exo, option, C.TRACK_TYPE_TEXT) }
        }
        labels.add("Load external subtitles…")
        actions.add { chooseSubtitle() }
        AlertDialog.Builder(this).setTitle("Audio & subtitle tracks")
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun selectTrack(exo: ExoPlayer, option: TrackOption, type: Int) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, listOf(option.index)))
            .build()
    }

    private fun chooseSubtitle() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/plain", "text/vtt", "application/x-subrip", "application/octet-stream", "application/ssa", "text/x-ssa"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, PICK_SUBTITLE)
    }

    private fun subtitleMime(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.lowercase() ?: uri.toString().lowercase()
        return when {
            name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    @Deprecated("System file-picker result API; retained for small native Android project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_SUBTITLE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            subtitleUri = uri
            val exo = player ?: return
            val pos = exo.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exo.playWhenReady
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            exo.setMediaItem(buildMediaItem(), pos)
            exo.prepare()
            exo.playWhenReady = wasPlaying
            Toast.makeText(this, "Subtitle attached", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMoreOptions() {
        val captions = arrayOf("Fit video", "Crop/zoom video", "Stretch video",
            "Rotate screen", if (repeatOne) "Repeat: ON" else "Repeat: OFF", "Rewind 10 seconds", "Forward 10 seconds")
        AlertDialog.Builder(this).setTitle("Player options").setItems(captions) { _, which ->
            when (which) {
                0 -> { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; playerView.resizeMode = resizeMode }
                1 -> { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; playerView.resizeMode = resizeMode }
                2 -> { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; playerView.resizeMode = resizeMode }
                3 -> requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                4 -> {
                    repeatOne = !repeatOne
                    player?.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    notifyGesture(if (repeatOne) "Repeat on" else "Repeat off")
                }
                5 -> seekRelative(-10_000)
                6 -> seekRelative(10_000)
            }
        }.show()
    }

    private fun seekRelative(delta: Long) {
        val exo = player ?: return
        val max = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, max))
        notifyGesture(if (delta > 0) "+10 sec" else "-10 sec")
    }

    private fun attachGestureZones() {
        val frame = playerView.parent as? FrameLayout ?: return
        listOf(false, true).forEach { right ->
            val zone = PlayerGestureZone(this, right,
                onDoubleTap = { seekRelative(if (right) 10_000L else -10_000L) },
                onVerticalScroll = { delta ->
                    if (right) {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val next = (current + delta * max).roundToInt().coerceIn(0, max)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        notifyGesture("Volume ${(100f * next / max).roundToInt()}%")
                    } else {
                        brightness = (brightness + delta).coerceIn(0.05f, 1f)
                        val params = window.attributes
                        params.screenBrightness = brightness
                        window.attributes = params
                        notifyGesture("Brightness ${(brightness * 100).roundToInt()}%")
                    }
                },
                onTap = { playerView.showController() }
            )
            frame.addView(zone, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.23f).toInt().coerceAtLeast(50),
                (resources.displayMetrics.heightPixels * 0.48f).toInt().coerceAtLeast(100),
                Gravity.CENTER_VERTICAL or (if (right) Gravity.RIGHT else Gravity.LEFT)
            ))
        }
    }

    private fun notifyGesture(message: String) {
        notice.text = message
        notice.visibility = View.VISIBLE
        toastHide?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { notice.visibility = View.GONE }
        toastHide = runnable
        handler.postDelayed(runnable, 900)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePosition()
        outState.putLong("position", lastSavedPosition)
        outState.putString("uri", videoUri)
        outState.putString("title", videoTitle)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        handler.removeCallbacks(progressSaver)
        savePosition()
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
