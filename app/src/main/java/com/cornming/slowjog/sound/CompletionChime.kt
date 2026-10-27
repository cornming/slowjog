package com.cornming.slowjog.sound

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 完成音效：歡呼加放煙火，約 4.6 秒。三發煙火（上升的哨音、爆炸的低頻悶響、劈啪的火花），
 * 底下是人群的歡呼（雜訊的起伏、拉長的「啊～」母音、零星的拍手聲）。
 * 全部由程式合成，不需要音檔，所以聽起來是「合成的歡呼」，不是真人錄音。
 * 用媒體音量播放，所以音量會跟著手機的媒體音量。
 */
object CompletionChime {

    const val SAMPLE_RATE = 44_100
    const val SECONDS = 4.6

    /** 合成整段音效（單聲道 16 位元）。獨立成純函式，方便測試。 */
    fun samples(): ShortArray {
        val total = (SECONDS * SAMPLE_RATE).toInt()
        val mix = DoubleArray(total)
        val random = Random(2026)

        addFirework(mix, random, startSec = 0.0, whistleSec = 0.7, from = 600.0, to = 2200.0, gain = 1.0)
        addFirework(mix, random, startSec = 1.4, whistleSec = 0.55, from = 700.0, to = 2600.0, gain = 0.9)
        addFirework(mix, random, startSec = 2.5, whistleSec = 0.5, from = 800.0, to = 3000.0, gain = 0.8)
        addCrowd(mix, random, startSec = 0.9, durationSec = 3.5)

        // 最後 0.25 秒淡出，避免尾巴突然截斷。
        val fade = (0.25 * SAMPLE_RATE).toInt()
        for (i in 0 until fade) mix[total - 1 - i] *= i.toDouble() / fade

        val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(total) { (mix[it] / peak * 0.85 * Short.MAX_VALUE).toInt().toShort() }
    }

    /** 播放一次；在背景執行緒進行，不會卡住呼叫的人。失敗時靜默放棄（提示音不是必要功能）。 */
    fun play() = Tones.play(samples(), SAMPLE_RATE)

    private fun sec(value: Double) = (value * SAMPLE_RATE).toInt()

    /** 一發煙火：哨音往上飆、爆炸、火花劈啪聲。 */
    private fun addFirework(
        mix: DoubleArray,
        random: Random,
        startSec: Double,
        whistleSec: Double,
        from: Double,
        to: Double,
        gain: Double,
    ) {
        val start = sec(startSec)
        val whistle = sec(whistleSec)

        var phase = 0.0
        for (i in 0 until whistle) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / whistleSec
            val freq = from + (to - from) * progress * progress // 越飛越快
            phase += 2 * PI * freq / SAMPLE_RATE
            val amp = 0.18 * gain * min(1.0, t / 0.05) * (1 - 0.3 * progress)
            add(mix, start + i, sin(phase) * amp)
        }

        val boom = start + whistle
        // 低頻的「碰」：頻率從 100 Hz 往下掉，加上低通的雜訊。
        var lowPhase = 0.0
        var smooth = 0.0
        for (i in 0 until sec(0.9)) {
            val t = i.toDouble() / SAMPLE_RATE
            lowPhase += 2 * PI * (45 + 55 * exp(-t / 0.12)) / SAMPLE_RATE
            smooth += 0.08 * ((random.nextDouble() * 2 - 1) - smooth)
            add(mix, boom + i, gain * (0.9 * sin(lowPhase) * exp(-t / 0.25) + 2.2 * smooth * exp(-t / 0.18)))
        }

        // 火花：數十顆很短的噪音顆粒，越後面越稀疏。
        repeat(90) {
            val t = (-ln(1 - random.nextDouble()) * 0.45).coerceAtMost(1.35)
            val at = boom + sec(0.05 + t)
            val amp = (0.1 + random.nextDouble() * 0.25) * gain * exp(-t / 0.8)
            var previous = 0.0
            for (i in 0 until sec(0.004)) {
                val x = random.nextDouble() * 2 - 1
                add(mix, at + i, (x - previous) * amp * (1 - i.toDouble() / sec(0.004))) // 一階高通，聽起來比較尖
                previous = x
            }
        }
    }

    /** 人群歡呼：雜訊起伏、「啊～」母音、拍手聲。 */
    private fun addCrowd(mix: DoubleArray, random: Random, startSec: Double, durationSec: Double) {
        val start = sec(startSec)
        val length = sec(durationSec)
        fun envelope(i: Int): Double {
            val t = i.toDouble() / SAMPLE_RATE
            return min(1.0, t / 0.4) * min(1.0, max(0.0, (durationSec - t) / 0.8))
        }

        // 1. 中頻雜訊（約 400 到 3000 Hz），音量隨「呼～呼～」起伏。
        val high = 1 - exp(-2 * PI * 3000 / SAMPLE_RATE)
        val low = 1 - exp(-2 * PI * 400 / SAMPLE_RATE)
        var lowpassHigh = 0.0
        var lowpassLow = 0.0
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            val x = random.nextDouble() * 2 - 1
            lowpassHigh += high * (x - lowpassHigh)
            lowpassLow += low * (x - lowpassLow)
            val swell = 0.7 + 0.3 * sin(2 * PI * 6.5 * t + 1.0) * (0.5 + 0.5 * sin(2 * PI * 0.9 * t))
            add(mix, start + i, (lowpassHigh - lowpassLow) * 1.6 * swell * envelope(i))
        }

        // 2. 「啊～」：三個音高的鋸齒波，用母音共振峰（約 700 與 1100 Hz）加權泛音，音高慢慢往上滑，帶一點顫音。
        for (base in doubleArrayOf(220.0, 262.0, 330.0)) {
            var phase = 0.0
            for (i in 0 until length) {
                val t = i.toDouble() / SAMPLE_RATE
                val freq = base * (1 + 0.25 * t / durationSec) * (1 + 0.015 * sin(2 * PI * 5.5 * t))
                phase += 2 * PI * freq / SAMPLE_RATE
                var voice = 0.0
                for (k in 1..12) {
                    val harmonic = k * freq
                    val weight = exp(-sq((harmonic - 700) / 300)) + 0.7 * exp(-sq((harmonic - 1150) / 350))
                    voice += sin(k * phase) * weight / k
                }
                add(mix, start + i, voice * 0.35 * envelope(i))
            }
        }

        // 3. 零星的拍手聲。
        repeat((durationSec * 55).toInt()) {
            val at = start + random.nextInt(length)
            val amp = (0.08 + random.nextDouble() * 0.2) * envelope(at - start)
            var smooth = 0.0
            for (i in 0 until sec(0.006)) {
                smooth += 0.35 * ((random.nextDouble() * 2 - 1) - smooth)
                add(mix, at + i, smooth * amp * (1 - i.toDouble() / sec(0.006)))
            }
        }
    }

    private fun sq(x: Double) = x * x

    private fun add(mix: DoubleArray, index: Int, value: Double) {
        if (index in mix.indices) mix[index] += value
    }
}
