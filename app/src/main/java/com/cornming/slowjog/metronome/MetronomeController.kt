package com.cornming.slowjog.metronome

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cornming.slowjog.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 節拍器的單一入口：畫面透過它改 BPM、開始與停止，並觀察狀態。
 * 真正出聲的引擎放在前景服務裡，所以離開畫面或鎖定螢幕後仍會繼續。
 */
object MetronomeController {

    data class State(
        val bpm: Int = MetronomeEngine.DEFAULT_BPM,
        val volume: Float = 1f,
        val running: Boolean = false,
        /** 目前已響的拍數，每一拍加一；畫面用它觸發閃動。 */
        val beat: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal val engine = MetronomeEngine { beatIndex ->
        _state.update { it.copy(beat = beatIndex) }
    }

    fun setBpm(value: Int) {
        val clamped = value.coerceIn(MetronomeEngine.MIN_BPM, MetronomeEngine.MAX_BPM)
        engine.bpm = clamped
        _state.update { it.copy(bpm = clamped) }
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        engine.volume = clamped
        _state.update { it.copy(volume = clamped) }
    }

    fun start(context: Context) {
        engine.sound = AppSettings.settings.value.metronomeSound // 依設定的音色出聲
        val intent = Intent(context, MetronomeService::class.java).setAction(MetronomeService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MetronomeService::class.java))
    }

    internal fun onEngineStarted() {
        _state.update { it.copy(running = true, beat = 0L) }
    }

    internal fun onEngineStopped() {
        _state.update { it.copy(running = false) }
    }
}
