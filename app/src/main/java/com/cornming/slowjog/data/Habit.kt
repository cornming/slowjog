package com.cornming.slowjog.data

import java.time.LocalDate
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 習慣分數與每週目標（做法借鏡 uhabits 的「指數平滑」）。
 *
 * 每一天算一個 0 到 1 的完成度：最近 7 天（含當天）運動的天數 ÷ 每週目標天數，上限 1。
 * 分數＝昨天分數 × 衰減係數 ＋ 今天完成度 × (1 − 衰減係數)。
 * 所以達到每週目標的休息日不扣分，偶爾漏一天也只會小幅下降、不會歸零；但分數需要幾週才會爬高。
 */
object Habit {

    const val MIN_WEEKLY_TARGET = 1
    const val MAX_WEEKLY_TARGET = 7
    const val DEFAULT_WEEKLY_TARGET = 5

    private fun target(weeklyTarget: Int) = weeklyTarget.coerceIn(MIN_WEEKLY_TARGET, MAX_WEEKLY_TARGET)

    /** 每過一天，舊分數保留的比例；目標天數越少，衰減越慢。 */
    fun decay(weeklyTarget: Int): Double = 0.5.pow(sqrt(target(weeklyTarget) / 7.0) / 13.0)

    /** [workoutDays] 是有運動的日期（epoch day），[today] 是今天。回傳 0.0 到 1.0。 */
    fun score(workoutDays: Set<Long>, today: Long, weeklyTarget: Int): Double {
        val first = workoutDays.minOrNull() ?: return 0.0
        if (first > today) return 0.0
        val goal = target(weeklyTarget)
        val keep = decay(goal)
        var score = 0.0
        for (day in first..today) {
            val count = (day - 6..day).count { it in workoutDays }
            val completion = min(1.0, count.toDouble() / goal)
            score = score * keep + completion * (1 - keep)
        }
        return score
    }

    /** 本週（週一到今天）運動了幾天。 */
    fun weekCount(workoutDays: Set<Long>, today: Long): Int {
        val monday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        return (monday..today).count { it in workoutDays }
    }
}
