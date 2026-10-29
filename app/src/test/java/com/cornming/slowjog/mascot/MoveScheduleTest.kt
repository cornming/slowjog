package com.cornming.slowjog.mascot

import com.cornming.slowjog.plan.StageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveScheduleTest {

    private fun min(m: Int) = m * 60_000L

    @Test
    fun everyStageExceptJogHasMoves() {
        assertTrue(MoveSchedule.movesFor(StageKind.JOG).isEmpty())
        assertEquals(5, MoveSchedule.movesFor(StageKind.STRENGTH).size)
        assertEquals(4, MoveSchedule.movesFor(StageKind.WARMUP).size)
        assertEquals(5, MoveSchedule.movesFor(StageKind.COOLDOWN).size)
        assertNull(MoveSchedule.at(StageKind.JOG, min(15), 1_000))
    }

    @Test
    fun defaultWarmupFitsAllFourMoves() {
        assertEquals(4, MoveSchedule.count(StageKind.WARMUP, min(5)))
        // 5 分鐘 / 4 個動作 = 每個 75 秒
        assertEquals(Move.MARCH, MoveSchedule.at(StageKind.WARMUP, min(5), 0)?.move)
        assertEquals(Move.MARCH, MoveSchedule.at(StageKind.WARMUP, min(5), 74_999)?.move)
        assertEquals(Move.ARM_CIRCLE, MoveSchedule.at(StageKind.WARMUP, min(5), 75_000)?.move)
        assertEquals(Move.TWIST, MoveSchedule.at(StageKind.WARMUP, min(5), 150_000)?.move)
        assertEquals(Move.SQUAT, MoveSchedule.at(StageKind.WARMUP, min(5), 299_999)?.move)
    }

    @Test
    fun defaultCooldownFitsAllFiveStretches() {
        assertEquals(5, MoveSchedule.count(StageKind.COOLDOWN, min(4)))
        val last = MoveSchedule.at(StageKind.COOLDOWN, min(4), 239_999)!!
        assertEquals(Move.CROSSARM, last.move)
        assertEquals(4, last.index)
        assertEquals(5, last.count)
        assertNull(last.next)
    }

    @Test
    fun shortStageDoesFewerMovesInOrder() {
        // 1 分鐘只夠 2 個動作（每個至少 30 秒）
        assertEquals(2, MoveSchedule.count(StageKind.WARMUP, min(1)))
        assertEquals(Move.ARM_CIRCLE, MoveSchedule.at(StageKind.WARMUP, min(1), 30_000)?.move)
        assertEquals(Move.ARM_CIRCLE, MoveSchedule.at(StageKind.WARMUP, min(1), 59_999)?.move)
        assertNull(MoveSchedule.at(StageKind.WARMUP, min(1), 59_999)?.next)
        assertEquals(Move.ARM_CIRCLE, MoveSchedule.at(StageKind.WARMUP, min(1), 0)?.next)
    }

    @Test
    fun aTinyStageStillGetsOneMove() {
        assertEquals(1, MoveSchedule.count(StageKind.COOLDOWN, 20_000))
    }

    @Test
    fun phaseWalksThroughTheCycleAndWraps() {
        val march = Move.MARCH // 1.6 秒一個循環
        val a = MoveSchedule.at(StageKind.WARMUP, min(5), 0)!!
        assertEquals(0f, a.phase, 1e-4f)
        val b = MoveSchedule.at(StageKind.WARMUP, min(5), 800)!!
        assertEquals(0.5f, b.phase, 1e-4f)
        val c = MoveSchedule.at(StageKind.WARMUP, min(5), 1_600)!!
        assertEquals(0f, c.phase, 1e-4f)
        assertEquals(march, c.move)
    }

    @Test
    fun sidedMovesMirrorEveryOtherCycle() {
        // 小腿伸展一個循環 16 秒，每 48 秒一個動作（4 分鐘 / 5）
        val first = MoveSchedule.at(StageKind.COOLDOWN, min(4), 1_000)!!
        val second = MoveSchedule.at(StageKind.COOLDOWN, min(4), 17_000)!!
        val third = MoveSchedule.at(StageKind.COOLDOWN, min(4), 33_000)!!
        assertEquals(Move.CALF, first.move)
        assertFalse(first.mirrored)
        assertTrue(second.mirrored)
        assertFalse(third.mirrored)
    }

    @Test
    fun movesWithoutSidesNeverMirror() {
        // 前彎伸展：第三個動作，開始於 96 秒
        val slot = MoveSchedule.at(StageKind.COOLDOWN, min(4), 96_000 + 17_000)!!
        assertEquals(Move.FORWARD, slot.move)
        assertFalse(slot.mirrored)
    }

    @Test
    fun cycleStartsOverWhenMoveChanges() {
        val slot = MoveSchedule.at(StageKind.COOLDOWN, min(4), 48_000)!!
        assertEquals(Move.QUAD, slot.move)
        assertEquals(0f, slot.phase, 1e-4f)
        assertFalse(slot.mirrored)
    }

    @Test
    fun beyondTheEndStaysOnTheLastMove() {
        val slot = MoveSchedule.at(StageKind.WARMUP, min(5), min(5) + 10_000)!!
        assertEquals(Move.SQUAT, slot.move)
    }

    @Test
    fun negativeElapsedGivesNothing() {
        assertNull(MoveSchedule.at(StageKind.WARMUP, min(5), -1))
    }

    @Test
    fun everyMoveHasASheetNameAndTip() {
        for (m in Move.values()) {
            assertTrue(m.id.isNotBlank())
            assertTrue(m.nameZh.isNotBlank())
            assertTrue(m.tip.isNotBlank())
            assertTrue(m.cycleSec > 0f)
        }
        assertEquals(Move.values().map { it.id }.toSet().size, Move.values().size)
    }

    @Test
    fun defaultStrengthStageDoesFiveMovesOf72Seconds() {
        assertEquals(5, MoveSchedule.count(StageKind.STRENGTH, min(6)))
        assertEquals(Move.DEEP_SQUAT, MoveSchedule.at(StageKind.STRENGTH, min(6), 71_999)?.move)
        assertEquals(Move.LUNGE, MoveSchedule.at(StageKind.STRENGTH, min(6), 72_000)?.move)
        assertEquals(Move.SIDE_LEG, MoveSchedule.at(StageKind.STRENGTH, min(6), min(6) - 1)?.move)
    }

    @Test
    fun lungeSwitchesLegsEveryCycle() {
        // 弓箭步一個循環 3 秒，從 72 秒開始
        assertFalse(MoveSchedule.at(StageKind.STRENGTH, min(6), 72_000 + 1_000)!!.mirrored)
        assertTrue(MoveSchedule.at(StageKind.STRENGTH, min(6), 72_000 + 4_000)!!.mirrored)
    }
}
