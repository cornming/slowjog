package com.cornming.slowjog.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 用手機內建的語音合成（TextToSpeech）念出階段與經過時間，說正體中文（台灣）。
 * 手機沒有中文語音或沒有語音引擎時，靜默放棄，不影響運動。
 */
object VoiceAnnouncer {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var initStarted = false
    private var counter = 0
    private val pending = ArrayDeque<String>()

    @Synchronized
    fun speak(context: Context, text: String) {
        if (failed) return
        if (ready) {
            say(text)
            return
        }
        pending.addLast(text)
        if (!initStarted) {
            initStarted = true
            val app = context.applicationContext
            Handler(Looper.getMainLooper()).post {
                tts = TextToSpeech(app) { status -> onInit(status) }
            }
        }
    }

    @Synchronized
    private fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            failed = true
            pending.clear()
            return
        }
        val result = engine.setLanguage(Locale.TAIWAN)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            failed = true
            pending.clear()
            return
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        ready = true
        while (pending.isNotEmpty()) say(pending.removeFirst())
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "slowjog-${counter++}")
    }
}
