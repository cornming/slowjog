package com.cornming.slowjog.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 完成提示音：四個上行的音（C5、E5、G5、C6），最後一個拖長，約 1.6 秒。
 * 聲音由程式合成，不需要音檔。用媒體音量播放，所以音量會跟著手機的媒體音量。
 */
object CompletionChime {

    const val SAMPLE_RATE = 44_100

    private val FREQUENCIES = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
    private const val NOTE_GAP_SEC = 0.22
    private const val LAST_NOTE_SEC = 0.9

    /** 合成整段提示音（單聲道 16 位元）。獨立成純函式，方便測試。 */
    fun samples(): ShortArray {
        val total = ((NOTE_GAP_SEC * (FREQUENCIES.size - 1) + LAST_NOTE_SEC) * SAMPLE_RATE).toInt()
        val mix = DoubleArray(total)
        FREQUENCIES.forEachIndexed { i, freq ->
            val start = (i * NOTE_GAP_SEC * SAMPLE_RATE).toInt()
            val isLast = i == FREQUENCIES.lastIndex
            val decay = if (isLast) 3.5 else 7.0
            val length = if (isLast) (LAST_NOTE_SEC * SAMPLE_RATE).toInt() else (0.6 * SAMPLE_RATE).toInt()
            for (n in 0 until min(length, total - start)) {
                val t = n.toDouble() / SAMPLE_RATE
                val attack = min(1.0, n / (0.005 * SAMPLE_RATE)) // 5 毫秒淡入，避免爆音
                val release = min(1.0, (length - n) / (0.03 * SAMPLE_RATE)) // 結尾 30 毫秒淡出
                val tone = sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t)
                mix[start + n] += tone * exp(-decay * t) * attack * release
            }
        }
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(total) { (mix[it] / peak * 0.8 * Short.MAX_VALUE).toInt().toShort() }
    }

    /** 播放一次；在背景執行緒進行，不會卡住呼叫的人。失敗時靜默放棄（提示音不是必要功能）。 */
    fun play() = Tones.play(samples(), SAMPLE_RATE)
}
