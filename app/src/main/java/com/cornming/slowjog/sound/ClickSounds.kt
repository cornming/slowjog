package com.cornming.slowjog.sound

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** 節拍音色：每天聽同一種聲音容易膩，所以提供幾種。 */
enum class ClickSound { CLICK, CLAP, SNAP }

/** 節拍音色的合成（純函式，不碰 Android，可測試）。每種都是約 30 到 60 毫秒的短音。 */
object ClickSounds {

    const val SAMPLE_RATE = 44_100

    private val cache = HashMap<ClickSound, ShortArray>()

    @Synchronized
    fun samples(sound: ClickSound): ShortArray = cache.getOrPut(sound) { build(sound) }

    private fun build(sound: ClickSound): ShortArray = when (sound) {
        ClickSound.CLICK -> click()
        ClickSound.CLAP -> clap()
        ClickSound.SNAP -> snap()
    }

    private fun ms(value: Int) = SAMPLE_RATE * value / 1000

    /** 節拍器：1000 Hz 的短促「答」聲，音量快速衰減。 */
    private fun click(): ShortArray {
        val out = ShortArray(ms(30))
        for (i in out.indices) {
            val envelope = exp(-i / (out.size * 0.25))
            val wave = sin(2.0 * PI * 1000.0 * i / SAMPLE_RATE)
            out[i] = (wave * envelope * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return out
    }

    /** 拍手：三個很近的噪音爆發（手掌不會同時拍到），整體衰減較慢。 */
    private fun clap(): ShortArray {
        val random = Random(7)
        val mix = DoubleArray(ms(60))
        val bursts = listOf(0 to 1.0, ms(8) to 0.8, ms(16) to 0.9)
        for ((offset, gain) in bursts) {
            for (i in 0 until mix.size - offset) {
                mix[offset + i] += (random.nextDouble() * 2 - 1) * gain * exp(-i / (SAMPLE_RATE * 0.006))
            }
        }
        return normalize(removeDc(mix))
    }

    /** 彈指：短促的高頻「啪」，噪音加上約 2.8 kHz 的共鳴。 */
    private fun snap(): ShortArray {
        val random = Random(11)
        val mix = DoubleArray(ms(35))
        for (i in mix.indices) {
            val envelope = exp(-i / (SAMPLE_RATE * 0.004))
            val tone = sin(2.0 * PI * 2800.0 * i / SAMPLE_RATE)
            mix[i] = (tone * 0.7 + (random.nextDouble() * 2 - 1) * 0.5) * envelope
        }
        return normalize(mix)
    }

    /** 一階高通，去掉噪音的直流成分。 */
    private fun removeDc(input: DoubleArray): DoubleArray {
        val out = DoubleArray(input.size)
        var prevIn = 0.0
        var prevOut = 0.0
        for (i in input.indices) {
            prevOut = input[i] - prevIn + 0.95 * prevOut
            prevIn = input[i]
            out[i] = prevOut
        }
        return out
    }

    private fun normalize(mix: DoubleArray): ShortArray {
        val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(mix.size) { (mix[it] / peak * 0.8 * Short.MAX_VALUE).toInt().toShort() }
    }
}
