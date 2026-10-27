package com.cornming.slowjog.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotLogicTest {

    @Test
    fun viewChangesEveryFiveMinutesOfJogging() {
        assertEquals(0, MascotLogic.viewIndex(0))
        assertEquals(0, MascotLogic.viewIndex(299))
        assertEquals(1, MascotLogic.viewIndex(300))
        assertEquals(2, MascotLogic.viewIndex(600))
        assertEquals(3, MascotLogic.viewIndex(900))
        assertEquals(0, MascotLogic.viewIndex(1200))
    }

    @Test
    fun manualTapsAdvanceTheView() {
        assertEquals(2, MascotLogic.viewIndex(300, manualOffset = 1))
        assertEquals(1, MascotLogic.viewIndex(0, manualOffset = 5))
    }

    @Test
    fun pathFromFrontToSideStepsThroughIntermediateAngles() {
        assertEquals(listOf(15, 30, 45), MascotLogic.anglePath(0, 45))
        assertEquals(listOf(60, 75, 90), MascotLogic.anglePath(45, 90))
    }

    @Test
    fun pathFromRightSideToOtherSideTurnsThroughFront() {
        assertEquals(
            listOf(75, 60, 45, 30, 15, 0, -15, -30, -45),
            MascotLogic.anglePath(90, -45),
        )
        assertEquals(listOf(-30, -15, 0), MascotLogic.anglePath(-45, 0))
    }

    @Test
    fun samePositionHasNoPath() {
        assertTrue(MascotLogic.anglePath(45, 45).isEmpty())
    }

    @Test
    fun negativeAnglesUseMirroredSheets() {
        assertEquals(30, MascotLogic.sheetAngle(-30))
        assertTrue(MascotLogic.isMirrored(-30))
        assertFalse(MascotLogic.isMirrored(30))
    }

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
