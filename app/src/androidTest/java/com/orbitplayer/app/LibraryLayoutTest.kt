package com.orbitplayer.app

import android.content.pm.ActivityInfo
import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLayoutTest {
    @Test fun landscapeKeepsFoldersVisibleAndBackReturnsToAlbums() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Library unexpectedly focused the keyboard",activity.findViewById<EditText>(R.id.search).hasFocus())
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            val until=System.currentTimeMillis()+30_000
            var ready=false
            while(!ready && System.currentTimeMillis()<until) {
                scenario.onActivity { activity ->
                    val recycler=activity.findViewById<RecyclerView>(R.id.videos)
                    ready=activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                        recycler.adapter is FolderAdapter && (recycler.adapter?.itemCount ?: 0)>0 && recycler.getChildAt(0)!=null
                }
                if(!ready) Thread.sleep(100)
            }
            assertTrue("No folder UI after landscape transition",ready)
            scenario.onActivity { activity ->
                val recycler=activity.findViewById<RecyclerView>(R.id.videos)
                assertTrue("Chrome consumed the browser",recycler.height>=72*activity.resources.displayMetrics.density)
                recycler.getChildAt(0).performClick()
            }
            val opened=System.currentTimeMillis()+15_000
            var videos=false
            while(!videos && System.currentTimeMillis()<opened) {
                scenario.onActivity { activity ->
                    videos=activity.findViewById<RecyclerView>(R.id.videos).adapter is VideoAdapter
                }
                if(!videos) Thread.sleep(100)
            }
            assertTrue("Folder tap did not open videos",videos)
            scenario.onActivity { activity ->
                val back=activity.findViewById<View>(R.id.libraryBack)
                assertTrue("Filters consumed the video browser",activity.findViewById<RecyclerView>(R.id.videos).height>=72*activity.resources.displayMetrics.density)
                assertEquals(View.VISIBLE,activity.findViewById<View>(R.id.btnFilters).visibility)
                assertEquals(View.VISIBLE,back.visibility)
                back.performClick()
            }
        }
    }
}
