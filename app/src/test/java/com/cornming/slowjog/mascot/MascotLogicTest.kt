package com.cornming.slowjog.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MascotLogicTest {

    @Test
    fun framesStartOnTheBeatAndAdvanceSixPerBeat() {
        // 180 BPM：一拍約 333 毫秒
        assertEquals(0, MascotLogic.frameIndex(beat = 0, sinceBeatMs = 0, bpm = 180))
        assertEquals(3, MascotLogic.frameIndex(beat = 0, sinceBeatMs = 170, bpm = 180))
        assertEquals(5, MascotLogic.frameIndex(beat = 0, sinceBeatMs = 330, bpm = 180))
        assertEquals(6, MascotLogic.frameIndex(beat = 1, sinceBeatMs = 0, bpm = 180))
        assertEquals(0, MascotLogic.frameIndex(beat = 2, sinceBeatMs = 0, bpm = 180))
    }

    @Test
    fun lateBeatKeepsTheLastFrameOfThatBeat() {
        assertEquals(5, MascotLogic.frameIndex(beat = 0, sinceBeatMs = 5_000, bpm = 180))
        assertEquals(11, MascotLogic.frameIndex(beat = 3, sinceBeatMs = 5_000, bpm = 180))
    }

    @Test
    fun bubbleDelayIsBetweenOneAndTwoMinutes() {
        assertEquals(60, MascotLogic.nextBubbleDelaySec(0.0))
        assertEquals(119, MascotLogic.nextBubbleDelaySec(0.9999))
    }

    @Test
    fun messageNeverRepeatsThePreviousOne() {
        for (prev in MascotLogic.MESSAGES.indices) {
            val (index, _) = MascotLogic.pickMessage(prev, prev.toDouble() / MascotLogic.MESSAGES.size, 180)
            assertNotEquals(prev, index)
        }
    }

    @Test
    fun messageFillsInBpm() {
        val last = MascotLogic.MESSAGES.indexOfFirst { it.contains("{bpm}") }
        val (_, text) = MascotLogic.pickMessage(-1, (last + 0.5) / MascotLogic.MESSAGES.size, 172)
        assertEquals("節拍 172，我們同步囉！", text)
    }
}
