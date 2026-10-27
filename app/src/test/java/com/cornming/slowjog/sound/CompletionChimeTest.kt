package com.cornming.slowjog.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompletionChimeTest {

    private val samples = CompletionChime.samples()

    @Test
    fun lengthIsAboutFourAndAHalfSeconds() {
        val seconds = samples.size.toDouble() / CompletionChime.SAMPLE_RATE
        assertTrue("長度 $seconds 秒", seconds in 4.0..5.5)
    }

    @Test
    fun hasSoundWithoutClipping() {
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue("峰值 $peak", peak in 20_000..Short.MAX_VALUE.toInt())
    }

    @Test
    fun endsQuietly() {
        val tail = samples.takeLast(200).maxOf { abs(it.toInt()) }
        assertTrue("結尾 $tail", tail < 1_500) // 淡出到接近靜音，不會突然截斷
    }

    @Test
    fun isLoudThroughoutNotJustAtTheStart() {
        // 三發煙火加歡呼，音訊不應該有超過 1 秒的大段靜音。
        val window = CompletionChime.SAMPLE_RATE
        var start = 0
        while (start + window <= samples.size - CompletionChime.SAMPLE_RATE / 2) {
            val peak = samples.slice(start until start + window).maxOf { abs(it.toInt()) }
            assertTrue("從 $start 起的 1 秒峰值只有 $peak", peak > 3_000)
            start += window / 2
        }
    }

    @Test
    fun isDeterministic() {
        assertEquals(CompletionChime.samples().toList(), samples.toList())
    }
}
