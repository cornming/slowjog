package com.cornming.slowjog.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 短音效的合成與播放：階段提示的「嗶」聲、完成音效、音色試聽共用。 */
object Tones {

    const val SAMPLE_RATE = 44_100
    private const val BEEP_HZ = 880.0
    private const val BEEP_MS = 130
    private const val GAP_MS = 110

    /** [count] 聲短「嗶」，次數代表階段（暖身 1 聲、超慢跑 2 聲、肌力 3 聲、伸展 4 聲）。 */
    fun beeps(count: Int): ShortArray {
        val n = count.coerceIn(1, 8)
        val beepFrames = SAMPLE_RATE * BEEP_MS / 1000
        val gapFrames = SAMPLE_RATE * GAP_MS / 1000
        val out = ShortArray(n * beepFrames + (n - 1) * gapFrames)
        val fade = SAMPLE_RATE * 8 / 1000 // 8 毫秒淡入淡出，避免爆音
        for (k in 0 until n) {
            val start = k * (beepFrames + gapFrames)
            for (i in 0 until beepFrames) {
                val edge = min(1.0, min(i, beepFrames - 1 - i).toDouble() / fade)
                val wave = sin(2 * PI * BEEP_HZ * i / SAMPLE_RATE)
                out[start + i] = (wave * edge * 0.7 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    /** 在背景執行緒播放一段 16 位元單聲道音訊，用媒體音量；失敗時靜默放棄。 */
    fun play(data: ShortArray, sampleRate: Int = SAMPLE_RATE) {
        thread(name = "tone-player", isDaemon = true) {
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (e: Exception) {
                return@thread
            }
            try {
                track.write(data, 0, data.size)
                track.play()
                Thread.sleep(data.size * 1000L / sampleRate + 200)
            } catch (e: Exception) {
                // 放棄即可
            } finally {
                runCatching { track.release() }
            }
        }
    }
}
