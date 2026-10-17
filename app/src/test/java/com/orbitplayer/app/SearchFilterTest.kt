package com.orbitplayer.app

import org.junit.Assert.*
import org.junit.Test

class SearchFilterTest {
    @Test fun formatAndResolution() {
        val filter=OrbitSearchFilter(extension="mkv", minimumHeight=1080)
        assertTrue(filter.matchesMetadata("Film.MKV",1080,3600000,123456))
        assertFalse(filter.matchesMetadata("Film.mp4",1080,3600000,123456))
        assertFalse(filter.matchesMetadata("Film.mkv",720,3600000,123456))
    }
    @Test fun durationAndFileSize() {
        val filter=OrbitSearchFilter(durationGroup=2,sizeGroup=1)
        assertTrue(filter.matchesMetadata("clip.mp4",480,600000,20000000))
        assertFalse(filter.matchesMetadata("clip.mp4",480,30000,20000000))
        assertFalse(filter.matchesMetadata("clip.mp4",480,600000,200000000))
    }
    @Test fun resetShowsEverything() {
        assertTrue(OrbitSearchFilter().matchesMetadata("anything.iso",0,0,0))
    }
}
