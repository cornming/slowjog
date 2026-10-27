package com.cornming.slowjog.workout

import android.content.Context
import android.os.SystemClock
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.data.WorkoutRecord
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.music.MusicControl
import com.cornming.slowjog.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 一次運動的單一入口：整段運動計時、超慢跑階段（節拍器）、暫停與結束寫入紀錄。
 * 目前只存在記憶體裡，App 被系統結束時，進行中的這次運動不會保留。
 */
object WorkoutController {

    data class State(
        val active: Boolean = false,
        val running: Boolean = false,
        val jogging: Boolean = false,
        /** 目標時間到了，等待使用者選擇儲存結束或繼續。 */
        val targetPrompt: Boolean = false,
    )

    private val timer = WorkoutTimer()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedAtWall = 0L
    private var targetMinutes = 0
    private var autoStop = true
    private var targetReached = false
    private var targetPrompt = false
    private var musicRequested = false
    private var targetJob: Job? = null

    init {
        // 使用者從通知上按「停止」時，節拍器停了，超慢跑階段也要跟著結束。
        scope.launch {
            MetronomeController.state.map { it.running }.distinctUntilChanged().collect { running ->
                synchronized(this@WorkoutController) {
                    if (!running && timer.jogging && timer.running) {
                        timer.endJog(SystemClock.elapsedRealtime())
                        publish()
                    }
                }
            }
        }
    }

    private fun publish() {
        _state.value = State(
            active = timer.started,
            running = timer.running,
            jogging = timer.jogging,
            targetPrompt = targetPrompt,
        )
    }

    @Synchronized
    fun start(context: Context, musicRequested: Boolean = false) {
        if (timer.started) return
        if (MetronomeController.state.value.running) MetronomeController.stop(context) // 結束試聽
        startedAtWall = System.currentTimeMillis()
        timer.start(SystemClock.elapsedRealtime())
        val settings = AppSettings.settings.value
        targetMinutes = settings.targetMinutes
        autoStop = settings.autoStopAtTarget
        targetReached = false
        targetPrompt = false
        this.musicRequested = musicRequested
        publish()
        watchTarget(context.applicationContext)
    }

    /** 每半秒檢查一次是否到了目標時間；暫停期間時間不走，所以不會誤觸發。 */
    private fun watchTarget(app: Context) {
        targetJob?.cancel()
        if (targetMinutes <= 0) return
        targetJob = scope.launch {
            while (isActive) {
                delay(500)
                synchronized(this@WorkoutController) {
                    if (timer.started && !targetReached &&
                        TargetTime.isReached(timer.elapsedMs(SystemClock.elapsedRealtime()), targetMinutes)
                    ) {
                        onTargetReached(app)
                    }
                }
            }
        }
    }

    private fun onTargetReached(app: Context) {
        targetReached = true
        targetPrompt = true
        if (autoStop) {
            if (timer.jogging) {
                timer.endJog(SystemClock.elapsedRealtime())
                MetronomeController.stop(app)
            }
            MusicControl.pause(app)
        }
        MusicControl.vibrateTimeUp(app)
        publish()
    }

    /** 目標時間到了之後選擇「繼續運動」：收起提示，不再重複觸發。 */
    @Synchronized
    fun dismissTargetPrompt() {
        targetPrompt = false
        publish()
    }

    /** 目標時間（分鐘）；0 為不設目標。 */
    @Synchronized
    fun targetMinutes(): Int = targetMinutes

    @Synchronized
    fun targetReached(): Boolean = targetReached

    @Synchronized
    fun startJog(context: Context) {
        if (!timer.startJog(SystemClock.elapsedRealtime())) return
        MetronomeController.start(context)
        publish()
    }

    @Synchronized
    fun endJog(context: Context) {
        if (!timer.jogging) return
        timer.endJog(SystemClock.elapsedRealtime())
        MetronomeController.stop(context)
        publish()
    }

    @Synchronized
    fun pause(context: Context) {
        if (!timer.running) return
        timer.pause(SystemClock.elapsedRealtime())
        if (timer.jogging) MetronomeController.stop(context) // 暫停時節拍器也停
        publish()
    }

    @Synchronized
    fun resume(context: Context) {
        if (!timer.started || timer.running) return
        timer.resume(SystemClock.elapsedRealtime())
        if (timer.jogging) MetronomeController.start(context)
        publish()
    }

    /** 結束運動。save 為 true 時寫入一筆紀錄；整段時長不足 1 秒不寫入。 */
    @Synchronized
    fun finish(context: Context, save: Boolean) {
        if (!timer.started) return
        targetJob?.cancel()
        targetJob = null
        if (MetronomeController.state.value.running) MetronomeController.stop(context)
        if (musicRequested && autoStop && !targetReached) MusicControl.pause(context)
        targetPrompt = false
        val summary = timer.finish(SystemClock.elapsedRealtime())
        val bpm = MetronomeController.state.value.bpm
        val startedAt = startedAtWall
        publish()
        if (save && summary.durationSec > 0) {
            val date = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val record = WorkoutRecord(
                dateEpochDay = date.toEpochDay(),
                startedAt = startedAt,
                durationSec = summary.durationSec,
                jogSec = summary.jogSec,
                bpm = bpm,
            )
            scope.launch { RecordRepository.insert(record) }
        }
    }

    @Synchronized
    fun elapsedSec(now: Long): Int = (timer.elapsedMs(now) / 1000).toInt()

    @Synchronized
    fun jogSec(now: Long): Int = (timer.jogMs(now) / 1000).toInt()
}
