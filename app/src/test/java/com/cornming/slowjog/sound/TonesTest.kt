package com.cornming.slowjog.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TonesTest {

    @Test
    fun moreBeepsAreLonger() {
        assertTrue(Tones.beeps(2).size > Tones.beeps(1).size)
        assertTrue(Tones.beeps(4).size > Tones.beeps(3).size)
    }

    @Test
    fun beepLengthMatchesCount() {
        val one = Tones.beeps(1).size
        val gap = Tones.SAMPLE_RATE * 110 / 1000
        assertEquals(one * 2 + gap, Tones.beeps(2).size)
    }

    @Test
    fun beepsStartAndEndQuietlyAndHaveSound() {
        val samples = Tones.beeps(3)
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.maxOf { abs(it.toInt()) } > 15_000)
    }

    @Test
    fun countIsClamped() {
        assertEquals(Tones.beeps(1).size, Tones.beeps(0).size)
        assertEquals(Tones.beeps(8).size, Tones.beeps(99).size)
    }
}
