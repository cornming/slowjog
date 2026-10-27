package com.cornming.slowjog.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimerTest {

    @Test
    fun countsWholeWorkoutAndJogSeparately() {
        val t = WorkoutTimer()
        t.start(0)
        assertTrue(t.startJog(5 * 60_000L))      // 暖身 5 分鐘後開始超慢跑
        t.endJog(20 * 60_000L)                    // 跑 15 分鐘
        val summary = t.finish(30 * 60_000L)      // 整段 30 分鐘
        assertEquals(1800, summary.durationSec)
        assertEquals(900, summary.jogSec)
    }

    @Test
    fun pauseStopsBothClocks() {
        val t = WorkoutTimer()
        t.start(0)
        t.startJog(0)
        t.pause(10_000)
        assertEquals(10_000L, t.elapsedMs(100_000))
        assertEquals(10_000L, t.jogMs(100_000))
        t.resume(100_000)
        val summary = t.finish(110_000)
        assertEquals(20, summary.durationSec)
        assertEquals(20, summary.jogSec)
    }

    @Test
    fun jogTimeStaysFrozenWhileJogIsOff() {
        val t = WorkoutTimer()
        t.start(0)
        t.startJog(0)
        t.endJog(60_000)
        assertEquals(60_000L, t.jogMs(120_000))
        assertEquals(120_000L, t.elapsedMs(120_000))
    }

    @Test
    fun canJogMoreThanOnce() {
        val t = WorkoutTimer()
        t.start(0)
        t.startJog(0)
        t.endJog(10_000)
        t.startJog(20_000)
        val summary = t.finish(50_000)
        assertEquals(50, summary.durationSec)
        assertEquals(40, summary.jogSec)
    }

    @Test
    fun cannotStartJogWhilePausedOrBeforeStart() {
        val t = WorkoutTimer()
        assertFalse(t.startJog(0))
        t.start(0)
        t.pause(1_000)
        assertFalse(t.startJog(2_000))
        assertFalse(t.jogging)
    }

    @Test
    fun finishResetsToNotStarted() {
        val t = WorkoutTimer()
        t.start(0)
        t.finish(5_000)
        assertFalse(t.started)
        assertEquals(0L, t.elapsedMs(10_000))
    }

    @Test
    fun clockGoingBackwardsNeverReducesTime() {
        val t = WorkoutTimer()
        t.start(10_000)
        t.pause(5_000)
        assertEquals(0L, t.elapsedMs(5_000))
    }
}
