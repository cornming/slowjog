package com.cornming.slowjog.workout

import android.content.Context
import android.os.SystemClock
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.data.WorkoutRecord
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.music.MusicControl
import com.cornming.slowjog.plan.Announcements
import com.cornming.slowjog.plan.PlanEnd
import com.cornming.slowjog.plan.StageCue
import com.cornming.slowjog.plan.StageTransition
import com.cornming.slowjog.plan.WorkoutPlan
import com.cornming.slowjog.mascot.CoachCue
import com.cornming.slowjog.mascot.MascotLogic
import com.cornming.slowjog.mascot.MoveCoach
import com.cornming.slowjog.mascot.MoveSchedule
import com.cornming.slowjog.voice.VoiceAnnouncer
import com.cornming.slowjog.voice.VoiceLine
import com.cornming.slowjog.voice.VoiceLines
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.sound.ChimeChoice
import com.cornming.slowjog.sound.ChimeSounds
import com.cornming.slowjog.sound.Tones
import com.cornming.slowjog.widget.SlowJogWidget
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
import kotlin.random.Random

/** 超慢跑時冒出來的一句鼓勵。[seq] 每說一句加一，畫面靠它知道有新的一句。 */
data class Cheer(val seq: Int, val text: String)

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

    private val _cheer = MutableStateFlow<Cheer?>(null)

    /** 最新的一句鼓勵；畫面顯示成小人物的氣泡，語音（設定可關）念同一句。 */
    val cheer: StateFlow<Cheer?> = _cheer.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedAtWall = 0L
    private var target = Target(TargetType.TIME, 0, 0)
    private var strideCm = Distance.DEFAULT_STRIDE_CM
    private var steps = 0.0
    private var lastAccrue = 0L
    private var autoStop = true
    private var plan: WorkoutPlan? = null
    private var cues = true
    private var voice = true
    private var coachVoice = true
    private var cheerVoice = true
    private var chimeChoice: ChimeChoice = ChimeChoice.Random
    private var lastStageIndex = -1
    private var lastMoveKey = -1
    private var lastCoachKey = -1
    private var lastCheerIndex = -1
    private var nextCheerAtJogSec = MascotLogic.FIRST_CHEER_JOG_SEC
    private var cheerSeq = 0
    private var autoAdvance = true
    private var lastCountdown = -1
    private var lastElapsedAnnounced = -1
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
                        val now = SystemClock.elapsedRealtime()
                        accrue(now)
                        timer.endJog(now)
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

    /**
     * 把「上次結算到現在」的超慢跑時間換算成步數。任何會改變計時狀態的操作都要先呼叫，
     * 這樣每一段是用當時的節拍與狀態結算，BPM 中途調整也不會算錯。
     */
    private fun accrue(now: Long) {
        if (timer.running && timer.jogging) {
            steps += Distance.steps(now - lastAccrue, MetronomeController.state.value.bpm)
        }
        lastAccrue = now
    }

    @Synchronized
    fun start(context: Context, musicRequested: Boolean = false) {
        if (timer.started) return
        if (MetronomeController.state.value.running) MetronomeController.stop(context) // 結束試聽
        startedAtWall = System.currentTimeMillis()
        val now = SystemClock.elapsedRealtime()
        timer.start(now)
        val settings = AppSettings.settings.value
        target = settings.target()
        strideCm = settings.strideCm
        steps = 0.0
        lastAccrue = now
        autoStop = settings.autoStopAtTarget
        plan = if (settings.planEnabled) settings.plan().takeIf { it.stages.isNotEmpty() } else null
        cues = settings.cuesEnabled
        voice = settings.voiceEnabled
        coachVoice = settings.coachVoiceEnabled
        cheerVoice = settings.cheerVoiceEnabled
        chimeChoice = settings.chimeChoice
        lastStageIndex = -1
        lastMoveKey = -1
        lastCoachKey = -1
        lastCheerIndex = -1
        nextCheerAtJogSec = MascotLogic.FIRST_CHEER_JOG_SEC
        cheerSeq = 0
        _cheer.value = null
        lastCountdown = -1
        autoAdvance = settings.planAutoEnabled && plan != null
        lastElapsedAnnounced = -1
        targetReached = false
        targetPrompt = false
        this.musicRequested = musicRequested
        // 運動期間先開著前景服務（節拍器待命），之後自動開始超慢跑時才不會被系統擋下。
        MetronomeController.hold(context.applicationContext)
        publish()
        watchTarget(context.applicationContext)
    }

    /**
     * 每半秒檢查一次：階段有沒有切換、要不要念經過時間、是否達到目標。
     * 暫停期間時間與步數都不增加，所以不會誤觸發。
     */
    private fun watchTarget(app: Context) {
        targetJob?.cancel()
        targetJob = scope.launch {
            while (isActive) {
                synchronized(this@WorkoutController) {
                    if (!timer.started) return@synchronized
                    val now = SystemClock.elapsedRealtime()
                    accrue(now)
                    val elapsedMs = timer.elapsedMs(now)
                    if (timer.running) {
                        checkCountdown(app, elapsedMs)
                        checkStage(app, elapsedMs)
                        checkMove(app, elapsedMs)
                        checkCoach(app, elapsedMs)
                        checkCheer(app, now, elapsedMs)
                        checkElapsedVoice(app, elapsedMs)
                    }
                    // 有課表時，目標（時間或距離）要等課表全部做完才結算，否則會在肌力、伸展還沒做之前就結束。
                    if (target.enabled && !targetReached && PlanEnd.targetCheckApplies(plan, elapsedMs) &&
                        target.isReached(elapsedMs, Distance.meters(steps, strideCm))
                    ) {
                        onGoalMet(app)
                    }
                }
                delay(200)
            }
        }
    }

    /** 走到新的階段（包含剛開始的第一個階段、以及課表全部完成）就提示一次。 */
    private fun checkStage(app: Context, elapsedMs: Long) {
        val p = plan ?: return
        val position = p.position(elapsedMs)
        if (position.index == lastStageIndex) return
        lastStageIndex = position.index
        val stage = position.stage
        // 自動接續：進入超慢跑階段就開始，離開就停止。
        if (autoAdvance && timer.running) {
            when (StageTransition.jogAction(stage?.kind, timer.jogging)) {
                StageTransition.JogAction.START -> startJog(app)
                StageTransition.JogAction.END -> endJog(app)
                StageTransition.JogAction.NONE -> Unit
            }
        }
        if (stage != null) {
            StageCue.play(app, stage.kind, sound = cues, vibrate = cues)
            if (voice) VoiceAnnouncer.speak(app, VoiceLines.stage(stage))
        } else {
            // 課表全部做完才算結束：響完成音效、停節拍器與音樂、問要不要儲存。
            if (!targetReached) onGoalMet(app, doneVoice = if (voice) VoiceLines.PLAN_DONE else null)
        }
    }

    /** 階段結束前 5 秒，每秒倒數一次：語音念數字、短嗶一聲。 */
    private fun checkCountdown(app: Context, elapsedMs: Long) {
        if (!autoAdvance) return
        val p = plan ?: return
        val position = p.position(elapsedMs)
        val number = StageTransition.countdown(position.remainingMs, p.next(position.index) != null)
        if (number == null) {
            lastCountdown = -1
            return
        }
        if (number == lastCountdown) return
        lastCountdown = number
        if (cues) Tones.play(Tones.beeps(1))
        if (voice) VoiceAnnouncer.speak(app, VoiceLines.count(number), flush = true)
    }

    /**
     * 暖身與伸展階段換到下一個動作時，用語音念動作名稱與要領（小人物同時在畫面上示範）。
     * 已經按了「開始超慢跑」就不念，因為這時在跑，不是在做動作。
     */
    private fun checkMove(app: Context, elapsedMs: Long) {
        if (!voice) return
        val p = plan ?: return
        val position = p.position(elapsedMs)
        val stage = position.stage ?: return
        val slot = MoveSchedule.at(stage, stage.minutes * 60_000L - position.remainingMs) ?: return
        val key = position.index * 100 + slot.index
        if (key == lastMoveKey) return
        lastMoveKey = key
        if (timer.jogging) return
        VoiceAnnouncer.speak(app, VoiceLines.move(slot.move))
    }

    /**
     * 做動作時的口令：停留型動作（伸展）每一側剩 10 秒提醒、最後 5 秒讀秒，左右分邊的從第二側起喊「換邊」；
     * 反覆型動作（深蹲、抬膝）剩 30 秒、10 秒提醒，最後 5 秒讀秒。階段結束前已經有倒數時不重複喊。
     */
    private fun checkCoach(app: Context, elapsedMs: Long) {
        if (!voice || !coachVoice || timer.jogging) return
        val p = plan ?: return
        val position = p.position(elapsedMs)
        val stage = position.stage ?: return
        if (autoAdvance && StageTransition.countdown(position.remainingMs, p.next(position.index) != null) != null) return
        val slot = MoveSchedule.at(stage, stage.minutes * 60_000L - position.remainingMs) ?: return
        val cue = MoveCoach.cueAt(slot) ?: return
        // 停留型動作的口令以「循環」為單位（每一側各喊一次）；反覆型以「整個動作」為單位。
        // 反覆型的循環只有一兩秒，key 帶上循環編號的話，同一個讀秒會因為跨過循環邊界而重複喊。
        val cycle = if (MoveCoach.isHold(slot.move)) slot.cycle else 0
        val key = ((position.index * 100 + slot.index) * 1000 + cycle) * 100 + cue.key
        if (key == lastCoachKey) return
        lastCoachKey = key
        VoiceAnnouncer.speak(app, VoiceLines.coach(cue), flush = cue.kind == CoachCue.Kind.COUNT)
    }

    /** 超慢跑時每 1 到 2 分鐘說一句鼓勵：氣泡一定會出現，語音依設定。快到階段結束要倒數時先不說。 */
    private fun checkCheer(app: Context, now: Long, elapsedMs: Long) {
        if (!timer.jogging) return
        val jogSec = (timer.jogMs(now) / 1000).toInt()
        if (jogSec < nextCheerAtJogSec) return
        val remainingMs = plan?.position(elapsedMs)?.remainingMs
        if (autoAdvance && remainingMs != null && remainingMs in 1 until MascotLogic.NO_CHEER_BEFORE_STAGE_END_MS) return
        sayCheer(app)
        nextCheerAtJogSec = jogSec + MascotLogic.nextBubbleDelaySec(Random.nextDouble())
    }

    private fun sayCheer(app: Context) {
        val (index, text) = MascotLogic.pickMessage(lastCheerIndex, Random.nextDouble())
        lastCheerIndex = index
        _cheer.value = Cheer(++cheerSeq, text)
        if (voice && cheerVoice) VoiceAnnouncer.speak(app, VoiceLines.cheer(index))
    }

    /** 點小人物：馬上說一句鼓勵（氣泡加語音）。 */
    @Synchronized
    fun cheerNow(context: Context) {
        if (!timer.started) return
        sayCheer(context.applicationContext)
    }

    /** 每 5 分鐘念一次經過時間；剛好在階段交界附近就略過，因為階段播報已經說了。 */
    private fun checkElapsedVoice(app: Context, elapsedMs: Long) {
        if (!voice || !Announcements.shouldAnnounceElapsed(elapsedMs, lastElapsedAnnounced)) return
        lastElapsedAnnounced = (elapsedMs / 60_000L).toInt()
        if (plan?.isNearBoundary(elapsedMs, 1_500) == true) return
        VoiceAnnouncer.speak(app, VoiceLines.elapsed(lastElapsedAnnounced))
    }

    /** 這次運動算結束了（沒課表時達成目標，有課表時課表全部做完）：停節拍器與音樂、響完成音效、問要不要儲存。 */
    private fun onGoalMet(app: Context, doneVoice: VoiceLine? = null) {
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
        playChimeSoon(app, doneVoice)
        publish()
    }

    /**
     * 稍等一下再響：剛送出的「暫停」媒體鍵還需要一點時間生效，不然提示音會和歌混在一起。
     * 有 [then] 時，等完成音效響完再念，語音才不會被蓋過去。
     */
    private fun playChimeSoon(app: Context? = null, then: VoiceLine? = null) {
        scope.launch {
            delay(if (autoStop) 500 else 0)
            val chimeMs = ChimeSounds.play(chimeChoice)
            if (then != null && app != null) {
                delay(chimeMs + 300)
                VoiceAnnouncer.speak(app, then)
            }
        }
    }

    /** 目標時間到了之後選擇「繼續運動」：收起提示，不再重複觸發。 */
    @Synchronized
    fun dismissTargetPrompt() {
        targetPrompt = false
        publish()
    }

    /** 是否啟用「自動接續階段」（開始運動當下的設定，且有課表）。 */
    @Synchronized
    fun autoAdvance(): Boolean = autoAdvance

    /** 這次運動的課表（開始運動當下的設定）；沒有啟用課表時為 null。 */
    @Synchronized
    fun plan(): WorkoutPlan? = plan

    /** 這次運動的目標（開始運動當下的設定）。 */
    @Synchronized
    fun target(): Target = target

    /** 到達目標時是否已自動停止節拍器與音樂。 */
    @Synchronized
    fun stoppedAtTarget(): Boolean = targetReached && autoStop

    /** 這次運動目前的估算距離（公尺），只計超慢跑階段。 */
    @Synchronized
    fun estimatedMeters(now: Long): Double {
        if (timer.started) accrue(now)
        return Distance.meters(steps, strideCm)
    }

    @Synchronized
    fun targetReached(): Boolean = targetReached

    @Synchronized
    fun startJog(context: Context) {
        val now = SystemClock.elapsedRealtime()
        accrue(now)
        if (!timer.startJog(now)) return
        if (!MetronomeController.start(context)) {
            // 系統不讓現在啟動節拍器服務（例如 App 在背景）：還原成沒在跑，請使用者手動開始。
            timer.endJog(now)
        }
        publish()
    }

    @Synchronized
    fun endJog(context: Context) {
        if (!timer.jogging) return
        val now = SystemClock.elapsedRealtime()
        accrue(now)
        timer.endJog(now)
        MetronomeController.stop(context)
        publish()
    }

    @Synchronized
    fun pause(context: Context) {
        if (!timer.running) return
        val now = SystemClock.elapsedRealtime()
        accrue(now)
        timer.pause(now)
        if (timer.jogging) MetronomeController.stop(context) // 暫停時節拍器也停
        publish()
    }

    @Synchronized
    fun resume(context: Context) {
        if (!timer.started || timer.running) return
        val now = SystemClock.elapsedRealtime()
        accrue(now)
        timer.resume(now)
        lastAccrue = now
        if (timer.jogging) MetronomeController.start(context)
        publish()
    }

    /** 結束運動。save 為 true 時寫入一筆紀錄；整段時長不足 1 秒不寫入。 */
    @Synchronized
    fun finish(context: Context, save: Boolean) {
        if (!timer.started) return
        accrue(SystemClock.elapsedRealtime())
        targetJob?.cancel()
        targetJob = null
        if (MetronomeController.state.value.running) MetronomeController.stop(context)
        MetronomeController.release(context.applicationContext) // 關掉待命的前景服務
        if (musicRequested && autoStop && !targetReached) MusicControl.pause(context)
        // 到目標時已經響過了；手動儲存結束才補一次完成音效。
        if (save && !targetReached) playChimeSoon()
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
            val app = context.applicationContext
            scope.launch {
                RecordRepository.insert(record)
                SlowJogWidget.refresh(app) // 桌面小工具的累計數字跟著更新
            }
        }
    }

    @Synchronized
    fun elapsedMs(now: Long): Long = timer.elapsedMs(now)

    @Synchronized
    fun elapsedSec(now: Long): Int = (timer.elapsedMs(now) / 1000).toInt()

    @Synchronized
    fun jogSec(now: Long): Int = (timer.jogMs(now) / 1000).toInt()
}
