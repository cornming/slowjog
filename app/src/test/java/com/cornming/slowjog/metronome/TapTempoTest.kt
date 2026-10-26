package com.cornming.slowjog.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {

    @Test
    fun singleTapReturnsNull() {
        assertNull(TapTempo().tap(0))
    }

    @Test
    fun evenTapsEveryHalfSecondGive120Bpm() {
        val tapTempo = TapTempo()
        var bpm: Int? = null
        for (i in 0..3) bpm = tapTempo.tap(i * 500L)
        assertEquals(120, bpm)
    }

    @Test
    fun tapsAbout333MsApartGive180Bpm() {
        val tapTempo = TapTempo()
        var bpm: Int? = null
        for (i in 0..5) bpm = tapTempo.tap(i * 333L)
        assertEquals(180, bpm)
    }

    @Test
    fun longGapStartsOver() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        assertEquals(120, tapTempo.tap(500))
        // 間隔超過兩秒，視為重新開始，所以這一下只是新的第一拍。
        assertNull(tapTempo.tap(5_000))
    }

    @Test
    fun veryFastTapsAreClampedToMaxBpm() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        assertEquals(MetronomeEngine.MAX_BPM, tapTempo.tap(10))
    }

    @Test
    fun verySlowTapsAreClampedToMinBpm() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        assertEquals(MetronomeEngine.MIN_BPM, tapTempo.tap(1_900))
    }

    @Test
    fun resetForgetsPreviousTaps() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(500)
        tapTempo.reset()
        assertNull(tapTempo.tap(1_000))
    }
}
