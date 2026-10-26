package com.cornming.slowjog.metronome

import kotlin.math.roundToInt

/**
 * 點拍子定速：使用者跟著腳步連續點擊，換算成 BPM。
 *
 * 純 Kotlin 實作、不依賴 Android，方便寫單元測試。
 * - 兩次點擊間隔超過 [maxGapMs] 視為重新開始。
 * - 取最近 [window] 個間隔的平均，避免單次點偏而讓數字亂跳。
 */
class TapTempo(
    private val maxGapMs: Long = 2000L,
    private val window: Int = 6,
    private val minBpm: Int = MetronomeEngine.MIN_BPM,
    private val maxBpm: Int = MetronomeEngine.MAX_BPM,
) {
    private val taps = ArrayDeque<Long>()

    /** 記錄一次點擊；累積至少兩次點擊後回傳換算後的 BPM，否則回傳 null。 */
    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > maxGapMs) {
            taps.clear()
        }
        taps.addLast(nowMs)
        while (taps.size > window + 1) {
            taps.removeFirst()
        }
        if (taps.size < 2) return null

        val averageMs = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        if (averageMs <= 0.0) return null
        return (60_000.0 / averageMs).roundToInt().coerceIn(minBpm, maxBpm)
    }

    fun reset() {
        taps.clear()
    }
}
