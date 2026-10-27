package com.cornming.slowjog.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompletionChimeTest {

    private val samples = CompletionChime.samples()

    @Test
    fun lengthIsAboutOnePointFiveSeconds() {
        val seconds = samples.size.toDouble() / CompletionChime.SAMPLE_RATE
        assertTrue("長度 $seconds 秒", seconds in 1.3..2.0)
    }

    @Test
    fun hasSoundWithoutClipping() {
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue("峰值 $peak", peak in 20_000..Short.MAX_VALUE.toInt())
    }

    @Test
    fun startsAndEndsQuietly() {
        assertEquals(0, samples.first().toInt())
        val tail = samples.takeLast(200).maxOf { abs(it.toInt()) }
        assertTrue("結尾 $tail", tail < 1500) // 淡出到接近靜音，不會爆音
    }
}
