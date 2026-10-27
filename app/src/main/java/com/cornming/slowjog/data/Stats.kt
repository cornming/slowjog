package com.cornming.slowjog.data

import java.time.LocalDate

/** 由運動紀錄算出的累計數字。 */
data class Stats(
    val count: Int,
    val totalJogSec: Long,
    val totalDurationSec: Long,
    /** 連續天數：從今天（今天還沒運動則從昨天）往前，每天都有紀錄的天數。 */
    val streakDays: Int,
    val doneToday: Boolean,
) {
    companion object {
        fun compute(records: List<WorkoutRecord>, today: LocalDate): Stats {
            val days = records.map { it.dateEpochDay }.toSet()
            val todayDay = today.toEpochDay()
            val doneToday = todayDay in days
            var day = if (doneToday) todayDay else todayDay - 1
            var streak = 0
            while (day in days) {
                streak++
                day--
            }
            return Stats(
                count = records.size,
                totalJogSec = records.sumOf { it.jogSec.toLong() },
                totalDurationSec = records.sumOf { it.durationSec.toLong() },
                streakDays = streak,
                doneToday = doneToday,
            )
        }
    }
}
