package com.cornming.slowjog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekDotsTest {

    private val wed = LocalDate.of(2026, 10, 28) // 星期三

    @Test
    fun weekStartsOnMondayAndMarksToday() {
        val dots = WeekDots.of(emptySet(), wed)
        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), dots.map { it.label })
        assertTrue(dots[2].today)
        assertFalse(dots[1].today)
        assertFalse(dots[2].future)
        assertTrue(dots[3].future)
        assertFalse(dots[0].future)
    }

    @Test
    fun doneDaysAreMarked() {
        val tue = LocalDate.of(2026, 10, 27).toEpochDay()
        val lastWeek = LocalDate.of(2026, 10, 20).toEpochDay()
        val dots = WeekDots.of(setOf(tue, lastWeek), wed)
        assertEquals(listOf(false, true, false, false, false, false, false), dots.map { it.done })
    }

    @Test
    fun sundayBelongsToTheSameWeekAsTheMondayBefore() {
        val sun = LocalDate.of(2026, 11, 1)
        val dots = WeekDots.of(emptySet(), sun)
        assertTrue(dots[6].today)
        assertTrue(dots.none { it.future })
    }

    @Test
    fun totalMetersUsesEachRecordsBpm() {
        val r1 = WorkoutRecord(dateEpochDay = 1, startedAt = 0, durationSec = 1800, jogSec = 900, bpm = 180)
        val r2 = WorkoutRecord(dateEpochDay = 2, startedAt = 0, durationSec = 600, jogSec = 60, bpm = 120)
        // 900 秒 × 3 步／秒 = 2700 步；60 秒 × 2 步／秒 = 120 步；共 2820 步 × 0.5 公尺
        assertEquals(1410.0, WeekDots.totalMeters(listOf(r1, r2), 50), 1e-6)
    }
}
