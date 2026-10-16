package com.orbitplayer.app

import org.junit.Assert.*
import org.junit.Test

class NetworkVideoUrlTest {
    @Test fun httpsStreamsOnly() {
        assertEquals("https://example.com/movie.mp4", NetworkVideoUrl.validated(" https://example.com/movie.mp4 "))
        assertNotNull(NetworkVideoUrl.validated("https://example.com/master.m3u8"))
        assertNull(NetworkVideoUrl.validated("http://example.com/movie.mp4"))
        assertNull(NetworkVideoUrl.validated("file:///sdcard/secret.mp4"))
        assertNull(NetworkVideoUrl.validated("https://user:pass@example.com/movie.mp4"))
        assertNull(NetworkVideoUrl.validated("javascript:alert(1)"))
    }
}
