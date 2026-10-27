package com.cornming.slowjog.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.cornming.slowjog.sound.ClickSound
import com.cornming.slowjog.sound.ClickSounds
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 節拍器引擎：用 AudioTrack 串流播放，每一拍是「一小段點擊聲 + 靜音」，
 * 整段長度剛好等於一拍，因此節拍由取樣點數決定，不會因為執行緒排程而飄移。
 *
 * - 不要求音訊焦點，所以可以和 Spotify 等其他 App 的音樂同時出聲。
 * - 每拍真正開始播放時才通知 [onBeat]（以播放進度為準），讓畫面閃動與聲音大致同步。
 */
class MetronomeEngine(
    private val onBeat: (beatIndex: Long) -> Unit,
) {
    @Volatile
    var bpm: Int = DEFAULT_BPM

    @Volatile
    var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            runCatching { track?.setVolume(field) }
        }

    /** 節拍音色；從下一拍開始生效。 */
    @Volatile
    var sound: ClickSound = ClickSound.CLICK

    @Volatile
    private var running = false

    @Volatile
    private var track: AudioTrack? = null

    private var writerThread: Thread? = null
    private var pollerThread: Thread? = null

    /** 每一拍在串流中的起始取樣點，播放進度越過它時就代表這一拍開始響了。 */
    private val beatStarts = ConcurrentLinkedQueue<Long>()

    @Synchronized
    fun start() {
        if (running) return
        running = true
        beatStarts.clear()

        val minBufferBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // 緩衝約 200 毫秒：夠穩定，又不會讓畫面閃動落後聲音太多。
        val bufferBytes = maxOf(minBufferBytes, SAMPLE_RATE / 5 * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.setVolume(volume)
        audioTrack.play()
        track = audioTrack

        writerThread = thread(name = "metronome-writer", isDaemon = true) { writeLoop(audioTrack) }
        pollerThread = thread(name = "metronome-poller", isDaemon = true) { pollLoop(audioTrack) }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        val audioTrack = track
        track = null

        runCatching { audioTrack?.stop() }
        writerThread?.interrupt()
        pollerThread?.interrupt()
        writerThread?.join(JOIN_TIMEOUT_MS)
        pollerThread?.join(JOIN_TIMEOUT_MS)
        runCatching { audioTrack?.release() }
        writerThread = null
        pollerThread = null
        beatStarts.clear()
    }

    private fun writeLoop(audioTrack: AudioTrack) {
        var writtenFrames = 0L
        while (running) {
            // 每拍重新讀取 bpm，所以調整速度會從下一拍開始生效。
            val framesPerBeat = SAMPLE_RATE * 60 / bpm.coerceIn(MIN_BPM, MAX_BPM)
            val beat = ShortArray(framesPerBeat)
            val click = ClickSounds.samples(sound)
            System.arraycopy(click, 0, beat, 0, minOf(click.size, framesPerBeat))

            beatStarts.add(writtenFrames)
            // 串流模式下這個呼叫會阻塞，直到緩衝區有空間，因此寫入速度自然被播放速度牽制。
            val result = audioTrack.write(beat, 0, framesPerBeat)
            if (result < 0) break
            writtenFrames += result
        }
    }

    private fun pollLoop(audioTrack: AudioTrack) {
        var beatIndex = 0L
        try {
            while (running) {
                val head = audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                var next = beatStarts.peek()
                while (next != null && head >= next) {
                    beatStarts.poll()
                    beatIndex++
                    onBeat(beatIndex)
                    next = beatStarts.peek()
                }
                Thread.sleep(POLL_INTERVAL_MS)
            }
        } catch (_: InterruptedException) {
            // stop() 會中斷這條執行緒，正常結束即可。
        } catch (_: IllegalStateException) {
            // AudioTrack 已被釋放。
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val MIN_BPM = 40
        const val MAX_BPM = 240

        /** 超慢跑建議的步頻，也就是每分鐘 180 步。 */
        const val DEFAULT_BPM = 180

        private const val POLL_INTERVAL_MS = 4L
        private const val JOIN_TIMEOUT_MS = 500L
    }
}
