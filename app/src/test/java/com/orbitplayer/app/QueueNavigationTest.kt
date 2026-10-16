package com.orbitplayer.app

import org.junit.Assert.*
import org.junit.Test

/** Deterministic tests for the queue boundaries and intentional auto-next behaviour. */
class QueueNavigationTest {
    @Test fun nextAndPreviousFromMiddle() {
        assertEquals(3, QueueNavigation.adjacentIndex(2, 5, 1))
        assertEquals(1, QueueNavigation.adjacentIndex(2, 5, -1))
    }
    @Test fun neverWrapBeyondEdges() {
        assertEquals(-1, QueueNavigation.adjacentIndex(0, 5, -1))
        assertEquals(-1, QueueNavigation.adjacentIndex(4, 5, 1))
        assertEquals(-1, QueueNavigation.adjacentIndex(0, 0, 1))
    }
    @Test fun handleMissingUriOrInvalidIndex() {
        assertEquals(-1, QueueNavigation.adjacentIndex(-1, 4, 1))
        assertEquals(-1, QueueNavigation.adjacentIndex(5, 4, -1))
        assertEquals(-1, QueueNavigation.adjacentIndex(1, 4, Int.MAX_VALUE))
    }
    @Test fun onlyPlayAllAdvancesOnNaturalEnd() {
        assertTrue(QueueNavigation.shouldAutoAdvance(false, true, true))
        assertFalse(QueueNavigation.shouldAutoAdvance(true, true, true))
        assertFalse(QueueNavigation.shouldAutoAdvance(false, false, true))
        assertFalse(QueueNavigation.shouldAutoAdvance(false, true, false))
    }
}
