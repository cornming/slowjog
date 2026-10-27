package com.cornming.slowjog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTest {

    @Test
    fun noWorkoutsMeansZero() {
        assertEquals(0.0, Habit.score(emptySet(), 100L, 5), 0.0)
        assertEquals(0.0, Habit.score(setOf(200L), 100L, 5), 0.0) // 紀錄在未來
    }

    @Test
    fun dailyForThirtyDaysIsAboutThreeQuarters() {
        val days = (0L until 30L).toSet()
        val score = Habit.score(days, 29L, 7)
        assertTrue("分數 $score", score in 0.74..0.78)
    }

    @Test
    fun oneMissedDayDropsSlightlyButNotToZero() {
        val full = (0L until 30L).toSet()
        val missed = full - 15L
        val fullScore = Habit.score(full, 29L, 7)
        val missedScore = Habit.score(missed, 29L, 7)
        assertTrue(missedScore < fullScore)
        assertTrue("漏一天分數 $missedScore", missedScore > 0.7)
    }

    @Test
    fun restDaysWithinWeeklyTargetAreNotPenalised() {
        val full = (0L until 30L).toSet()
        val missed = full - 15L // 每週目標 5 天，漏一天仍有 6 天
        assertEquals(Habit.score(full, 29L, 5), Habit.score(missed, 29L, 5), 1e-9)
    }

    @Test
    fun meetingALowerWeeklyTargetBuildsUp() {
        val days = (0L until 8L).flatMap { w -> listOf(w * 7, w * 7 + 2, w * 7 + 4) }.toSet()
        assertTrue(Habit.score(days, 55L, 3) > 0.8)
    }

    @Test
    fun stoppingDecaysTheScore() {
        val days = (0L until 30L).toSet()
        val atEnd = Habit.score(days, 29L, 7)
        val tenDaysLater = Habit.score(days, 39L, 7)
        assertTrue(tenDaysLater < atEnd)
        assertTrue(tenDaysLater > 0.5)
    }

    @Test
    fun scoreStaysBetweenZeroAndOne() {
        val days = (0L until 400L).toSet()
        val score = Habit.score(days, 399L, 1)
        assertTrue(score in 0.0..1.0)
    }

    @Test
    fun weekCountStartsOnMonday() {
        // 2026-10-27 是星期二；週一是 10-26
        val tuesday = java.time.LocalDate.of(2026, 10, 27).toEpochDay()
        val monday = tuesday - 1
        val lastSunday = tuesday - 2
        assertEquals(2, Habit.weekCount(setOf(monday, tuesday, lastSunday), tuesday))
        assertEquals(0, Habit.weekCount(setOf(lastSunday), tuesday))
    }
}
