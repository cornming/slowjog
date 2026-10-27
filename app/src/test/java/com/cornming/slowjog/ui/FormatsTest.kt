package com.cornming.slowjog.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun clockUnderAnHour() {
        assertEquals("0:00", formatClock(0))
        assertEquals("1:05", formatClock(65))
        assertEquals("30:00", formatClock(1800))
    }

    @Test
    fun clockOverAnHour() {
        assertEquals("1:01:01", formatClock(3661))
    }

    @Test
    fun negativeIsTreatedAsZero() {
        assertEquals("0:00", formatClock(-5))
    }

    @Test
    fun totalShowsHoursAndMinutes() {
        assertEquals("0 分", formatTotal(0))
        assertEquals("25 分", formatTotal(1500))
        assertEquals("3 小時 25 分", formatTotal(3 * 3600 + 25 * 60 + 30))
    }
}
