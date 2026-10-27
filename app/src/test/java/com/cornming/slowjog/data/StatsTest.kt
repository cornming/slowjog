package com.cornming.slowjog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private val today = LocalDate.of(2026, 10, 27)

    private fun record(date: LocalDate, durationSec: Int = 1800, jogSec: Int = 900) = WorkoutRecord(
        dateEpochDay = date.toEpochDay(),
        startedAt = 0,
        durationSec = durationSec,
        jogSec = jogSec,
        bpm = 180,
    )

    @Test
    fun emptyHasNoStreak() {
        val stats = Stats.compute(emptyList(), today)
        assertEquals(0, stats.count)
        assertEquals(0, stats.streakDays)
        assertFalse(stats.doneToday)
    }

    @Test
    fun sumsJogAndWholeWorkoutSeparately() {
        val stats = Stats.compute(
            listOf(record(today, 1800, 900), record(today.minusDays(1), 1500, 600)),
            today,
        )
        assertEquals(1500L, stats.totalJogSec)
        assertEquals(3300L, stats.totalDurationSec)
        assertEquals(2, stats.count)
    }

    @Test
    fun streakCountsConsecutiveDaysIncludingToday() {
        val stats = Stats.compute(
            listOf(record(today), record(today.minusDays(1)), record(today.minusDays(2))),
            today,
        )
        assertEquals(3, stats.streakDays)
        assertTrue(stats.doneToday)
    }

    @Test
    fun streakContinuesFromYesterdayIfTodayNotDoneYet() {
        val stats = Stats.compute(listOf(record(today.minusDays(1)), record(today.minusDays(2))), today)
        assertEquals(2, stats.streakDays)
        assertFalse(stats.doneToday)
    }

    @Test
    fun gapBreaksTheStreak() {
        val stats = Stats.compute(listOf(record(today), record(today.minusDays(2))), today)
        assertEquals(1, stats.streakDays)
    }

    @Test
    fun missingYesterdayAndTodayMeansZero() {
        val stats = Stats.compute(listOf(record(today.minusDays(2))), today)
        assertEquals(0, stats.streakDays)
    }

    @Test
    fun severalRecordsOnOneDayCountAsOneDay() {
        val stats = Stats.compute(listOf(record(today), record(today)), today)
        assertEquals(1, stats.streakDays)
        assertEquals(2, stats.count)
    }
}
