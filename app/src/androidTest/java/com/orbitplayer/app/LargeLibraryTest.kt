package com.orbitplayer.app

import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Synthetic metadata through the real Activity/filter/diff/RecyclerView path.
 * All URIs reference the same indexed smoke video with distinct query keys.
 * This is a deterministic UI regression, not a phone/storage performance claim. */
@RunWith(AndroidJUnit4::class)
class LargeLibraryTest {
    private fun waitFor(scenario: ActivityScenario<MainActivity>, message: String,
        condition: (MainActivity) -> Boolean) {
        val until = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < until) {
            var done = false
            scenario.onActivity { done = condition(it) }
            if (done) return
            Thread.sleep(100)
        }
        fail(message)
    }

    @Test fun syntheticLibrariesRenderSearchAndDiscardOldQueries() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val settings = OrbitSettings(context)
        val previousFolder = settings.lastFolder
        val previousTab = settings.lastTab
        val sourceField = MainActivity::class.java.getDeclaredField("allVideos").apply { isAccessible = true }
        val scanField = MainActivity::class.java.getDeclaredField("scanInFlight").apply { isAccessible = true }
        val filter = MainActivity::class.java.getDeclaredMethod("filterVideos").apply { isAccessible = true }
        val refresh = MainActivity::class.java.getDeclaredMethod("refreshContinueCard").apply { isAccessible = true }
        val candidate = MainActivity::class.java.getDeclaredField("continueVideo").apply { isAccessible = true }
        val progress = context.getSharedPreferences("playback_positions_v1", android.content.Context.MODE_PRIVATE)
        var progressBackup = emptyMap<String, Any?>()
        try {
            settings.lastFolder = "All folders"
            settings.lastTab = "FOLDERS"
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitFor(scenario, "Initial MediaStore scan did not finish") { activity ->
                    !scanField.getBoolean(activity) && (sourceField.get(activity) as List<*>).isNotEmpty()
                }
                lateinit var base: VideoItem
                scenario.onActivity { activity ->
                    base = (sourceField.get(activity) as List<*>).first() as VideoItem
                    activity.findViewById<View>(R.id.navHome).performClick()
                }
                for (size in listOf(0, 1, 10, 100, 1_000)) {
                    val source = (0 until size).map { index -> base.copy(
                        uri = base.uri.buildUpon().appendQueryParameter("orbit_qa", index.toString()).build(),
                        title = String.format(Locale.ROOT, "Synthetic %05d.mp4", index),
                        folder = "QA/${index % 50}", modifiedSeconds = index.toLong()) }
                    scenario.onActivity { activity -> sourceField.set(activity, source); filter.invoke(activity) }
                    waitFor(scenario, "Synthetic library did not render $size videos") { activity ->
                        val list = activity.findViewById<RecyclerView>(R.id.videos)
                        list.adapter is VideoAdapter && list.adapter?.itemCount == size &&
                            activity.findViewById<android.widget.TextView>(R.id.count).text.toString() ==
                            activity.resources.getQuantityString(R.plurals.library_video_count, size, size, size)
                    }
                    scenario.onActivity { activity ->
                        assertEquals(size == 0, activity.findViewById<View>(R.id.empty).visibility == View.VISIBLE)
                        if (size > 0) activity.findViewById<RecyclerView>(R.id.videos).scrollToPosition(size - 1)
                    }
                    if (size > 0) waitFor(scenario, "Could not scroll to the last of $size videos") {
                        it.findViewById<RecyclerView>(R.id.videos).findViewHolderForAdapterPosition(size - 1) != null
                    }
                }
                scenario.onActivity { it.findViewById<EditText>(R.id.search).setText("Synthetic 00999") }
                waitFor(scenario, "Search failed to return the matching synthetic item") { activity ->
                    val field = MainActivity::class.java.getDeclaredField("visibleVideos").apply { isAccessible = true }
                    val shown = field.get(activity) as List<*>
                    shown.size == 1 && (shown.single() as VideoItem).title == "Synthetic 00999.mp4" &&
                        activity.findViewById<RecyclerView>(R.id.videos).adapter?.itemCount == 1
                }
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.search).apply {
                        setText("Synthetic 00001")
                        setText("no matching synthetic file")
                    }
                }
                waitFor(scenario, "An older search result replaced the final empty query") { activity ->
                    activity.findViewById<RecyclerView>(R.id.videos).adapter?.itemCount == 0 &&
                        activity.findViewById<View>(R.id.empty).visibility == View.VISIBLE
                }
                lateinit var resume: VideoItem
                scenario.onActivity { resume = (sourceField.get(it) as List<*>).last() as VideoItem }
                val key = "position_${resume.uri.toString().hashCode()}"
                val saved = progress.all
                progressBackup = listOf(key, "last_played_uri").associateWith { saved[it] }
                progress.edit().putLong(key, 10_000L).putString("last_played_uri", resume.uri.toString()).apply()
                scenario.onActivity { refresh.invoke(it) }
                waitFor(scenario, "Continue card failed to load its worker result") {
                    (candidate.get(it) as? VideoItem)?.uri == resume.uri
                }
                progress.edit().remove(key).apply()
                scenario.onActivity { activity ->
                    refresh.invoke(activity)
                    assertNull("Cleared progress left a stale card clickable", candidate.get(activity))
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.continueCard).visibility)
                }
                // Wait for worker completion, then verify no older result resurfaced.
                val executor = MainActivity::class.java.getDeclaredField("filterExecutor").apply { isAccessible = true }
                lateinit var idle: java.util.concurrent.Future<*>
                scenario.onActivity { idle = (executor.get(it) as java.util.concurrent.ExecutorService).submit {} }
                idle.get(30, java.util.concurrent.TimeUnit.SECONDS)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { assertNull(candidate.get(it)) }
            }
        } finally {
            settings.lastFolder = previousFolder
            settings.lastTab = previousTab
            val editor = progress.edit()
            for ((key, value) in progressBackup) when (value) {
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                null -> editor.remove(key)
            }
            editor.apply()
        }
    }
}
