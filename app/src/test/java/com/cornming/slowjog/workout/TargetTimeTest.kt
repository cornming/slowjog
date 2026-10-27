package com.cornming.slowjog.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTimeTest {

    @Test
    fun reachedExactlyAtTarget() {
        assertFalse(TargetTime.isReached(30 * 60_000L - 1, 30))
        assertTrue(TargetTime.isReached(30 * 60_000L, 30))
        assertTrue(TargetTime.isReached(31 * 60_000L, 30))
    }

    @Test
    fun zeroMeansNoTarget() {
        assertFalse(TargetTime.isReached(10 * 60_000L, 0))
        assertNull(TargetTime.remainingSec(10 * 60_000L, 0))
    }

    @Test
    fun remainingRoundsUpAndStopsAtZero() {
        assertEquals(1800, TargetTime.remainingSec(0, 30))
        assertEquals(1, TargetTime.remainingSec(30 * 60_000L - 1, 30))
        assertEquals(0, TargetTime.remainingSec(30 * 60_000L, 30))
        assertEquals(0, TargetTime.remainingSec(40 * 60_000L, 30))
    }
}
