package com.cornming.slowjog.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChimeSoundsTest {

    private val all = ChimeKind.values().associateWith { ChimeSounds.samples(it) }

    @Test
    fun everyKindIsAFewSecondsLong() {
        for ((kind, s) in all) {
            val seconds = s.size.toDouble() / ChimeSounds.SAMPLE_RATE
            assertTrue("${kind.name} 長度 $seconds 秒", seconds in 2.0..5.5)
        }
    }

    @Test
    fun everyKindHasSoundWithoutClipping() {
        for ((kind, s) in all) {
            val peak = s.maxOf { abs(it.toInt()) }
            assertTrue("${kind.name} 峰值 $peak", peak in 20_000..Short.MAX_VALUE.toInt())
        }
    }

    @Test
    fun everyKindEndsQuietly() {
        for ((kind, s) in all) {
            val tail = s.takeLast(200).maxOf { abs(it.toInt()) }
            assertTrue("${kind.name} 結尾 $tail", tail < 1_500)
        }
    }

    @Test
    fun everyKindSoundsDifferentFromTheOthers() {
        val kinds = ChimeKind.values()
        for (a in kinds) for (b in kinds) {
            if (a.ordinal < b.ordinal) {
                assertNotEquals("${a.name} 和 ${b.name} 一樣", all.getValue(a).toList(), all.getValue(b).toList())
            }
        }
    }

    @Test
    fun synthesisIsDeterministic() {
        for (kind in ChimeKind.values()) {
            assertEquals(kind.name, ChimeSounds.samples(kind).toList(), all.getValue(kind).toList())
        }
    }

    @Test
    fun noLongSilenceInTheFirstSecondsOfEachKind() {
        // 開頭 1.5 秒內要有明顯的聲音，不會讓人以為沒響。
        val window = ChimeSounds.SAMPLE_RATE * 3 / 2
        for ((kind, s) in all) {
            val peak = s.take(window).maxOf { abs(it.toInt()) }
            assertTrue("${kind.name} 開頭峰值 $peak", peak > 3_000)
        }
    }

    // ---- 挑音效 ----

    @Test
    fun fixedChoiceAlwaysPlaysThatKind() {
        for (kind in ChimeKind.values()) {
            assertEquals(kind, ChimeLogic.pick(ChimeChoice.Fixed(kind), kind, 0.3))
            assertEquals(kind, ChimeLogic.pick(ChimeChoice.Fixed(kind), null, 0.9))
        }
    }

    @Test
    fun randomChoiceNeverRepeatsTheLastOne() {
        for (last in ChimeKind.values()) {
            for (step in 0..99) {
                val picked = ChimeLogic.pick(ChimeChoice.Random, last, step / 100.0)
                assertNotEquals(last, picked)
            }
        }
    }

    @Test
    fun randomChoiceReachesEveryKind() {
        val seen = (0..99).map { ChimeLogic.pick(ChimeChoice.Random, null, it / 100.0) }.toSet()
        assertEquals(ChimeKind.values().toSet(), seen)
    }

    @Test
    fun randomWorksAtTheEdgesOfTheRange() {
        assertTrue(ChimeLogic.pick(ChimeChoice.Random, null, 0.0) in ChimeKind.values())
        assertTrue(ChimeLogic.pick(ChimeChoice.Random, null, 0.999999) in ChimeKind.values())
    }

    // ---- 設定存取 ----

    @Test
    fun choiceIdsRoundTrip() {
        for (kind in ChimeKind.values()) {
            assertEquals(ChimeChoice.Fixed(kind), ChimeChoice.fromId(ChimeChoice.Fixed(kind).id))
        }
        assertEquals(ChimeChoice.Random, ChimeChoice.fromId(ChimeChoice.Random.id))
    }

    @Test
    fun unknownOrMissingIdMeansRandom() {
        assertEquals(ChimeChoice.Random, ChimeChoice.fromId(null))
        assertEquals(ChimeChoice.Random, ChimeChoice.fromId("nonsense"))
        assertNull(ChimeKind.fromId("nonsense"))
        assertFalse(ChimeKind.values().map { it.id }.contains(ChimeChoice.RANDOM_ID))
    }
}
