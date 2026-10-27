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

    /** 運動期間先把前景服務開著（節拍器待命）：之後自動開始超慢跑時，不必在背景才新啟動服務。 */
    @Volatile
    private var holding = false

    /** 運動開始時呼叫（App 在前景）。失敗（例如系統不允許）回傳 false，之後照舊在需要時才啟動。 */
    fun hold(context: Context): Boolean {
        holding = try {
            val intent = Intent(context, MetronomeService::class.java).setAction(MetronomeService.ACTION_HOLD)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            false
        }
        return holding
    }

    /** 運動結束：停節拍並關掉待命的服務。 */
    fun release(context: Context) {
        if (!holding) return
        holding = false
        engine.stop()
        onEngineStopped()
        context.stopService(Intent(context, MetronomeService::class.java))
    }

    /** 開始出聲；服務啟動被系統擋下時回傳 false。 */
    fun start(context: Context): Boolean {
        engine.sound = AppSettings.settings.value.metronomeSound // 依設定的音色出聲
        if (holding && MetronomeService.alive) {
            engine.start()
            onEngineStarted()
            return true
        }
        return try {
            val intent = Intent(context, MetronomeService::class.java).setAction(MetronomeService.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop(context: Context) {
        if (holding && MetronomeService.alive) {
            engine.stop()
            onEngineStopped()
        } else {
            context.stopService(Intent(context, MetronomeService::class.java))
        }
    }

    internal fun onServiceDestroyed() {
        holding = false
    }

    internal fun onEngineStarted() {
        _state.update { it.copy(running = true, beat = 0L) }
    }

    internal fun onEngineStopped() {
        _state.update { it.copy(running = false) }
    }
}
