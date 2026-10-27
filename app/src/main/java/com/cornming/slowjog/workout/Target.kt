package com.cornming.slowjog.workout

/** 運動目標的種類：整段運動的時間，或超慢跑的估算距離。 */
enum class TargetType { TIME, DISTANCE }

/**
 * 一次運動的目標。時間以整段運動計；距離只計超慢跑階段、而且是估算值（見 [Distance]）。
 * 數值為 0 代表該種類不設目標。
 */
data class Target(
    val type: TargetType = TargetType.TIME,
    val minutes: Int = 30,
    val distanceM: Int = 0,
) {
    val enabled: Boolean
        get() = when (type) {
            TargetType.TIME -> minutes > 0
            TargetType.DISTANCE -> distanceM > 0
        }

    fun isReached(elapsedMs: Long, estimatedMeters: Double): Boolean = when (type) {
        TargetType.TIME -> TargetTime.isReached(elapsedMs, minutes)
        TargetType.DISTANCE -> distanceM > 0 && estimatedMeters >= distanceM
    }

    /** 距離目標還差幾公尺；非距離目標或沒設目標回傳 null，已達成回傳 0。 */
    fun remainingMeters(estimatedMeters: Double): Int? {
        if (type != TargetType.DISTANCE || distanceM <= 0) return null
        return Math.ceil((distanceM - estimatedMeters).coerceAtLeast(0.0)).toInt()
    }
}
