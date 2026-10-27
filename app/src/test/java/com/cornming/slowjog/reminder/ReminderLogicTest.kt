package com.cornming.slowjog.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderLogicTest {

    private val zone = ZoneId.of("Asia/Taipei")
    private fun at(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 10, 27, h, m, s, 0, zone)

    @Test
    fun nextTriggerIsLaterToday() {
        assertEquals(at(8, 30), ReminderLogic.nextTrigger(at(7, 0), 8, 30))
    }

    @Test
    fun nextTriggerIsTomorrowWhenPassedOrExact() {
        assertEquals(at(8, 30).plusDays(1), ReminderLogic.nextTrigger(at(9, 0), 8, 30))
        assertEquals(at(8, 30).plusDays(1), ReminderLogic.nextTrigger(at(8, 30), 8, 30)) // 剛好響的那一刻，要排到明天
        assertEquals(at(8, 30).plusDays(1), ReminderLogic.nextTrigger(at(8, 30, 5), 8, 30))
    }

    @Test
    fun missedDaysCountsFromLastWorkout() {
        assertEquals(0, ReminderLogic.missedDays(setOf(100L), 100L)) // 今天做了
        assertEquals(0, ReminderLogic.missedDays(setOf(99L, 100L), 101L)) // 昨天做了
        assertEquals(1, ReminderLogic.missedDays(setOf(99L), 101L))
        assertEquals(3, ReminderLogic.missedDays(setOf(90L, 97L), 101L))
    }

    @Test
    fun noRecordsMeansNothingMissed() {
        assertEquals(0, ReminderLogic.missedDays(emptySet(), 100L))
    }

    @Test
    fun extraNeedsTwoMissedDays() {
        assertFalse(ReminderLogic.needsExtra(0))
        assertFalse(ReminderLogic.needsExtra(1))
        assertTrue(ReminderLogic.needsExtra(2))
        assertTrue(ReminderLogic.needsExtra(10))
    }

    @Test
    fun extraTriggerIsThreeHoursLaterSameDay() {
        assertEquals(at(11, 30), ReminderLogic.extraTrigger(at(8, 30)))
        assertNull(ReminderLogic.extraTrigger(at(22, 0))) // 會跨到隔天就不補
    }
}
