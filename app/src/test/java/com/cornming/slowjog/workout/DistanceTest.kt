package com.cornming.slowjog.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceTest {

    @Test
    fun stepsAreBpmTimesMinutes() {
        assertEquals(180.0, Distance.steps(60_000, 180), 1e-9)
        assertEquals(90.0, Distance.steps(30_000, 180), 1e-9)
        assertEquals(5400.0, Distance.steps(30 * 60_000L, 180), 1e-9)
    }

    @Test
    fun nonPositiveInputsGiveZeroSteps() {
        assertEquals(0.0, Distance.steps(0, 180), 0.0)
        assertEquals(0.0, Distance.steps(-100, 180), 0.0)
        assertEquals(0.0, Distance.steps(60_000, 0), 0.0)
    }

    @Test
    fun metersUseStride() {
        assertEquals(2700.0, Distance.meters(5400.0, 50), 1e-9)
        assertEquals(1620.0, Distance.meters(5400.0, 30), 1e-9)
    }

    @Test
    fun strideIsClamped() {
        assertEquals(Distance.meters(100.0, Distance.MAX_STRIDE_CM), Distance.meters(100.0, 500), 1e-9)
        assertEquals(Distance.meters(100.0, Distance.MIN_STRIDE_CM), Distance.meters(100.0, 1), 1e-9)
    }

    @Test
    fun metersPerMinute() {
        assertEquals(90.0, Distance.metersPerMinute(180, 50), 1e-9)
    }

    @Test
    fun minutesForRoundsUp() {
        assertEquals(34, Distance.minutesFor(3000, 180, 50)) // 3000 / 90 = 33.3
        assertEquals(10, Distance.minutesFor(900, 180, 50))
        assertNull(Distance.minutesFor(0, 180, 50))
        assertNull(Distance.minutesFor(1000, 0, 50))
    }
}
