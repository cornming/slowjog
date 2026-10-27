package com.cornming.slowjog.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanTest {

    private val plan = WorkoutPlan.of(5, 15, 6, 4)
    private fun min(m: Int) = m * 60_000L

    @Test
    fun defaultPlanIsThirtyMinutes() {
        assertEquals(min(30), plan.totalMs)
        assertEquals(4, plan.stages.size)
    }

    @Test
    fun positionFollowsElapsedTime() {
        val start = plan.position(0)
        assertEquals(0, start.index)
        assertEquals(StageKind.WARMUP, start.stage?.kind)
        assertEquals(min(5), start.remainingMs)

        val jog = plan.position(min(5))
        assertEquals(StageKind.JOG, jog.stage?.kind)
        assertEquals(min(15), jog.remainingMs)

        val strength = plan.position(min(20) + 1_000)
        assertEquals(StageKind.STRENGTH, strength.stage?.kind)

        val cooldown = plan.position(min(29))
        assertEquals(StageKind.COOLDOWN, cooldown.stage?.kind)
        assertEquals(min(1), cooldown.remainingMs)
    }

    @Test
    fun finishedPlanIsPastTheLastStage() {
        val done = plan.position(min(30))
        assertEquals(4, done.index)
        assertNull(done.stage)
        assertEquals(4, plan.position(min(99)).index)
    }

    @Test
    fun zeroMinuteStagesAreSkipped() {
        val noStrength = WorkoutPlan.of(5, 15, 0, 4)
        assertEquals(3, noStrength.stages.size)
        assertEquals(StageKind.COOLDOWN, noStrength.position(min(20)).stage?.kind)
        assertEquals(min(24), noStrength.totalMs)
    }

    @Test
    fun nextStage() {
        assertEquals(StageKind.JOG, plan.next(0)?.kind)
        assertNull(plan.next(3))
    }

    @Test
    fun beepCountsDifferPerStage() {
        assertEquals(listOf(1, 2, 3, 4), StageKind.values().map { it.beepCount })
    }

    @Test
    fun nearBoundary() {
        assertTrue(plan.isNearBoundary(min(5) + 1_000, 1_500))
        assertTrue(plan.isNearBoundary(min(5) - 1_000, 1_500))
        assertFalse(plan.isNearBoundary(min(10), 1_500))
    }
}
