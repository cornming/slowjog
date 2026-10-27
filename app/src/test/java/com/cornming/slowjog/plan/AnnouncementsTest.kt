package com.cornming.slowjog.plan

import com.cornming.slowjog.mascot.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementsTest {

    @Test
    fun stageStartText() {
        assertEquals("暖身開始，5 分鐘", Announcements.stageStart(Stage(StageKind.WARMUP, 5)))
        assertEquals("超慢跑開始，15 分鐘", Announcements.stageStart(Stage(StageKind.JOG, 15)))
    }

    @Test
    fun stretchStageIsCalledStretch() {
        assertEquals("伸展", Announcements.stageName(StageKind.COOLDOWN))
        assertEquals("伸展開始，4 分鐘", Announcements.stageStart(Stage(StageKind.COOLDOWN, 4)))
    }

    @Test
    fun moveStartSaysNameAndTip() {
        assertEquals("抬膝踏步。${Move.MARCH.tip}", Announcements.moveStart(Move.MARCH))
    }

    @Test
    fun elapsedEveryFiveMinutesOnlyOnce() {
        assertFalse(Announcements.shouldAnnounceElapsed(4 * 60_000L + 59_000, -1))
        assertTrue(Announcements.shouldAnnounceElapsed(5 * 60_000L, -1))
        assertTrue(Announcements.shouldAnnounceElapsed(5 * 60_000L + 400, 0))
        assertFalse(Announcements.shouldAnnounceElapsed(5 * 60_000L + 400, 5)) // 已念過
        assertFalse(Announcements.shouldAnnounceElapsed(7 * 60_000L, 5))
        assertTrue(Announcements.shouldAnnounceElapsed(10 * 60_000L, 5))
    }

    @Test
    fun noAnnouncementAtStart() {
        assertFalse(Announcements.shouldAnnounceElapsed(0, -1))
    }

    @Test
    fun elapsedText() {
        assertEquals("已經 10 分鐘", Announcements.elapsed(10))
    }
}
