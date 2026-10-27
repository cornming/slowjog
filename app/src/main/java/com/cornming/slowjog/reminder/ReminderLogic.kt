package com.cornming.slowjog.reminder

import java.time.ZonedDateTime

/** 每日提醒的規則（純邏輯，不碰 Android，方便測試）。 */
object ReminderLogic {

    /** 連續漏掉幾天（含）以上，當天就補提醒一次。 */
    const val MISSED_DAYS_FOR_EXTRA = 2

    /** 補提醒比原本的提醒時間晚幾個小時。 */
    const val EXTRA_DELAY_HOURS = 3L

    /** 嚴格晚於 [now] 的下一個 [hour]:[minute]。 */
    fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * 到昨天為止漏了幾天：從最近一次運動的隔天算到昨天。
     * 今天已運動，或完全沒有紀錄（剛開始用）都算 0。
     */
    fun missedDays(workoutDays: Set<Long>, today: Long): Int {
        if (today in workoutDays) return 0
        val last = workoutDays.filter { it < today }.maxOrNull() ?: return 0
        return (today - last - 1).toInt()
    }

    fun needsExtra(missedDays: Int): Boolean = missedDays >= MISSED_DAYS_FOR_EXTRA

    /** 補提醒的時間：當天提醒時間再過 [EXTRA_DELAY_HOURS] 小時；不跨到隔天。 */
    fun extraTrigger(mainToday: ZonedDateTime): ZonedDateTime? {
        val extra = mainToday.plusHours(EXTRA_DELAY_HOURS)
        return if (extra.toLocalDate() == mainToday.toLocalDate()) extra else null
    }
}
