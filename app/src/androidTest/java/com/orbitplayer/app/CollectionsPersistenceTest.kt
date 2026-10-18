package com.orbitplayer.app

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android preferences/JSON, in separate QA stores: never clears a user's library. */
@RunWith(AndroidJUnit4::class)
class CollectionsPersistenceTest {
    private lateinit var context: Context
    @Before fun prepare() {
        context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("qa_$name", mode)
        }
        listOf("orbit_collections_v1","orbit_queue_v1","orbit_preferences_v2").forEach {
            context.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun playlistRenameRemovalAndFavoriteSurviveNewInstances() {
        val collections = OrbitCollections(context)
        assertEquals(3,collections.add("Trip",listOf("content://a","content://b","content://c","content://a")))
        collections.add("Existing",emptyList())
        assertFalse(collections.rename("Trip","Existing"))
        assertFalse(collections.rename("Trip","  "))
        assertTrue(collections.rename("Trip","  Holiday  "))
        assertEquals(listOf("content://a","content://b","content://c"),OrbitCollections(context).items("Holiday"))
        collections.remove("Holiday","content://b")
        assertEquals(listOf("content://a","content://c"),OrbitCollections(context).items("Holiday"))
        assertTrue(collections.toggleFavorite("content://a"))
        assertTrue(OrbitCollections(context).isFavorite("content://a"))
        collections.delete("Holiday")
        assertTrue(OrbitCollections(context).isFavorite("content://a"))
        assertFalse(OrbitCollections(context).names().contains("Holiday"))
    }
    @Test fun settingsReuseExistingKeysAndClampInvalidValues() {
        val options=OrbitSettings(context)
        options.gallery=false; options.defaultAspect="Crop"; options.setFolder("hidden","Movies/Private",true)
        options.prefs.edit().putInt("folder_columns",99).putInt("seek_seconds",-3).commit()
        val restored=OrbitSettings(context)
        assertFalse(restored.gallery)
        assertEquals("Crop",restored.defaultAspect)
        assertTrue(restored.hasFolder("hidden","Movies/Private"))
        assertEquals(4,restored.folderColumns)
        assertEquals(5,restored.seekSeconds)
    }
    @Test fun queueIsolationAndStoredOrder() {
        val a=VideoItem(Uri.parse("content://media/external/video/media/1"),"a",60_000,100,"Movies",1)
        val b=a.copy(uri=Uri.parse("content://media/external/video/media/2"),title="b")
        PlaybackQueue.replace(listOf(a,b),true)
        PlaybackQueue.persist(context)
        PlaybackQueue.awaitPersistenceForTest()
        PlaybackQueue::class.java.getDeclaredField("restored").apply { isAccessible=true }.setBoolean(PlaybackQueue,false)
        PlaybackQueue.ensureRestored(context)
        assertTrue(PlaybackQueue.autoAdvance)
        assertEquals(b.uri,PlaybackQueue.adjacent(a.uri.toString(),1)?.uri)
        PlaybackQueue.replace(emptyList(),false)
        PlaybackQueue.ensureRestored(context)
        assertFalse(PlaybackQueue.autoAdvance)
        assertNull(PlaybackQueue.adjacent(a.uri.toString(),1))
        PlaybackQueue.persist(context)
        PlaybackQueue.awaitPersistenceForTest()
        PlaybackQueue.ensureRestored(context)
        assertFalse(PlaybackQueue.autoAdvance)
        assertNull(PlaybackQueue.adjacent(a.uri.toString(),1))
    }
}
