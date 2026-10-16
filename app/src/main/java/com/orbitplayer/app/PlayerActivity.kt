package com.orbitplayer.app

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.app.PictureInPictureParams
import android.util.Rational
import android.widget.SeekBar
import android.widget.ImageView
import android.widget.ProgressBar
import android.view.HapticFeedbackConstants
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
import android.widget.LinearLayout
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
    private lateinit var gestureHud: View
    private lateinit var gestureMeter: ProgressBar
    private var showRemainingTime = false
    private var holdSpeedRestore: Float? = null
    private var sleepTimerMinutes = 0
    private var sleepTimerRunnable: Runnable? = null
    private lateinit var speedButton: TextView
    private lateinit var audioManager: AudioManager
    private lateinit var topBar: View
    private lateinit var centerControls: View
    private lateinit var bottomControls: View
    private lateinit var unlockButton: View
    private lateinit var playPauseButton: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeLabel: TextView
    private lateinit var durationLabel: TextView
    private var controlsVisible = true
    private var touchLocked = false
    private var userSeeking = false
    private var gestureSeeking = false
    private var gestureSeekAnchor = 0L
    private val hideControlsTask = Runnable {
        if (!userSeeking && !touchLocked) hideControls()
    }
    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, if (controlsVisible) 400 else 1000)
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("playback_positions_v1", MODE_PRIVATE) }
    private val userOptions by lazy { OrbitSettings(this) }
    private fun skipMs() = userOptions.seekSeconds * 1000L
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
        playerView.useController = false
        topBar = findViewById(R.id.topBar)
        centerControls = findViewById(R.id.centerControls)
        bottomControls = findViewById(R.id.bottomControls)
        unlockButton = findViewById(R.id.btnUnlock)
        playPauseButton = findViewById(R.id.btnPlayPause)
        seekBar = findViewById(R.id.playerSeekBar)
        currentTimeLabel = findViewById(R.id.timeCurrent)
        durationLabel = findViewById(R.id.timeDuration)
        playerTitle = findViewById(R.id.playerTitle)
        notice = findViewById(R.id.gestureNotice)
        gestureHud = findViewById(R.id.gestureHud)
        gestureMeter = findViewById(R.id.gestureMeter)
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
        requestedOrientation = when (userOptions.defaultOrientation) {
            "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        repeatOne = userOptions.repeat
        PlaybackQueue.ensureRestored(this)
        runCatching {
            playerView.subtitleView?.setFractionalTextSize(0.0533f * userOptions.subtitleScale)
        }
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.btnTracks).setOnClickListener { showTrackChooser() }
        findViewById<View>(R.id.btnSubs).setOnClickListener { chooseSubtitle() }
        speedButton.setOnClickListener { showSpeedChooser() }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMoreOptions() }
        findViewById<View>(R.id.btnPrevious).setOnClickListener { switchQueue(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { switchQueue(1) }
        findViewById<View>(R.id.btnRewind).setOnClickListener { seekRelative(-skipMs()) }
        findViewById<View>(R.id.btnForward).setOnClickListener { seekRelative(skipMs()) }
        playPauseButton.setOnClickListener { togglePlayPause() }
        findViewById<View>(R.id.btnAspect).setOnClickListener { cycleAspectRatio() }
        findViewById<View>(R.id.btnLock).setOnClickListener { setTouchLocked(true) }
        unlockButton.setOnClickListener { setTouchLocked(false) }
        findViewById<View>(R.id.btnPip).setOnClickListener { startPictureInPicture() }
        durationLabel.setOnClickListener {
            showRemainingTime = !showRemainingTime
            updateProgress()
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
                handler.removeCallbacks(hideControlsTask)
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    if (duration > 0L) {
                        val target = duration * progress / 1000L
                        currentTimeLabel.text = formatTime(target)
                        showGestureHud("Seek · " + formatTime(target), progress / 10)
                    }
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val duration = player?.duration ?: 0L
                if (duration > 0L) player?.seekTo(duration * seekBar.progress / 1000L)
                userSeeking = false
                hideGestureHudSoon()
                updateProgress()
                scheduleAutoHide()
            }
        })
        attachGestureZones()
        adjustForScreenSize()
        showControls()
        if (savedInstanceState != null) {
            lastSavedPosition = savedInstanceState.getLong("position", 0L)
            videoUri = savedInstanceState.getString("uri", videoUri)
            videoTitle = savedInstanceState.getString("title", videoTitle)
            playerTitle.text = videoTitle
        } else {
            lastSavedPosition = if (userOptions.resume) savedProgress(videoUri) else 0L
        }
    }

    override fun onStart() {
        super.onStart()
        if (videoUri.isNotBlank()) {
            try {
                preparePlayer()
            } catch (error: Exception) {
                android.util.Log.e("OrbitPlayer", "Player startup failed", error)
                AlertDialog.Builder(this)
                    .setTitle("Player could not start")
                    .setMessage((error.message ?: error.javaClass.simpleName).take(1200) + "\\n\\nPlease copy any error report shown after reopening the app.")
                    .setPositiveButton("Close") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun preparePlayer() {
        if (player != null) return
        val exo = ExoPlayer.Builder(this).build()
        exo.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        exo.setHandleAudioBecomingNoisy(true)
        player = exo
        playerView.player = exo
        prefs.edit().putString("last_played_uri", videoUri).apply()
        recordWatchHistory(videoUri)
        exo.setPlaybackSpeed(userOptions.playbackSpeed)
        speedButton.text = "${userOptions.playbackSpeed}×"
        val params = exo.trackSelectionParameters.buildUpon()
        if (userOptions.preferredAudio.isNotBlank())
            params.setPreferredAudioLanguage(userOptions.preferredAudio)
        if (userOptions.preferredSubtitle.isNotBlank())
            params.setPreferredTextLanguage(userOptions.preferredSubtitle)
        exo.trackSelectionParameters = params.build()
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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton()
                if (isPlaying) scheduleAutoHide() else {
                    handler.removeCallbacks(hideControlsTask)
                    if (!touchLocked) showControls()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateProgress()
                updatePlayPauseButton()

                if (playbackState == Player.STATE_ENDED) {
                    prefs.edit().remove(positionKey(videoUri)).apply()
                    lastSavedPosition = 0L
                    val finishedUri = videoUri
                    val canAdvance = PlaybackQueue.adjacent(finishedUri, 1) != null
                    if (QueueNavigation.shouldAutoAdvance(repeatOne, PlaybackQueue.autoAdvance, canAdvance)) {
                        // Media3 listener callbacks may run during transitions; avoid re-entrant setMediaItem.
                        handler.post {
                            if (!isFinishing && !isDestroyed && videoUri == finishedUri) switchQueue(1)
                        }
                    }
                }
            }
        })
        try {
            exo.setMediaItem(buildMediaItem(), lastSavedPosition.coerceAtLeast(0L))
            exo.prepare()
            exo.playWhenReady = userOptions.autoplay
        } catch (ex: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Could not open video")
                .setMessage(ex.message ?: "Unknown file error")
                .setPositiveButton("Close") { _, _ -> finish() }
                .show()
        }
        handler.removeCallbacks(progressSaver)
        handler.postDelayed(progressSaver, 5000)
        handler.removeCallbacks(progressUpdater)
        handler.post(progressUpdater)
        scheduleAutoHide()
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

    private fun recordWatchHistory(uri: String) {
        val old = prefs.getString("recently_watched", "")?.split("|")
            ?.filter { it.isNotBlank() } ?: emptyList()
        prefs.edit().putString("recently_watched",
            (listOf(uri) + old).distinct().take(100).joinToString("|")).apply()
    }

    private fun switchQueue(direction: Int) {
        PlaybackQueue.ensureRestored(this)
        val adjacent = PlaybackQueue.adjacent(videoUri, direction)
        if (adjacent == null) {
            Toast.makeText(this, "No ${if (direction > 0) "next" else "previous"} video in this list", Toast.LENGTH_SHORT).show()
            return
        }
        savePosition()
        videoUri = adjacent.uri.toString()
        prefs.edit().putString("last_played_uri", videoUri).apply()
        recordWatchHistory(videoUri)
        videoTitle = adjacent.title
        playerTitle.text = videoTitle
        subtitleUri = null
        lastSavedPosition = if (userOptions.resume) savedProgress(videoUri) else 0L
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
            userOptions.playbackSpeed = speeds[selected]
            speedButton.text = captions[selected].substringBefore(' ')
            scheduleAutoHide()
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
        handler.removeCallbacks(hideControlsTask)
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.orbit_pro_sheet)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.52f)
        }
        fun action(id: Int, work: () -> Unit) {
            dialog.findViewById<View>(id).setOnClickListener {
                dialog.dismiss()
                work()
            }
        }
        action(R.id.actionTracks) { showTrackChooser() }
        action(R.id.actionInformation) { showVideoInfo() }
        action(R.id.actionSleep) { showSleepTimer() }
        action(R.id.actionRotate) { rotateVideo() }
        action(R.id.actionAspect) { cycleAspectRatio() }
        val repeat = dialog.findViewById<TextView>(R.id.actionRepeat)
        repeat.text = if (repeatOne) "Repeat video  ·  On" else "Repeat video  ·  Off"
        action(R.id.actionRepeat) {
            repeatOne = !repeatOne
            player?.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            userOptions.repeat = repeatOne
            notifyGesture(if (repeatOne) "Repeat on" else "Repeat off")
        }
        action(R.id.actionPip) { startPictureInPicture() }
        dialog.setOnDismissListener { scheduleAutoHide() }
        dialog.show()
        // Android resets the dialog window on show() on some API levels.
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun setAspectRatio(mode: Int) {
        resizeMode = mode
        playerView.resizeMode = mode
        val caption = when (mode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
            else -> "Fit"
        }
        findViewById<TextView>(R.id.btnAspect).text = caption.uppercase(java.util.Locale.ROOT)
        notifyGesture("Screen: $caption")
        scheduleAutoHide()
    }

    private fun showVideoInfo() {
        val exo = player ?: return
        val size = exo.videoSize
        val resolution = if (size.width > 0 && size.height > 0)
            "${size.width} × ${size.height}" else "Not reported"
        val video = exo.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
            ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) } }
        val audio = exo.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
            ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) } }
        val mime = video?.sampleMimeType?.substringAfter('/') ?: "Unknown"
        val audioType = audio?.sampleMimeType?.substringAfter('/') ?: "Unknown"
        val info = buildString {
            appendLine(videoTitle)
            appendLine()
            appendLine("Resolution: $resolution")
            appendLine("Video: $mime")
            appendLine("Audio: $audioType")
            appendLine("Duration: ${if (exo.duration > 0) formatTime(exo.duration) else "Unknown"}")
            appendLine("Playback speed: ${exo.playbackParameters.speed}×")
            appendLine("Decoder: Android Media3 ExoPlayer")
        }
        AlertDialog.Builder(this).setTitle("Video information")
            .setMessage(info).setPositiveButton("Done", null).show()
    }

    private fun showSleepTimer() {
        val options = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes")
        val values = intArrayOf(0, 15, 30, 45, 60, 90)
        AlertDialog.Builder(this).setTitle("Sleep timer")
            .setSingleChoiceItems(options, values.indexOf(sleepTimerMinutes).coerceAtLeast(0)) { dialog, which ->
                sleepTimerRunnable?.let { handler.removeCallbacks(it) }
                sleepTimerRunnable = null
                sleepTimerMinutes = values[which]
                if (sleepTimerMinutes > 0) {
                    val task = Runnable {
                        player?.pause()
                        sleepTimerMinutes = 0
                        sleepTimerRunnable = null
                        showControls()
                        notifyGesture("Sleep timer finished")
                    }
                    sleepTimerRunnable = task
                    handler.postDelayed(task, sleepTimerMinutes * 60_000L)
                }
                notifyGesture(if (sleepTimerMinutes > 0) "Timer: $sleepTimerMinutes min" else "Sleep timer off")
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun seekRelative(delta: Long) {
        val exo = player ?: return
        val max = if (exo.duration > 0) exo.duration else Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, max))
        playPauseButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        notifyGesture(if (delta > 0) "+${kotlin.math.abs(delta) / 1000} seconds"
            else "-${kotlin.math.abs(delta) / 1000} seconds")
        updateProgress()
    }

    private fun attachGestureZones() {
        val layer = findViewById<FrameLayout>(R.id.gestureLayer)
        val zone = PlayerGestureZone(
            this,
            onTap = { if (touchLocked) showUnlockButton() else toggleControls() },
            onDoubleTap = { fraction ->
                if (!touchLocked && userOptions.gestureDoubleTap) {
                    when {
                        fraction < 0.36f -> seekRelative(-skipMs())
                        fraction > 0.64f -> seekRelative(skipMs())
                        else -> togglePlayPause()
                    }
                }
            },
            onVerticalScroll = { right, delta ->
                if (!touchLocked) {
                    if (right && userOptions.gestureVolume) {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val next = (current + delta * max).roundToInt().coerceIn(0, max)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        val percent = (100f * next / max).roundToInt()
                        showGestureHud("Volume  $percent%", percent)
                    } else if (!right && userOptions.gestureBrightness) {
                        brightness = (brightness + delta).coerceIn(0.05f, 1f)
                        val params = window.attributes
                        params.screenBrightness = brightness
                        window.attributes = params
                        val percent = (brightness * 100).roundToInt()
                        showGestureHud("Brightness  $percent%", percent)
                    }
                }
            },
            onHorizontalSeek = { amount, completed ->
                if (!touchLocked) {
                    val exo = player
                    val duration = exo?.duration ?: 0L
                    if (exo != null && duration > 0L) {
                        if (!gestureSeeking) {
                            gestureSeeking = true
                            gestureSeekAnchor = exo.currentPosition.coerceAtLeast(0L)
                        }
                        // A full-screen horizontal swipe spans at most half the video.
                        val target = (gestureSeekAnchor + (amount * duration * 0.50f).toLong())
                            .coerceIn(0L, duration)
                        if (completed) {
                            exo.seekTo(target)
                            gestureSeeking = false
                            scheduleAutoHide()
                        }
                        showGestureHud("Seek  ${formatTime(target)} / ${formatTime(duration)}",
                            (100.0 * target.toDouble() / duration.toDouble()).toInt().coerceIn(0, 100))
                        if (completed) hideGestureHudSoon()
                    }
                }
            },
            onPinch = { zoomIn ->
                if (!touchLocked) {
                    resizeMode = if (zoomIn) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                 else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    playerView.resizeMode = resizeMode
                    notifyGesture(if (zoomIn) "Fill screen" else "Fit to screen")
                }
            },
            onHoldBoost = { pressed ->
                if (!touchLocked && userOptions.gestureHoldSpeed) {
                    val exo = player
                    if (pressed && exo?.isPlaying == true && holdSpeedRestore == null) {
                        holdSpeedRestore = exo.playbackParameters.speed
                        exo.setPlaybackSpeed(2f)
                        showGestureHud("⏩  2× speed · Hold", -1)
                        playPauseButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } else if (!pressed) {
                        holdSpeedRestore?.let { original -> exo?.setPlaybackSpeed(original) }
                        holdSpeedRestore = null
                        hideGestureHudSoon()
                    }
                }
            },
            onGestureCancelled = { gestureSeeking = false }
        )
        layer.addView(zone, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun notifyGesture(message: String) {
        showGestureHud(message, -1)
        hideGestureHudSoon()
    }

    private fun showGestureHud(message: String, percentage: Int) {
        if (notice.text.toString() != message) notice.text = message
        gestureMeter.visibility = if (percentage in 0..100) View.VISIBLE else View.GONE
        if (percentage in 0..100) gestureMeter.progress = percentage
        toastHide?.let { handler.removeCallbacks(it) }
        gestureHud.visibility = View.VISIBLE
        if (gestureHud.alpha < 1f) gestureHud.animate().alpha(1f).setDuration(120).start()
    }

    private fun hideGestureHudSoon() {
        toastHide?.let { handler.removeCallbacks(it) }
        val hide = Runnable {
            gestureHud.animate().alpha(0f).setDuration(220)
                .withEndAction { gestureHud.visibility = View.GONE }.start()
        }
        toastHide = hide
        handler.postDelayed(hide, 950)
    }


    private fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        updatePlayPauseButton()
        showControls()
    }

    private fun updatePlayPauseButton() {
        if (!::playPauseButton.isInitialized) return
        val isPlaying = player?.isPlaying == true
        playPauseButton.setImageResource(if (isPlaying) R.drawable.orbit_ic_pause else R.drawable.orbit_ic_play)
        playPauseButton.contentDescription = if (isPlaying) "Pause video" else "Play video"
    }

    private fun updateProgress() {
        val exo = player ?: return
        if (!::seekBar.isInitialized) return
        val duration = exo.duration
        val position = exo.currentPosition.coerceAtLeast(0L)
        if (duration > 0L) {
            val totalText = if (showRemainingTime) "−" + formatTime((duration - position).coerceAtLeast(0L))
                else formatTime(duration)
            if (durationLabel.text.toString() != totalText) durationLabel.text = totalText
            val buffered = (exo.bufferedPosition.coerceIn(0L, duration) * 1000 / duration).toInt().coerceIn(0, 1000)
            seekBar.secondaryProgress = buffered
            if (!userSeeking) {
                val newTime = formatTime(position)
                if (currentTimeLabel.text.toString() != newTime) currentTimeLabel.text = newTime
                seekBar.progress = (position * 1000 / duration).toInt().coerceIn(0, 1000)
            }
        } else {
            durationLabel.text = "--:--"
            if (!userSeeking) currentTimeLabel.text = "00:00"
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0L) / 1000)
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0)
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds % 60)
    }

    private fun animateControls(view: View, visible: Boolean) {
        view.animate().cancel()
        if (visible) {
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(160).start()
        } else if (view.visibility == View.VISIBLE) {
            view.animate().alpha(0f).setDuration(160).withEndAction {
                if (!controlsVisible || touchLocked) view.visibility = View.GONE
            }.start()
        }
    }

    private fun showControls() {
        if (touchLocked) { showUnlockButton(); return }
        controlsVisible = true
        unlockButton.visibility = View.GONE
        listOf(topBar, bottomControls).forEach { animateControls(it, true) }
        scheduleAutoHide()
    }

    private fun hideControls() {
        controlsVisible = false
        handler.removeCallbacks(hideControlsTask)
        listOf(topBar, bottomControls).forEach { animateControls(it, false) }
        unlockButton.visibility = View.GONE
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(hideControlsTask)
        if (controlsVisible && !touchLocked && !userSeeking)
            handler.postDelayed(hideControlsTask,
                (if (player?.isPlaying == true) userOptions.controlsTimeout
                    else userOptions.controlsTimeout + 3) * 1000L)
    }

    private fun setTouchLocked(locked: Boolean) {
        touchLocked = locked
        hideControls()
        if (locked) {
            showUnlockButton()
            notifyGesture("Controls locked")
        } else {
            showControls()
            notifyGesture("Controls unlocked")
        }
    }

    private fun showUnlockButton() {
        unlockButton.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsTask)
        handler.postDelayed({ if (touchLocked) unlockButton.visibility = View.GONE }, 3500)
    }

    private fun cycleAspectRatio() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.resizeMode = resizeMode
        val caption = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
            else -> "Fit"
        }
        findViewById<TextView>(R.id.btnAspect).text = "▣  $caption"
        notifyGesture("Screen: $caption")
        scheduleAutoHide()
    }

    private fun rotateVideo() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        showControls()
    }

    /** Compact landscape keeps video unobstructed; advanced actions remain in the sheet. */
    private fun adjustForScreenSize() {
        val compact = resources.configuration.screenHeightDp < 510
        findViewById<View>(R.id.quickActions).visibility = if (compact) View.GONE else View.VISIBLE
        topBar.layoutParams = topBar.layoutParams.apply {
            height = ((if (compact) 54 else 76) * resources.displayMetrics.density).toInt()
        }
        // Keep the transport bar slimmer in landscape without hiding playback actions.
        val transport = findViewById<View>(R.id.centerControls)
        transport.layoutParams = transport.layoutParams.apply {
            height = ((if (compact) 62 else 72) * resources.displayMetrics.density).toInt()
        }
        val timeline = findViewById<View>(R.id.timelineRow)
        timeline.layoutParams = timeline.layoutParams.apply {
            height = ((if (compact) 36 else 42) * resources.displayMetrics.density).toInt()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustForScreenSize()
        if (!touchLocked) showControls()
    }

    private fun enableImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    private fun startPictureInPicture() {
        if (Build.VERSION.SDK_INT < 26) return
        val videoSize = player?.videoSize
        val width = videoSize?.width?.takeIf { it > 0 } ?: 16
        val height = videoSize?.height?.takeIf { it > 0 } ?: 9
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(width, height).let { ratio ->
                        if (ratio.toFloat() in (1f / 2.39f)..2.39f) ratio else Rational(16, 9)
                    })
                    .build()
            )
        }.onFailure { notifyGesture("PiP not supported on this device") }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        findViewById<View>(R.id.gestureLayer).visibility =
            if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        if (isInPictureInPictureMode) hideControls()
        else { enableImmersiveMode(); showControls() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePosition()
        outState.putLong("position", lastSavedPosition)
        outState.putString("uri", videoUri)
        outState.putString("title", videoTitle)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) {
            super.onStop()
            return
        }
        handler.removeCallbacks(progressSaver)
        handler.removeCallbacks(progressUpdater)
        handler.removeCallbacks(hideControlsTask)
        savePosition()
        holdSpeedRestore?.let { player?.setPlaybackSpeed(it) }
        holdSpeedRestore = null
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    override fun onDestroy() {
        // PiP may bypass normal onStop cleanup on some devices.
        playerView.player = null
        player?.release()
        player = null
        sleepTimerRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
