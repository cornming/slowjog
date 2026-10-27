package com.cornming.slowjog.plan

/** 課表的階段種類；提示音的次數 ＝ 順序 + 1（暖身 1 聲、超慢跑 2 聲、肌力 3 聲、收操 4 聲）。 */
enum class StageKind {
    WARMUP, JOG, STRENGTH, COOLDOWN;

    val beepCount: Int get() = ordinal + 1
}

data class Stage(val kind: StageKind, val minutes: Int)

/** 目前走到課表的哪裡：[index] 等於階段數代表課表已全部完成。 */
data class PlanPosition(val index: Int, val stage: Stage?, val remainingMs: Long)

/**
 * 運動課表：由階段依序組成（預設暖身 5、超慢跑 15、肌力 6、收操 4 分鐘，共 30 分鐘）。
 * 時間以「整段運動已過時間」計算，暫停時不走。0 分鐘的階段會被略過。
 */
class WorkoutPlan(stages: List<Stage>) {

    val stages: List<Stage> = stages.filter { it.minutes > 0 }

    val totalMs: Long = this.stages.sumOf { it.minutes * 60_000L }

    fun position(elapsedMs: Long): PlanPosition {
        var start = 0L
        stages.forEachIndexed { index, stage ->
            val end = start + stage.minutes * 60_000L
            if (elapsedMs < end) return PlanPosition(index, stage, end - elapsedMs)
            start = end
        }
        return PlanPosition(stages.size, null, 0)
    }

    /** 階段的下一個階段；沒有則 null。 */
    fun next(index: Int): Stage? = stages.getOrNull(index + 1)

    /** 是否剛好在某個階段交界前後 [marginMs] 毫秒內（用來避免語音重複念）。 */
    fun isNearBoundary(elapsedMs: Long, marginMs: Long): Boolean {
        var boundary = 0L
        for (stage in stages) {
            boundary += stage.minutes * 60_000L
            if (kotlin.math.abs(elapsedMs - boundary) <= marginMs) return true
        }
        return false
    }

    companion object {
        const val DEFAULT_WARMUP = 5
        const val DEFAULT_JOG = 15
        const val DEFAULT_STRENGTH = 6
        const val DEFAULT_COOLDOWN = 4

        fun of(warmup: Int, jog: Int, strength: Int, cooldown: Int) = WorkoutPlan(
            listOf(
                Stage(StageKind.WARMUP, warmup),
                Stage(StageKind.JOG, jog),
                Stage(StageKind.STRENGTH, strength),
                Stage(StageKind.COOLDOWN, cooldown),
            ),
        )
    }
}
