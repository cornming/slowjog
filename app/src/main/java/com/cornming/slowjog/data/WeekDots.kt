package com.cornming.slowjog.data

import com.cornming.slowjog.workout.Distance
import java.time.DayOfWeek
import java.time.LocalDate

/** 首頁「本週」的一格：星期幾、有沒有運動、是不是今天、還沒到。 */
data class DayDot(val label: String, val done: Boolean, val today: Boolean, val future: Boolean)

/** 首頁用的小計算。純 Kotlin，有單元測試。 */
object WeekDots {

    private val LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 本週（週一到週日）每一天的狀態。 */
    fun of(doneDays: Set<Long>, today: LocalDate): List<DayDot> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0 until 7).map { i ->
            val day = monday.plusDays(i.toLong())
            DayDot(
                label = LABELS[i],
                done = day.toEpochDay() in doneDays,
                today = day == today,
                future = day.isAfter(today),
            )
        }
    }

    /** 所有紀錄的估算距離（公尺）：每筆的超慢跑時間 × 當時節拍 × 現在的步幅。 */
    fun totalMeters(records: List<WorkoutRecord>, strideCm: Int): Double =
        Distance.meters(records.sumOf { Distance.steps(it.jogSec * 1000L, it.bpm) }, strideCm)
}
