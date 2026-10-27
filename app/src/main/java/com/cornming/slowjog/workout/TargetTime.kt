package com.cornming.slowjog.workout

/** 運動目標時間的判斷。targetMinutes 為 0 代表不設目標。 */
object TargetTime {

    fun isReached(elapsedMs: Long, targetMinutes: Int): Boolean =
        targetMinutes > 0 && elapsedMs >= targetMinutes * 60_000L

    /** 距離目標還剩幾秒；不設目標回傳 null，已超過回傳 0。 */
    fun remainingSec(elapsedMs: Long, targetMinutes: Int): Int? {
        if (targetMinutes <= 0) return null
        val remainingMs = targetMinutes * 60_000L - elapsedMs
        return if (remainingMs <= 0) 0 else ((remainingMs + 999) / 1000).toInt()
    }
}
