package com.cornming.slowjog.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSoundsTest {

    @Test
    fun everySoundIsShortAudibleAndNotClipping() {
        for (sound in ClickSound.values()) {
            val samples = ClickSounds.samples(sound)
            val ms = samples.size * 1000 / ClickSounds.SAMPLE_RATE
            assertTrue("$sound 長度 $ms 毫秒", ms in 20..80)
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("$sound 峰值 $peak", peak in 15_000..Short.MAX_VALUE.toInt())
        }
    }

    @Test
    fun soundsAreDifferentFromEachOther() {
        val click = ClickSounds.samples(ClickSound.CLICK).toList()
        val clap = ClickSounds.samples(ClickSound.CLAP).toList()
        val snap = ClickSounds.samples(ClickSound.SNAP).toList()
        assertFalse(click == clap)
        assertFalse(clap == snap)
        assertFalse(click == snap)
    }

    @Test
    fun sameSoundIsDeterministic() {
        assertEquals(ClickSounds.samples(ClickSound.CLAP).toList(), ClickSounds.samples(ClickSound.CLAP).toList())
    }

    @Test
    fun soundsDecayAtTheEnd() {
        for (sound in ClickSound.values()) {
            val tail = ClickSounds.samples(sound).takeLast(50).maxOf { abs(it.toInt()) }
            assertTrue("$sound 結尾 $tail", tail < 3_000)
        }
    }
}
