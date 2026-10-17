package com.orbitplayer.app

import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Media3-backed foreground media session for notification/headset and opt-in background audio. */
class OrbitPlaybackService : MediaSessionService() {
    companion object {
        const val LOCAL_BIND_ACTION = "com.orbitplayer.app.BIND_LOCAL_PLAYER"
    }

    inner class PlayerBinder : Binder() {
        val service: OrbitPlaybackService get() = this@OrbitPlaybackService
        val player: ExoPlayer get() = this@OrbitPlaybackService.exo
    }

    private val binder = PlayerBinder()
    private val timerHandler = Handler(Looper.getMainLooper())
    private var sleepTimer: Runnable? = null
    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(OrbitDataSourceFactory(this)))
            .build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }
        session = MediaSession.Builder(this, exo).build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == LOCAL_BIND_ACTION) binder else super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!OrbitSettings(this).backgroundPlayback || !isPlaybackOngoing) {
            pauseAllPlayersAndStopSelf()
        }
    }

    /** Service-owned timer survives Activity closure while opt-in background audio plays. */
    fun setSleepTimer(minutes: Int) {
        sleepTimer?.let { timerHandler.removeCallbacks(it) }
        sleepTimer = null
        if (minutes > 0) {
            val action = Runnable { exo.pause(); sleepTimer = null }
            sleepTimer = action
            timerHandler.postDelayed(action, minutes * 60_000L)
        }
    }

    override fun onDestroy() {
        sleepTimer?.let { timerHandler.removeCallbacks(it) }
        sleepTimer = null
        session?.release()
        session = null
        exo.release()
        super.onDestroy()
    }
}
