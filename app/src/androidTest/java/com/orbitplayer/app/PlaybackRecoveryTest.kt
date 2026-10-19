package com.orbitplayer.app

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackRecoveryTest {
    private fun engine(activity: PlayerActivity) = PlayerActivity::class.java.getDeclaredField("player")
        .apply { isAccessible = true }.get(activity) as ExoPlayer?

    private fun waitFor(scenario: ActivityScenario<PlayerActivity>, message: String,
        condition: (ExoPlayer) -> Boolean) {
        val until = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < until) {
            var done = false
            scenario.onActivity { activity -> done = engine(activity)?.let(condition) == true }
            if (done) return
            Thread.sleep(100)
        }
        fail(message)
    }

    @Test fun missingAndCorruptQueueEntriesSkipAndLastErrorRetriesAfterFileRecovery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val base = context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID), "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
            arrayOf("orbit_smoke.mp4"), null)!!.use {
            check(it.moveToFirst()) { "Seed orbit_smoke.mp4 before instrumentation" }
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
        }
        val directory = File(context.cacheDir, "queue-recovery-${System.nanoTime()}").apply { mkdirs() }
        val corrupt = File(directory, "corrupt.mp4").apply { writeText("not a media container") }
        val recovered = File(directory, "recovered.mp4").apply { writeText("not a media container") }
        val good = File(directory, "good.mp4")
        context.contentResolver.openInputStream(base)!!.use { input -> good.outputStream().use(input::copyTo) }
        val first = VideoItem(base, "Recovery first", 28_000, 1, "QA", 1)
        val items = listOf(first,
            first.copy(uri = Uri.fromFile(File(directory, "missing.mp4")), title = "Recovery missing"),
            first.copy(uri = Uri.fromFile(corrupt), title = "Recovery corrupt"),
            first.copy(uri = Uri.fromFile(good), title = "Recovery good"),
            first.copy(uri = Uri.fromFile(recovered), title = "Recovery last"))
        val previousQueue = PlaybackQueue.snapshot()
        val previousAdvance = PlaybackQueue.autoAdvance
        val settings = OrbitSettings(context)
        val previousRepeat = settings.repeat
        try {
            settings.repeat = false
            PlaybackQueue.replace(items, true)
            ActivityScenario.launch<PlayerActivity>(Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URI, base.toString())
                putExtra(PlayerActivity.EXTRA_TITLE, first.title)
            }).use { scenario ->
                waitFor(scenario, "First item never became ready") { it.playbackState == Player.STATE_READY }
                scenario.onActivity { engine(it)!!.apply { seekTo(duration - 200); play() } }
                waitFor(scenario, "Queue failed to skip missing/corrupt middle entries") {
                    it.currentMediaItem?.localConfiguration?.uri == Uri.fromFile(good) && it.playbackState == Player.STATE_READY
                }
                scenario.onActivity { engine(it)!!.pause() }
                assertEquals("Recovery must not delete queue entries or original files", items, PlaybackQueue.snapshot())
                assertTrue(corrupt.exists())
                scenario.onActivity { engine(it)!!.apply { seekTo(duration - 200); play() } }
                waitFor(scenario, "Last corrupt item did not report an error") {
                    it.currentMediaItem?.localConfiguration?.uri == Uri.fromFile(recovered) && it.playerError != null
                }
                context.contentResolver.openInputStream(base)!!.use { input -> recovered.outputStream().use(input::copyTo) }
                var clicked = false
                var observed = "No active accessibility window"
                val until = System.currentTimeMillis() + 15_000
                while (!clicked && System.currentTimeMillis() < until) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    val matches = root?.findAccessibilityNodeInfosByText("Retry")
                    observed = "package=${root?.packageName}, labels=${matches?.map { it.text?.toString() }}"
                    // Android themes can expose transformed, all-caps button text.
                    clicked = matches
                        ?.firstOrNull { it.text?.toString()?.equals("Retry", ignoreCase = true) == true }
                        ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    if (!clicked) Thread.sleep(100)
                }
                assertTrue("Error dialog did not expose a working Retry button: $observed", clicked)
                println("RECOVERY_RETRY_INPUT $observed")
                waitFor(scenario, "Retry did not recover the repaired file") {
                    it.currentMediaItem?.localConfiguration?.uri == Uri.fromFile(recovered) &&
                        it.playbackState == Player.STATE_READY && it.playerError == null && it.playWhenReady
                }
                scenario.onActivity { engine(it)!!.pause() }
            }
        } finally {
            settings.repeat = previousRepeat
            PlaybackQueue.replace(previousQueue, previousAdvance)
            PlaybackQueue.persist(context)
            PlaybackQueue.awaitPersistenceForTest()
            directory.deleteRecursively()
        }
    }
}
