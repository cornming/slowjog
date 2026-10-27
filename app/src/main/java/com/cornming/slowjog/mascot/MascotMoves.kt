package com.cornming.slowjog.mascot

import com.cornming.slowjog.plan.Stage
import com.cornming.slowjog.plan.StageKind

/**
 * 暖身與伸展的動作。每個動作有一張影格表（mascot/moves.js 產生，12 格一個循環）。
 * [cycleSec] 是影格表一個循環走多久；[alternatesSides] 代表動作分左右，
 * 每做完一個循環就左右鏡像換邊。
 */
enum class Move(
    val id: String,
    val nameZh: String,
    val tip: String,
    val cycleSec: Float,
    val alternatesSides: Boolean,
) {
    MARCH("march", "抬膝踏步", "膝蓋輕輕抬高，手臂前後擺動。", 1.6f, false),
    ARM_CIRCLE("armcircle", "手臂繞圈", "雙手放鬆伸直，畫大圈活動肩膀。", 2.0f, false),
    TWIST("twist", "轉體", "雙手打開，腰帶著上身左右轉。", 2.4f, false),
    SQUAT("squat", "屈膝半蹲", "屁股往後坐，雙手往前伸，蹲到舒服的深度就好。", 4.0f, false),

    CALF("calf", "小腿伸展", "後腳跟貼地，雙手往前推，感覺小腿拉長，左右都做。", 16f, true),
    QUAD("quad", "大腿前側伸展", "站穩，手抓同側腳踝往臀部靠，左右都做。", 16f, true),
    FORWARD("forward", "前彎伸展", "上身慢慢往前放鬆下垂，膝蓋不要鎖死。", 16f, false),
    SIDEBEND("sidebend", "上舉側彎", "雙手上舉，身體慢慢往兩側彎。", 8f, false),
    CROSSARM("crossarm", "肩膀橫向伸展", "一手橫過胸前，另一手輕輕把手肘往身體壓，左右都做。", 16f, true),
}

/** 現在該做哪個動作、做到循環的哪裡。 */
data class MoveSlot(
    val move: Move,
    /** 這個階段的第幾個動作（從 0 起）與總共幾個。 */
    val index: Int,
    val count: Int,
    /** 目前循環的相位，0 以上、小於 1。 */
    val phase: Float,
    /** 這個循環是否要左右鏡像（動作分左右時，一左一右輪流）。 */
    val mirrored: Boolean,
    /** 下一個動作；最後一個動作為 null。 */
    val next: Move?,
)

/** 暖身階段與伸展階段的動作輪替。純邏輯，有單元測試。 */
object MoveSchedule {

    /** 每個動作至少做這麼久，階段太短時就少做幾個動作。 */
    const val MIN_SLOT_MS = 30_000L

    val WARMUP_MOVES = listOf(Move.MARCH, Move.ARM_CIRCLE, Move.TWIST, Move.SQUAT)
    val STRETCH_MOVES = listOf(Move.CALF, Move.QUAD, Move.FORWARD, Move.SIDEBEND, Move.CROSSARM)

    /** 暖身與收操（伸展）階段才有動作，其他階段回傳空。 */
    fun movesFor(kind: StageKind): List<Move> = when (kind) {
        StageKind.WARMUP -> WARMUP_MOVES
        StageKind.COOLDOWN -> STRETCH_MOVES
        else -> emptyList()
    }

    /** 這個階段會做幾個動作：每個至少 [MIN_SLOT_MS]，最多把清單做完。 */
    fun count(kind: StageKind, stageMs: Long): Int {
        val all = movesFor(kind)
        if (all.isEmpty()) return 0
        return (stageMs / MIN_SLOT_MS).toInt().coerceIn(1, all.size)
    }

    /** 階段已過 [elapsedMs] 時的動作；階段沒有動作或時間不合理時回傳 null。 */
    fun at(kind: StageKind, stageMs: Long, elapsedMs: Long): MoveSlot? {
        val n = count(kind, stageMs)
        if (n == 0 || stageMs <= 0 || elapsedMs < 0) return null
        val slotMs = stageMs / n
        val index = (elapsedMs / slotMs).toInt().coerceAtMost(n - 1)
        val within = elapsedMs - index * slotMs
        val move = movesFor(kind)[index]
        val cycleMs = (move.cycleSec * 1000).toLong().coerceAtLeast(1)
        val cycle = within / cycleMs
        val phase = ((within % cycleMs).toDouble() / cycleMs).toFloat()
        return MoveSlot(
            move = move,
            index = index,
            count = n,
            phase = phase,
            mirrored = move.alternatesSides && cycle % 2L == 1L,
            next = movesFor(kind).getOrNull(index + 1)?.takeIf { index + 1 < n },
        )
    }

    /** 方便用課表階段與目前階段內已過的時間查詢。 */
    fun at(stage: Stage, elapsedInStageMs: Long): MoveSlot? =
        at(stage.kind, stage.minutes * 60_000L, elapsedInStageMs)
}
