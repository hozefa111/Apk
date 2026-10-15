package com.orbitplayer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {
    @Test fun shortVideo() { assertEquals("01:25", formatDuration(85_000)) }
    @Test fun longVideo() { assertEquals("1:01:01", formatDuration(3_661_000)) }
    @Test fun negativeDuration() { assertEquals("00:00", formatDuration(-123)) }
    @Test fun sizeInMegabytes() { assertEquals("1.5 MB", formatSize(1_572_864)) }
}
