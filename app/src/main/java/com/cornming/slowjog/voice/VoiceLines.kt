package com.cornming.slowjog.voice

import com.cornming.slowjog.mascot.CoachCue
import com.cornming.slowjog.mascot.MascotLogic
import com.cornming.slowjog.mascot.Move
import com.cornming.slowjog.plan.Announcements
import com.cornming.slowjog.plan.Stage
import com.cornming.slowjog.plan.StageKind
import com.cornming.slowjog.plan.StageTransition

/**
 * 一句要念的話。
 * [id] 是預錄語音檔的檔名（assets/voice/<id>.wav）；有檔案就播檔案（可以是 AI 產生的自然語音），
 * 沒有檔案就用 [fallback] 交給手機內建的語音合成念。
 * [text] 是預錄語音的稿子（給 tools/voice 產生語音檔用）。
 */
data class VoiceLine(val id: String, val text: String, val fallback: String = text)

/** 所有會念的話。純邏輯，有單元測試；語音稿 tools/voice/script.csv 由 [catalog] 對照。 */
object VoiceLines {

    private fun stageId(kind: StageKind) = when (kind) {
        StageKind.WARMUP -> "warmup"
        StageKind.JOG -> "jog"
        StageKind.STRENGTH -> "strength"
        StageKind.COOLDOWN -> "cooldown"
    }

    private fun stageScript(kind: StageKind) = when (kind) {
        StageKind.WARMUP -> "暖身開始，一起動一動吧！"
        StageKind.JOG -> "超慢跑開始，跟著節拍出發囉！"
        StageKind.STRENGTH -> "肌力訓練開始，動作做漂亮一點喔！"
        StageKind.COOLDOWN -> "伸展開始，慢慢放鬆，辛苦了～"
    }

    /** 階段開始。語音檔不念分鐘數（畫面上有），手機內建語音則會念。 */
    fun stage(stage: Stage) =
        VoiceLine("stage_${stageId(stage.kind)}", stageScript(stage.kind), Announcements.stageStart(stage))

    val PLAN_DONE = VoiceLine("plan_done", "課表全部完成，今天辛苦了，你超棒的！", Announcements.PLAN_DONE)

    /** 每 5 分鐘念一次經過時間，語音檔涵蓋 5 到 60 分鐘。 */
    val ELAPSED_MINUTES = (5..60 step 5).toList()

    fun elapsed(minutes: Int) =
        VoiceLine("elapsed_$minutes", "已經${chineseNumber(minutes)}分鐘囉，繼續保持！", Announcements.elapsed(minutes))

    /** 最後 5 秒讀秒。 */
    fun count(number: Int) =
        VoiceLine("count_$number", "${DIGITS[number.coerceIn(1, 5)]}！", StageTransition.spoken(number))

    fun coach(cue: CoachCue): VoiceLine = when (cue.kind) {
        CoachCue.Kind.SWITCH_SIDE -> VoiceLine("coach_switch", "換邊！")
        CoachCue.Kind.LEFT_30 -> VoiceLine("coach_30s", "還有三十秒，加油！")
        CoachCue.Kind.LEFT_10 -> VoiceLine("coach_10s", "還有十秒！")
        CoachCue.Kind.COUNT -> count(cue.number)
    }

    fun move(move: Move) = VoiceLine("move_${move.id}", Announcements.moveStart(move))

    /** 鼓勵的話：[index] 是 [MascotLogic.MESSAGES] 的索引。 */
    fun cheer(index: Int): VoiceLine {
        val i = index.coerceIn(0, MascotLogic.MESSAGES.size - 1)
        return VoiceLine("cheer_%02d".format(i + 1), MascotLogic.MESSAGES[i])
    }

    /** 全部語音稿，依產生語音檔的順序。 */
    fun catalog(): List<VoiceLine> = buildList {
        for (kind in StageKind.values()) add(stage(Stage(kind, 0)))
        add(PLAN_DONE)
        for (m in ELAPSED_MINUTES) add(elapsed(m))
        for (n in 5 downTo 1) add(count(n))
        add(coach(CoachCue(CoachCue.Kind.SWITCH_SIDE)))
        add(coach(CoachCue(CoachCue.Kind.LEFT_30)))
        add(coach(CoachCue(CoachCue.Kind.LEFT_10)))
        for (m in Move.values()) add(move(m))
        for (i in MascotLogic.MESSAGES.indices) add(cheer(i))
    }

    private val DIGITS = listOf("零", "一", "二", "三", "四", "五")
    private val NUMERALS = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    /** 0 到 99 的中文念法：5 → 五、10 → 十、15 → 十五、30 → 三十、45 → 四十五。 */
    fun chineseNumber(n: Int): String {
        val v = n.coerceIn(0, 99)
        return when {
            v < 10 -> NUMERALS[v]
            v < 20 -> "十" + if (v % 10 == 0) "" else NUMERALS[v % 10]
            else -> NUMERALS[v / 10] + "十" + if (v % 10 == 0) "" else NUMERALS[v % 10]
        }
    }
}
