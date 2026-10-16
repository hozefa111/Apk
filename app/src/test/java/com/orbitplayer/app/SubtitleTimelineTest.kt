package com.orbitplayer.app

import org.junit.Assert.*
import org.junit.Test

class SubtitleTimelineTest {
    @Test fun srtOffsetAndGap() {
        val timeline = SubtitleTimeline.parse("1\n00:00:01,000 --> 00:00:02,500\nHello\n\n2\n00:00:03,000 --> 00:00:04,000\nWorld")
        assertEquals(2, timeline.size)
        assertEquals("", timeline.textAt(500))
        assertEquals("Hello", timeline.textAt(1500))
        assertEquals("", timeline.textAt(2700))
        assertEquals("World", timeline.textAt(3500))
        assertEquals("Hello", timeline.textAt(2500, 1000))
        assertEquals("World", timeline.textAt(2500, -1000))
    }
    @Test fun webvttAndBadEntries() {
        val text = "WEBVTT\n\n00:02.100 --> 00:03.250\n<b>Hi</b> there\n\nbroken entry"
        val timeline = SubtitleTimeline.parse(text)
        assertEquals(1, timeline.size)
        assertEquals("Hi there", timeline.textAt(2400))
    }
}
