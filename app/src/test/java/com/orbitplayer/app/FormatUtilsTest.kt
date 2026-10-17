package com.orbitplayer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {
    @Test fun extensionFilterIsCaseInsensitive() {
        assertEquals("mkv", videoExtension("Holiday.MKV"))
        assertEquals("mp4", videoExtension("clip.archive.Mp4"))
    }
    @Test fun unnamedOrInvalidExtensionsAreNotOffered() {
        assertEquals("", videoExtension("video"))
        assertEquals("", videoExtension("movie."))
        assertEquals("", videoExtension("clip.a!"))
    }
    @Test fun shortVideo() { assertEquals("01:25", formatDuration(85_000)) }
    @Test fun longVideo() { assertEquals("1:01:01", formatDuration(3_661_000)) }
    @Test fun negativeDuration() { assertEquals("00:00", formatDuration(-123)) }
    @Test fun sizeInMegabytes() { assertEquals("1.5 MB", formatSize(1_572_864)) }
    @Test fun nestedInternalStorageFolder() {
        assertEquals("Movies/Camera", legacyMediaFolder("/storage/emulated/0/Movies/Camera/clip.mp4"))
    }
    @Test fun sdCardFoldersRemainDistinct() {
        assertEquals("ABCD-1234/Movies", legacyMediaFolder("/storage/ABCD-1234/Movies/movie.mkv"))
    }
    @Test fun legacySdcardPath() {
        assertEquals("Download/Clips", legacyMediaFolder("/sdcard/Download/Clips/test.mp4"))
    }
    @Test fun rootAndMissingFileHaveNoRelativeFolder() {
        assertEquals(null, legacyMediaFolder("/storage/emulated/0/clip.mp4"))
        assertEquals(null, legacyMediaFolder(null))
    }
}
