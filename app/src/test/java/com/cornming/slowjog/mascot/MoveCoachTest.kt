package com.cornming.slowjog.mascot

import com.cornming.slowjog.plan.StageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveCoachTest {

    // 預設伸展 4 分鐘：5 個動作，每個 48 秒；小腿伸展一個循環 16 秒（左右各一次，一側 16 秒）。
    private val stretchMs = 4 * 60_000L

    private fun stretchCue(elapsedMs: Long): CoachCue? =
        MoveCoach.cueAt(MoveSchedule.at(StageKind.COOLDOWN, stretchMs, elapsedMs)!!)

    @Test
    fun holdMoveCountsDownTheLastFiveSecondsOfEachSide() {
        // 小腿伸展第一側：0 到 16 秒。
        assertEquals(CoachCue(CoachCue.Kind.COUNT, 5), stretchCue(11_100))
        assertEquals(CoachCue(CoachCue.Kind.COUNT, 5), stretchCue(11_900))
        assertEquals(CoachCue(CoachCue.Kind.COUNT, 4), stretchCue(12_100))
        assertEquals(CoachCue(CoachCue.Kind.COUNT, 1), stretchCue(15_500))
    }

    @Test
    fun holdMoveSaysTenSecondsLeft() {
        assertEquals(CoachCue(CoachCue.Kind.LEFT_10), stretchCue(6_100))
        assertEquals(CoachCue(CoachCue.Kind.LEFT_10), stretchCue(6_900))
        assertNull(stretchCue(5_500))
    }

    @Test
    fun alternatingHoldSaysSwitchSidesFromTheSecondCycle() {
        assertNull(stretchCue(100)) // 第一側開頭不用喊
        assertEquals(CoachCue(CoachCue.Kind.SWITCH_SIDE), stretchCue(16_200))
        assertEquals(CoachCue(CoachCue.Kind.SWITCH_SIDE), stretchCue(17_000))
        assertNull(stretchCue(17_600)) // 超過視窗不補喊
        assertEquals(CoachCue(CoachCue.Kind.SWITCH_SIDE), stretchCue(32_200)) // 第三個循環再換回來
    }

    @Test
    fun nonAlternatingHoldNeverSaysSwitch() {
        // 前彎伸展 16 秒不分左右：第二個動作的第一個循環之後也不喊換邊。
        val slot = MoveSchedule.at(StageKind.COOLDOWN, stretchMs, 2 * 48_000L + 17_000)!!
        assertEquals(Move.FORWARD, slot.move)
        assertTrue(MoveCoach.cueAt(slot)?.kind != CoachCue.Kind.SWITCH_SIDE)
    }

    @Test
    fun repetitionMoveCountsOverTheWholeMove() {
        // 肌力預設 6 分鐘：5 個動作，每個 72 秒。深蹲 0 到 72 秒。
        val ms = 6 * 60_000L
        fun cue(elapsed: Long) = MoveCoach.cueAt(MoveSchedule.at(StageKind.STRENGTH, ms, elapsed)!!)
        assertNull(cue(1_000))
        assertEquals(CoachCue(CoachCue.Kind.LEFT_30), cue(42_500)) // 剩 29.5 秒 → 30
        assertEquals(CoachCue(CoachCue.Kind.LEFT_10), cue(62_500))
        assertEquals(CoachCue(CoachCue.Kind.COUNT, 5), cue(67_500))
        assertEquals(CoachCue(CoachCue.Kind.COUNT, 1), cue(71_500))
    }

    @Test
    fun repetitionLungeDoesNotShoutSwitchEveryCycle() {
        val ms = 6 * 60_000L
        for (t in 72_000L until 144_000L step 100L) {
            val cue = MoveCoach.cueAt(MoveSchedule.at(StageKind.STRENGTH, ms, t)!!)
            assertFalse("$t 毫秒", cue?.kind == CoachCue.Kind.SWITCH_SIDE)
        }
    }

    @Test
    fun shortMoveSkipsThirtySecondsLeftRightAfterItsName() {
        // 暖身 2 分鐘：4 個動作，每個 30 秒；剛開始（剩 30 秒）不喊，因為正在念動作名稱。
        val ms = 2 * 60_000L
        val slot = MoveSchedule.at(StageKind.WARMUP, ms, 100)!!
        assertNull(MoveCoach.cueAt(slot))
    }

    @Test
    fun cueStaysTheSameThroughoutOneSecond() {
        val seen = (11_001L..11_999L step 50L).map { stretchCue(it) }.toSet()
        assertEquals(setOf(CoachCue(CoachCue.Kind.COUNT, 5)), seen)
    }

    @Test
    fun keysDifferBetweenCues() {
        val keys = listOf(
            CoachCue(CoachCue.Kind.SWITCH_SIDE),
            CoachCue(CoachCue.Kind.LEFT_30),
            CoachCue(CoachCue.Kind.LEFT_10),
        ) + (1..5).map { CoachCue(CoachCue.Kind.COUNT, it) }
        assertEquals(keys.size, keys.map { it.key }.toSet().size)
    }
}
