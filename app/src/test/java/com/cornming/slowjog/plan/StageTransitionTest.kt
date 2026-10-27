package com.cornming.slowjog.plan

import com.cornming.slowjog.plan.StageTransition.JogAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StageTransitionTest {

    @Test
    fun countdownShowsFiveToOneInTheLastFiveSeconds() {
        assertNull(StageTransition.countdown(5_001, true))
        assertEquals(5, StageTransition.countdown(5_000, true))
        assertEquals(5, StageTransition.countdown(4_001, true))
        assertEquals(4, StageTransition.countdown(4_000, true))
        assertEquals(2, StageTransition.countdown(1_001, true))
        assertEquals(1, StageTransition.countdown(1_000, true))
        assertEquals(1, StageTransition.countdown(1, true))
    }

    @Test
    fun noCountdownAtOrAfterTheBoundary() {
        assertNull(StageTransition.countdown(0, true))
        assertNull(StageTransition.countdown(-100, true))
    }

    @Test
    fun noCountdownInTheLastStage() {
        assertNull(StageTransition.countdown(3_000, false))
    }

    @Test
    fun spokenNumbersAreChinese() {
        assertEquals("五", StageTransition.spoken(5))
        assertEquals("一", StageTransition.spoken(1))
        assertEquals("10", StageTransition.spoken(10)) // 超出 0 到 5 就直接念數字
    }

    @Test
    fun enteringJogStageStartsJogging() {
        assertEquals(JogAction.START, StageTransition.jogAction(StageKind.JOG, jogging = false))
        assertEquals(JogAction.NONE, StageTransition.jogAction(StageKind.JOG, jogging = true))
    }

    @Test
    fun leavingJogStageStopsJogging() {
        assertEquals(JogAction.END, StageTransition.jogAction(StageKind.STRENGTH, jogging = true))
        assertEquals(JogAction.END, StageTransition.jogAction(StageKind.COOLDOWN, jogging = true))
        assertEquals(JogAction.END, StageTransition.jogAction(null, jogging = true))
    }

    @Test
    fun otherStagesLeaveNotJoggingAlone() {
        assertEquals(JogAction.NONE, StageTransition.jogAction(StageKind.WARMUP, jogging = false))
        assertEquals(JogAction.NONE, StageTransition.jogAction(StageKind.STRENGTH, jogging = false))
        assertEquals(JogAction.NONE, StageTransition.jogAction(null, jogging = false))
    }
}
