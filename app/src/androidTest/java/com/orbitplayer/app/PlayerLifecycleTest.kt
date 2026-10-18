package com.orbitplayer.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerLifecycleTest {
    private fun launch(): ActivityScenario<PlayerActivity> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val uri=context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),"${MediaStore.Video.Media.DISPLAY_NAME} = ?",arrayOf("orbit_smoke.mp4"),null)?.use {
            if(it.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,it.getLong(0)) else null
        }
        checkNotNull(uri) { "Seed orbit_smoke.mp4 using run_emulator_smoke.sh before instrumentation" }
        return ActivityScenario.launch(Intent(context,PlayerActivity::class.java).apply {
            action=Intent.ACTION_VIEW
            setDataAndType(uri,"video/mp4")
        })
    }
    private fun engine(activity: PlayerActivity): ExoPlayer? = PlayerActivity::class.java
        .getDeclaredField("player").apply { isAccessible=true }.get(activity) as? ExoPlayer
    private fun waitReady(scenario: ActivityScenario<PlayerActivity>) {
        val until=System.currentTimeMillis()+30_000
        while(System.currentTimeMillis()<until) {
            var ready=false
            scenario.onActivity { ready=engine(it)?.playbackState==Player.STATE_READY }
            if(ready) return
            Thread.sleep(100)
        }
        fail("ExoPlayer never reached READY")
    }
    @Test fun manualPausePositionAndTrackChoiceSurviveStopStart() {
        launch().use { scenario ->
            waitReady(scenario)
            scenario.onActivity { activity ->
                engine(activity)!!.apply {
                    pause(); seekTo(12_000)
                    trackSelectionParameters=trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertNull(engine(it)) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitReady(scenario)
            scenario.onActivity { activity ->
                val exo=engine(activity)!!
                assertFalse(exo.playWhenReady)
                assertTrue("Stopped session lost position: ${exo.currentPosition}",exo.currentPosition in 11_500..12_500)
                assertTrue(exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            }
        }
    }
    @Test fun externalSubtitleFormatsProduceCuesAfterStoppedPickerReturn() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        for (name in listOf("captions.srt","captions.vtt","captions.ass","captions.ssa")) {
            val file=java.io.File(context.cacheDir,name)
            instrumentation.context.assets.open(name).use { input -> file.outputStream().use(input::copyTo) }
            launch().use { scenario ->
                waitReady(scenario)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { activity ->
                    val method=PlayerActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }
                    method.invoke(activity,821,android.app.Activity.RESULT_OK,Intent().setData(android.net.Uri.fromFile(file)))
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitReady(scenario)
                // MIME lookup is asynchronous: the old video can report READY before
                // the newly selected subtitle has been attached and selected.
                val attachedUntil=System.currentTimeMillis()+15_000
                var attached=false
                while(!attached && System.currentTimeMillis()<attachedUntil) {
                    scenario.onActivity { activity ->
                        val exo=engine(activity)!!
                        attached=exo.playbackState==Player.STATE_READY && exo.currentTracks.groups.any { group ->
                            group.type==C.TRACK_TYPE_TEXT && (0 until group.length).any { index ->
                                group.isTrackSelected(index) && group.getTrackFormat(index).label=="External subtitles"
                            }
                        }
                    }
                    if(!attached) Thread.sleep(100)
                }
                assertTrue("External subtitle was not selected for $name",attached)
                scenario.onActivity { engine(it)!!.apply { pause();seekTo(5_000) } }
                val until=System.currentTimeMillis()+15_000
                var found=false
                while(!found && System.currentTimeMillis()<until) {
                    scenario.onActivity { activity -> found=engine(activity)!!.currentCues.cues.any { it.text?.contains("Orbit")==true } }
                    if(!found) Thread.sleep(100)
                }
                assertTrue("No rendered subtitle cue for $name",found)
                scenario.recreate()
                waitReady(scenario)
                scenario.onActivity { activity ->
                    val field=PlayerActivity::class.java.getDeclaredField("subtitleUri").apply { isAccessible=true }
                    assertEquals(android.net.Uri.fromFile(file),field.get(activity))
                }
            }
            file.delete()
        }
    }

    @Test fun transportRemainsAtBottomAndLandscapeHasSecondaryActions() {
        launch().use { scenario ->
            waitReady(scenario)
            scenario.onActivity { activity ->
                val root=activity.findViewById<View>(R.id.playerRoot)
                val play=activity.findViewById<View>(R.id.btnPlayPause)
                val location=IntArray(2);play.getLocationOnScreen(location)
                assertTrue("Transport overlaps video center",location[1]+play.height/2>root.height*0.6)
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btnMore).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog=PlayerActivity::class.java.getDeclaredField("optionsDialog").apply { isAccessible=true }.get(activity) as android.app.Dialog
                assertTrue("Landscape sheet lost Lock",dialog.findViewById<View>(R.id.actionLock).isShown)
                assertTrue("Landscape sheet lost Speed",dialog.findViewById<View>(R.id.actionSpeed).isShown)
            }
        }
    }
    @Test fun touchLockSurvivesRecreationAndExternalOpenHasNoOldQueue() {
        launch().use { scenario ->
            waitReady(scenario)
            scenario.onActivity { it.findViewById<View>(R.id.btnLock).performClick() }
            scenario.recreate()
            waitReady(scenario)
            scenario.onActivity { activity ->
                val field=PlayerActivity::class.java.getDeclaredField("touchLocked").apply { isAccessible=true }
                assertTrue(field.getBoolean(activity))
                assertFalse(PlaybackQueue.autoAdvance)
                activity.findViewById<View>(R.id.btnUnlock).performClick()
                assertFalse(field.getBoolean(activity))
            }
        }
    }
}
