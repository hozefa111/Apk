package com.orbitplayer.app

import org.junit.Assert.*
import org.junit.Test

class LibraryEngineTest {
    private fun video(key: String, title: String = "$key.mp4", folder: String = "Movies", duration: Long = 60_000,
        size: Long = 100, date: Long = 100) = LibraryEntry(key, title, folder, duration, size, date)

    @Test fun emptyAndSingleLibraries() {
        assertTrue(LibraryEngine.videos(emptyList(), LibraryQuery()).isEmpty())
        val one = video("one")
        assertEquals(listOf(one), LibraryEngine.videos(listOf(one), LibraryQuery()))
    }
    @Test fun folderSearchKeepsWholeAlbumCounts() {
        val source = listOf(video("a", "Holiday.MP4", "Movies/Camera"), video("b", folder="Movies/Camera"),
            video("c", folder="Download/Camera"))
        val albums = LibraryEngine.albums(source, "holiday", emptySet(), emptySet(), 0)
        assertEquals(1, albums.size)
        assertEquals(2, albums.single().items.size)
        assertEquals(2, LibraryEngine.videos(source, LibraryQuery(text="movies/camera")).size)
    }
    @Test fun hiddenFoldersAreExcludedFromEveryCollection() {
        val source = listOf(video("a", folder="Hidden"), video("b"))
        for (filter in listOf("ALL", "FAVORITES", "HISTORY", "CONTINUE", "RECENT")) {
            val query = LibraryQuery(filter=filter, hidden=setOf("Hidden"), favorites=setOf("a","b"),
                history=listOf("a","b"), positions=mapOf("a" to 10_000L,"b" to 10_000L), nowSeconds=100)
            assertEquals(listOf("b"), LibraryEngine.videos(source, query).map { it.key })
        }
        assertEquals(listOf("Movies"), LibraryEngine.albums(source,"",setOf("Hidden"),emptySet(),0).map { it.path })
    }
    @Test fun playlistsAndHistoryRespectRecordedOrderAndIgnoreMissingMedia() {
        val source = listOf(video("a"), video("b"), video("c"))
        assertEquals(listOf("c","a"), LibraryEngine.videos(source, LibraryQuery(playlist=listOf("missing","c","a"))).map { it.key })
        assertEquals(listOf("b","a"), LibraryEngine.videos(source, LibraryQuery(filter="HISTORY",history=listOf("missing","b","a"))).map { it.key })
    }
    @Test fun formatFolderAndTextFiltersCombine() {
        val source = listOf(video("a","Holiday.MKV","Trips/2026"),video("b","Holiday.mp4","Trips/2026"),video("c","Holiday.mkv","Other"))
        assertEquals(listOf("a"),LibraryEngine.videos(source,LibraryQuery(text="holiday",folder="Trips/2026",format="mkv")).map { it.key })
    }
    @Test fun allSortModesHaveDeterministicResults() {
        val source = listOf(video("a","z.mp4",duration=10,size=500,date=5),video("b","a.mp4",duration=30,size=100,date=3))
        for ((sort, expected) in mapOf("NEWEST" to listOf("a","b"),"OLDEST" to listOf("b","a"),
            "NAME" to listOf("b","a"),"SIZE" to listOf("a","b"),"DURATION" to listOf("b","a"))) {
            assertEquals(sort,expected,LibraryEngine.videos(source,LibraryQuery(sort=sort)).map { it.key })
        }
    }
    @Test fun pinnedFoldersPrecedeNameCountAndRecentSorting() {
        val source = listOf(video("a",folder="A",date=99),video("b",folder="Z",date=1),video("c",folder="A"))
        for (sort in 0..2) assertEquals("Z",LibraryEngine.albums(source,"",emptySet(),setOf("Z"),sort).first().path)
    }
    @Test fun primaryKeysSurviveAndSdCardFoldersDoNotMerge() {
        assertEquals("Movies/Camera",mediaFolder("Movies/Camera/","external_primary","Camera"))
        assertEquals("abcd-1234/Movies/Camera",mediaFolder("Movies/Camera/","abcd-1234","Camera"))
        assertEquals("Camera",mediaFolder(null,null,"Camera"))
        assertEquals("Other",mediaFolder(null,null,null))
    }
    @Test fun resumeExcludesUnstartedAndCompletedMedia() {
        assertFalse(hasResumePosition(2_999,60_000))
        assertTrue(hasResumePosition(3_000,60_000))
        assertFalse(hasResumePosition(55_000,60_000))
        assertFalse(hasResumePosition(5_000,0))
    }
    @Test fun generatedLibrariesKeepCountsSearchAndOrderedQueuesAtScale() {
        for (size in listOf(0,1,10,100,1_000,10_000)) {
            val source=(0 until size).map { video("id$it",folder="Folder/${it%50}",duration=it*1000L) }
            val start=System.nanoTime()
            val videos=LibraryEngine.videos(source,LibraryQuery(sort="DURATION"))
            val albums=LibraryEngine.albums(source,"",emptySet(),emptySet(),1)
            assertEquals(size,videos.size)
            assertEquals(size,albums.sumOf { it.items.size })
            assertEquals(source.count { it.folder=="Folder/1" },LibraryEngine.videos(source,LibraryQuery(folder="Folder/1")).size)
            val order=source.takeLast(100).reversed().map { it.key }
            assertEquals(order,LibraryEngine.videos(source,LibraryQuery(playlist=order)).map { it.key })
            println("LIBRARY_FIXTURE size=$size elapsedMs=${(System.nanoTime()-start)/1_000_000.0}")
        }
    }
}
