package com.cornming.slowjog.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTest {

    private val time30 = Target(TargetType.TIME, minutes = 30, distanceM = 2000)
    private val dist2k = Target(TargetType.DISTANCE, minutes = 30, distanceM = 2000)

    @Test
    fun timeTargetIgnoresDistance() {
        assertFalse(time30.isReached(29 * 60_000L, 99_999.0))
        assertTrue(time30.isReached(30 * 60_000L, 0.0))
    }

    @Test
    fun distanceTargetIgnoresTime() {
        assertFalse(dist2k.isReached(99 * 60_000L, 1999.0))
        assertTrue(dist2k.isReached(0, 2000.0))
    }

    @Test
    fun zeroMeansDisabled() {
        assertFalse(Target(TargetType.TIME, 0, 2000).enabled)
        assertFalse(Target(TargetType.DISTANCE, 30, 0).enabled)
        assertFalse(Target(TargetType.DISTANCE, 30, 0).isReached(Long.MAX_VALUE, 1e9))
        assertTrue(dist2k.enabled)
    }

    @Test
    fun remainingMeters() {
        assertEquals(500, dist2k.remainingMeters(1500.0))
        assertEquals(1, dist2k.remainingMeters(1999.2)) // 往上取整
        assertEquals(0, dist2k.remainingMeters(2500.0))
        assertNull(time30.remainingMeters(0.0))
        assertNull(Target(TargetType.DISTANCE, 30, 0).remainingMeters(0.0))
    }
}
