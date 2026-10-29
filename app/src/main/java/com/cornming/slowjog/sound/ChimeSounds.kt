package com.cornming.slowjog.sound

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 完成音效的合成與播放。煙火歡呼沿用 [CompletionChime]，其他幾種在這裡合成。
 * 全部由程式合成，不需要音檔；用媒體音量播放。
 */
object ChimeSounds {

    const val SAMPLE_RATE = 44_100

    private var lastPlayed: ChimeKind? = null

    /** 合成一種音效（單聲道 16 位元）。獨立成純函式，方便測試。 */
    fun samples(kind: ChimeKind): ShortArray = when (kind) {
        ChimeKind.FIREWORKS -> CompletionChime.samples()
        ChimeKind.FANFARE -> fanfare()
        ChimeKind.BELLS -> bells()
        ChimeKind.CHIPTUNE -> chiptune()
        ChimeKind.MUSIC_BOX -> musicBox()
        ChimeKind.APPLAUSE -> applause()
    }

    /** 播放指定的一種；在背景執行緒進行。回傳這段音效多長（毫秒）。 */
    fun play(kind: ChimeKind): Long {
        lastPlayed = kind
        val data = samples(kind)
        Tones.play(data, SAMPLE_RATE)
        return data.size * 1000L / SAMPLE_RATE
    }

    /** 依設定播放：固定一種，或隨機（避開上一次響的那種）。回傳這段音效多長（毫秒）。 */
    fun play(choice: ChimeChoice): Long = play(ChimeLogic.pick(choice, lastPlayed, Random().nextDouble()))

    // ---- 勝利號角 ----

    private fun fanfare(): ShortArray {
        val mix = DoubleArray(sec(3.3))
        // 短、短、短、長：G4 G4 G4 C5，然後 C 大和弦收尾。
        brass(mix, 0.00, 0.16, 392.00, 0.9)
        brass(mix, 0.20, 0.16, 392.00, 0.9)
        brass(mix, 0.40, 0.16, 392.00, 0.9)
        brass(mix, 0.60, 0.70, 523.25, 1.0)
        for (freq in doubleArrayOf(523.25, 659.25, 783.99)) brass(mix, 1.45, 1.55, freq, 0.8)
        brass(mix, 1.45, 1.55, 1046.50, 0.45)
        cymbal(mix, Random(7), 1.45, 1.1, 0.5)
        return finish(mix)
    }

    /** 銅管音色：很多泛音，開頭有短短的「噗」，音高帶一點顫音。 */
    private fun brass(mix: DoubleArray, startSec: Double, durSec: Double, freq: Double, gain: Double) {
        val start = sec(startSec)
        val length = sec(durSec)
        var phase = 0.0
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = min(1.0, t / 0.03)
            val release = min(1.0, (durSec - t) / 0.12)
            val env = attack * max(0.0, release)
            val vibrato = if (t > 0.25) 1 + 0.004 * sin(2 * PI * 5.5 * t) else 1.0
            phase += 2 * PI * freq * vibrato / SAMPLE_RATE
            // 剛吹響時比較亮，之後泛音收斂一點。
            val brightness = 1.0 + 1.2 * exp(-t / 0.08)
            var value = 0.0
            for (k in 1..10) value += sin(k * phase) / k.toDouble().pow(0.9) * exp(-(k - 1) / (3.0 * brightness))
            add(mix, start + i, value * 0.22 * gain * env)
        }
    }

    private fun cymbal(mix: DoubleArray, random: Random, startSec: Double, durSec: Double, gain: Double) {
        val start = sec(startSec)
        var previous = 0.0
        for (i in 0 until sec(durSec)) {
            val t = i.toDouble() / SAMPLE_RATE
            val x = random.nextDouble() * 2 - 1
            add(mix, start + i, (x - previous) * 0.25 * gain * exp(-t / 0.35)) // 一階高通：沙沙的銅鈸
            previous = x
        }
    }

    // ---- 清脆鐘聲 ----

    private fun bells(): ShortArray {
        val mix = DoubleArray(sec(4.4))
        bell(mix, 0.00, 659.25, 0.9) // E5
        bell(mix, 0.42, 783.99, 0.9) // G5
        bell(mix, 0.84, 1046.50, 1.0) // C6
        bell(mix, 1.50, 261.63, 1.3) // 最後一聲低沉的 C4
        bell(mix, 1.50, 523.25, 0.6)
        return finish(mix)
    }

    /** 鐘的泛音不是整數倍，聽起來才像金屬；高的泛音衰減得比較快。 */
    private fun bell(mix: DoubleArray, startSec: Double, freq: Double, gain: Double) {
        val start = sec(startSec)
        val partials = arrayOf(
            doubleArrayOf(1.0, 1.0, 1.9), // 倍數, 音量, 衰減時間（秒）
            doubleArrayOf(2.0, 0.55, 1.3),
            doubleArrayOf(2.76, 0.4, 0.9),
            doubleArrayOf(5.4, 0.22, 0.5),
            doubleArrayOf(8.93, 0.1, 0.3),
        )
        val length = sec(2.6)
        for (p in partials) {
            var phase = 0.0
            for (i in 0 until length) {
                val t = i.toDouble() / SAMPLE_RATE
                phase += 2 * PI * freq * p[0] / SAMPLE_RATE
                val strike = min(1.0, t / 0.002)
                add(mix, start + i, sin(phase) * p[1] * exp(-t / p[2]) * strike * 0.3 * gain)
            }
        }
    }

    // ---- 8-bit 過關 ----

    private fun chiptune(): ShortArray {
        val mix = DoubleArray(sec(2.6))
        val run = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51, 1567.98)
        run.forEachIndexed { index, freq -> square(mix, index * 0.085, 0.085, freq, 0.5, 0.5) }
        // 最後在 C 大和弦的三個音之間快速輪流（琶音），音量慢慢收掉。
        val chord = doubleArrayOf(1046.50, 1318.51, 1567.98)
        val chordStart = run.size * 0.085
        val steps = (1.5 / 0.035).toInt()
        for (s in 0 until steps) {
            val fade = 1 - s.toDouble() / steps
            square(mix, chordStart + s * 0.035, 0.035, chord[s % 3], 0.25, 0.5 * fade)
        }
        return finish(mix)
    }

    /** 方波：復古遊戲機的音色。 */
    private fun square(mix: DoubleArray, startSec: Double, durSec: Double, freq: Double, duty: Double, gain: Double) {
        val start = sec(startSec)
        val length = sec(durSec)
        var phase = 0.0
        for (i in 0 until length) {
            phase += freq / SAMPLE_RATE
            phase -= kotlin.math.floor(phase)
            val edge = min(1.0, min(i, length - 1 - i).toDouble() / (SAMPLE_RATE * 0.003))
            add(mix, start + i, (if (phase < duty) 1.0 else -1.0) * 0.2 * gain * edge)
        }
    }

    // ---- 音樂盒 ----

    private fun musicBox(): ShortArray {
        val mix = DoubleArray(sec(5.2))
        // C 大調五聲音階的小旋律：往上爬、再輕輕落下。
        val melody = doubleArrayOf(
            1046.50, 1318.51, 1567.98, 1760.00, 1567.98, 1318.51, 1046.50, 783.99,
            1046.50, 1318.51, 1567.98, 2093.00,
        )
        melody.forEachIndexed { index, freq -> pluck(mix, index * 0.27, freq, 0.9) }
        val end = melody.size * 0.27
        pluck(mix, end, 1046.50, 1.0)
        pluck(mix, end, 1567.98, 0.8)
        pluck(mix, end, 523.25, 0.7)
        return finish(mix)
    }

    /** 音樂盒的齒片：音高很純、衰減快的泛音。 */
    private fun pluck(mix: DoubleArray, startSec: Double, freq: Double, gain: Double) {
        val start = sec(startSec)
        val length = sec(1.9)
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            val strike = min(1.0, t / 0.002)
            val value = sin(2 * PI * freq * t) * exp(-t / 0.55) +
                0.35 * sin(2 * PI * freq * 2 * t) * exp(-t / 0.3) +
                0.12 * sin(2 * PI * freq * 5.4 * t) * exp(-t / 0.08)
            add(mix, start + i, value * strike * 0.3 * gain)
        }
    }

    // ---- 掌聲歡呼 ----

    private fun applause(): ShortArray {
        val random = Random(11)
        val total = 4.0
        val mix = DoubleArray(sec(total))
        // 掌聲：很多短促的噪音，密度先升後降。
        val claps = 900
        repeat(claps) {
            val t = random.nextDouble() * total
            val density = min(1.0, t / 0.5) * min(1.0, (total - t) / 1.2)
            if (random.nextDouble() > density) return@repeat
            val amp = 0.1 + random.nextDouble() * 0.3
            val length = sec(0.006 + random.nextDouble() * 0.012)
            var smooth = 0.0
            val tone = 0.25 + random.nextDouble() * 0.4
            for (i in 0 until length) {
                smooth += tone * ((random.nextDouble() * 2 - 1) - smooth)
                add(mix, sec(t) + i, smooth * amp * (1 - i.toDouble() / length))
            }
        }
        // 兩聲口哨：音高往上滑，帶一點顫音。
        whistle(mix, 0.7, 0.45, 1500.0, 2300.0)
        whistle(mix, 1.9, 0.6, 1700.0, 2600.0)
        return finish(mix)
    }

    private fun whistle(mix: DoubleArray, startSec: Double, durSec: Double, from: Double, to: Double) {
        val start = sec(startSec)
        var phase = 0.0
        for (i in 0 until sec(durSec)) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durSec
            val freq = (from + (to - from) * progress * progress) * (1 + 0.012 * sin(2 * PI * 6 * t))
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = min(1.0, t / 0.04) * min(1.0, (durSec - t) / 0.08)
            add(mix, start + i, sin(phase) * 0.3 * env)
        }
    }

    // ---- 共用 ----

    private fun sec(value: Double) = (value * SAMPLE_RATE).toInt()

    private fun add(mix: DoubleArray, index: Int, value: Double) {
        if (index in mix.indices) mix[index] += value
    }

    /** 最後 0.3 秒淡出，音量調到接近滿格。 */
    private fun finish(mix: DoubleArray): ShortArray {
        val fade = sec(0.3)
        for (i in 0 until min(fade, mix.size)) mix[mix.size - 1 - i] *= i.toDouble() / fade
        val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(mix.size) { (mix[it] / peak * 0.85 * Short.MAX_VALUE).toInt().toShort() }
    }
}
