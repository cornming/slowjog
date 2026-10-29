package com.cornming.slowjog.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 播放預錄的語音檔（assets/voice/<id>.wav），例如用 AI 產生的自然語音。
 * 沒有那個檔案時 [play] 回傳 false，呼叫的人改用手機內建語音念。
 * 一句一句排隊播；[play] 的 flush 為 true 時打斷正在播的、馬上播這句（讀秒用）。
 */
object VoiceClips {

    private val main = Handler(Looper.getMainLooper())
    private var names: Set<String>? = null
    private val queue = ArrayDeque<String>()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** assets/voice 裡有哪些語音檔（不含副檔名）；只讀一次。 */
    @Synchronized
    fun available(context: Context): Set<String> {
        names?.let { return it }
        val found = runCatching {
            context.applicationContext.assets.list("voice").orEmpty()
                .filter { it.endsWith(".wav") }
                .map { it.removeSuffix(".wav") }
                .toSet()
        }.getOrDefault(emptySet())
        names = found
        return found
    }

    fun has(context: Context, id: String): Boolean = id in available(context)

    /** 有這個語音檔就排進播放並回傳 true；沒有回傳 false。 */
    fun play(context: Context, id: String, flush: Boolean = false): Boolean {
        val app = context.applicationContext
        if (!has(app, id)) return false
        main.post { enqueue(app, id, flush) }
        return true
    }

    private fun enqueue(app: Context, id: String, flush: Boolean) {
        if (flush) {
            queue.clear()
            stopCurrent()
        }
        queue.addLast(id)
        if (player == null) next(app)
    }

    private fun next(app: Context) {
        while (true) {
            val id = queue.removeFirstOrNull()
            if (id == null) {
                abandonFocus(app)
                return
            }
            requestFocus(app)
            val mp = MediaPlayer()
            try {
                app.assets.openFd("voice/$id.wav").use { fd ->
                    mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                mp.setAudioAttributes(attributes)
                mp.setOnCompletionListener {
                    runCatching { it.release() }
                    if (player === it) player = null
                    next(app)
                }
                mp.setOnErrorListener { m, _, _ ->
                    runCatching { m.release() }
                    if (player === m) player = null
                    next(app)
                    true
                }
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                runCatching { mp.release() } // 這句播不出來就跳過，接著播下一句
            }
        }
    }

    private fun stopCurrent() {
        val current = player ?: return
        player = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    private fun requestFocus(app: Context) {
        if (focus != null) return
        val manager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        manager.requestAudioFocus(request)
        focus = request
    }

    private fun abandonFocus(app: Context) {
        val request = focus ?: return
        focus = null
        (app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(request)
    }
}
