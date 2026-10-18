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

    @Test fun queueSheetReordersRemovesRestoresAndPlaysSelectedVideo() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val base=context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),"${MediaStore.Video.Media.DISPLAY_NAME} = ?",arrayOf("orbit_smoke.mp4"),null)?.use {
            if(it.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,it.getLong(0)) else null
        }!!
        val copies=listOf("queue-b.mp4","queue-c.mp4").map { name ->
            java.io.File(context.cacheDir,name).apply {
                context.contentResolver.openInputStream(base)!!.use { input -> outputStream().use(input::copyTo) }
            }
        }
        val a=VideoItem(base,"Queue A",28_000,1,"Movies",1)
        val b=a.copy(uri=android.net.Uri.fromFile(copies[0]),title="Queue B")
        val c=a.copy(uri=android.net.Uri.fromFile(copies[1]),title="Queue C")
        val previous=PlaybackQueue.snapshot()
        val autoAdvance=PlaybackQueue.autoAdvance
        val settings=OrbitSettings(context)
        val previousRepeat=settings.repeat
        fun sheet(activity: PlayerActivity)=PlayerActivity::class.java.getDeclaredField("queueDialog")
            .apply { isAccessible=true }.get(activity) as PlaybackQueueDialog
        fun menu(dialog: PlaybackQueueDialog)=PlaybackQueueDialog::class.java.getDeclaredField("menu")
            .apply { isAccessible=true }.get(dialog) as android.app.AlertDialog
        fun choose(dialog: android.app.AlertDialog, label: String) {
            val list=dialog.listView
            val index=(0 until list.adapter.count).first { list.adapter.getItem(it).toString()==label }
            list.performItemClick(list.getChildAt(index-list.firstVisiblePosition),index,list.adapter.getItemId(index))
        }
        fun waitRows(scenario: ActivityScenario<PlayerActivity>, titles: List<String>) {
            val until=System.currentTimeMillis()+15_000
            var loaded=false
            while(!loaded && System.currentTimeMillis()<until) {
                scenario.onActivity { activity ->
                    val list=sheet(activity).findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.queueItems)
                    loaded=list.adapter?.itemCount==titles.size && titles.withIndex().all { (index,title) ->
                        list.findViewHolderForAdapterPosition(index)?.itemView
                            ?.findViewById<android.widget.TextView>(R.id.queueTitle)?.text?.toString()==title
                    }
                }
                if(!loaded) Thread.sleep(100)
            }
            assertTrue("Queue rows did not show $titles",loaded)
        }
        fun waitSelected(scenario: ActivityScenario<PlayerActivity>, expected: android.net.Uri) {
            val until=System.currentTimeMillis()+30_000
            var selected=false
            while(!selected && System.currentTimeMillis()<until) {
                scenario.onActivity { activity ->
                    selected=engine(activity)?.let { it.playbackState==Player.STATE_READY && it.currentMediaItem?.localConfiguration?.uri==expected && it.playWhenReady } == true
                }
                if(!selected) Thread.sleep(100)
            }
            assertTrue("Queue did not play $expected",selected)
        }
        try {
            settings.repeat=false
            PlaybackQueue.replace(listOf(a,b,c),true)
            ActivityScenario.launch<PlayerActivity>(Intent(context,PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URI,a.uri.toString());putExtra(PlayerActivity.EXTRA_TITLE,a.title)
            }).use { scenario ->
                waitReady(scenario)
                scenario.onActivity { activity ->
                    activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    engine(activity)!!.apply { pause();seekTo(0) }
                    activity.findViewById<View>(R.id.playerHeading).performClick()
                }
                scenario.recreate()
                waitReady(scenario)
                scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitRows(scenario,listOf("Queue A","Queue B","Queue C"))
                scenario.onActivity { activity ->
                    val dialog=sheet(activity)
                    val list=dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.queueItems)
                    list.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<View>(R.id.queueMore).performClick()
                    choose(menu(dialog),activity.getString(R.string.queue_move_down))
                    assertEquals(listOf(a,c,b),PlaybackQueue.snapshot())
                }
                waitRows(scenario,listOf("Queue A","Queue C","Queue B"))
                scenario.onActivity { activity ->
                    val dialog=sheet(activity)
                    val list=dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.queueItems)
                    list.findViewHolderForAdapterPosition(2)!!.itemView.findViewById<View>(R.id.queueMore).performClick()
                    choose(menu(dialog),activity.getString(R.string.queue_remove))
                    assertEquals(listOf(a,c),PlaybackQueue.snapshot())
                }
                waitRows(scenario,listOf("Queue A","Queue C"))
                scenario.onActivity { activity ->
                    val toggle=sheet(activity).findViewById<android.widget.Switch>(R.id.queueAutoplay)
                    toggle.performClick()
                    assertFalse(PlaybackQueue.autoAdvance)
                    toggle.performClick()
                    assertTrue(PlaybackQueue.autoAdvance)
                    engine(activity)!!.apply { seekTo((duration-500).coerceAtLeast(0));play() }
                }
                waitSelected(scenario,c.uri)
                waitRows(scenario,listOf("Queue A","Queue C"))
                scenario.onActivity { activity ->
                    val dialog=sheet(activity)
                    val list=dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.queueItems)
                    assertEquals("▶",list.findViewHolderForAdapterPosition(1)!!.itemView.findViewById<android.widget.TextView>(R.id.queueNumber).text.toString())
                    list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
                }
                waitSelected(scenario,a.uri)
                scenario.onActivity { activity ->
                    assertEquals("Queue A",activity.findViewById<android.widget.TextView>(R.id.playerTitle).text.toString())
                    assertFalse(activity.findViewById<View>(R.id.btnPrevious).isEnabled)
                    assertTrue(activity.findViewById<View>(R.id.btnNext).isEnabled)
                }
            }
        } finally {
            settings.repeat=previousRepeat
            PlaybackQueue.replace(previous,autoAdvance)
            PlaybackQueue.persist(context)
            PlaybackQueue.awaitPersistenceForTest()
            copies.forEach { it.delete() }
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
