package com.cornming.slowjog.mascot

import kotlin.math.ceil

/** 做動作時，教練口令的種類。 */
data class CoachCue(val kind: Kind, val number: Int = 0) {
    enum class Kind { SWITCH_SIDE, LEFT_30, LEFT_10, COUNT }

    /** 同一個循環裡，同一句口令的識別碼（用來避免重複念）。 */
    val key: Int get() = kind.ordinal * 10 + number
}

/**
 * 暖身、肌力與伸展做動作時的口令：換邊、還剩多久、最後 5 秒讀秒。純邏輯，有單元測試。
 *
 * 動作分兩種：
 * - 停留型（一個循環 8 秒以上，例如伸展）：以「這一側」為單位，剩 10 秒提醒一次、最後 5 秒讀秒；
 *   左右分邊的動作，從第二個循環起開頭喊「換邊」。
 * - 反覆型（一個循環幾秒，例如深蹲、抬膝）：以「這個動作」為單位，剩 30 秒、剩 10 秒提醒，最後 5 秒讀秒；
 *   左右輪流的反覆動作（弓箭步）每幾秒就換，不喊換邊，口令只會吵。
 */
object MoveCoach {

    /** 一個循環達到這麼久，就當作停留型動作。 */
    const val HOLD_CYCLE_SEC = 8f

    /** 最後幾秒讀秒。 */
    const val COUNT_FROM = 5

    /** 「換邊」只在新循環開始後這麼短的時間內喊，過了就不喊（避免延遲才補喊）。 */
    private const val SWITCH_WINDOW_MS = 1_500L

    /** 反覆型動作的「剩 30 秒」，動作至少要做了這麼久才喊，避免和剛念完的動作名稱撞在一起。 */
    private const val LEFT_30_MIN_ELAPSED_MS = 5_000L

    fun isHold(move: Move): Boolean = move.cycleSec >= HOLD_CYCLE_SEC

    /** 此刻該喊的口令；沒有則 null。同一秒內回傳同一句。 */
    fun cueAt(slot: MoveSlot): CoachCue? {
        val hold = isHold(slot.move)
        if (hold && slot.move.alternatesSides && slot.cycle > 0 && slot.cycleElapsedMs < SWITCH_WINDOW_MS) {
            return CoachCue(CoachCue.Kind.SWITCH_SIDE)
        }
        // 停留型看這一側剩多久；但最後一個循環可能被動作時間截短，所以不能超過整個動作剩下的時間，
        // 否則最後幾秒的讀秒會停在 3 就換動作了。
        val remainingMs = if (hold) minOf(slot.cycleRemainingMs, slot.slotRemainingMs) else slot.slotRemainingMs
        if (remainingMs <= 0) return null
        val seconds = ceil(remainingMs / 1000.0).toInt()
        return when {
            seconds in 1..COUNT_FROM -> CoachCue(CoachCue.Kind.COUNT, seconds)
            seconds == 10 -> CoachCue(CoachCue.Kind.LEFT_10)
            !hold && seconds == 30 && slot.slotElapsedMs >= LEFT_30_MIN_ELAPSED_MS -> CoachCue(CoachCue.Kind.LEFT_30)
            else -> null
        }
    }
}
