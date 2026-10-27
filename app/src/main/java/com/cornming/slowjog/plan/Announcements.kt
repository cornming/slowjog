package com.cornming.slowjog.plan

import com.cornming.slowjog.mascot.Move

/** 語音播報與畫面上的文字。 */
object Announcements {

    /** 每隔幾分鐘念一次經過時間。 */
    const val ELAPSED_EVERY_MIN = 5

    fun stageName(kind: StageKind): String = when (kind) {
        StageKind.WARMUP -> "暖身"
        StageKind.JOG -> "超慢跑"
        StageKind.STRENGTH -> "肌力訓練"
        StageKind.COOLDOWN -> "伸展"
    }

    fun stageStart(stage: Stage): String = "${stageName(stage.kind)}開始，${stage.minutes} 分鐘"

    /** 暖身與伸展換到下一個動作時念的話：動作名稱加一句要領。 */
    fun moveStart(move: Move): String = "${move.nameZh}。${move.tip}"

    const val PLAN_DONE = "課表完成"

    fun elapsed(minutes: Int): String = "已經 $minutes 分鐘"

    /** 這一分鐘該不該念經過時間：5 分鐘的整數倍，而且和上次念的不同。 */
    fun shouldAnnounceElapsed(elapsedMs: Long, lastAnnouncedMinute: Int): Boolean {
        val minute = (elapsedMs / 60_000L).toInt()
        return minute > 0 && minute % ELAPSED_EVERY_MIN == 0 && minute != lastAnnouncedMinute
    }
}
