package com.cornming.slowjog.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 接收提醒鬧鐘，以及開機、App 更新、時區或時間變更：
 * 提醒鬧鐘響了就先排好下一天，再判斷今天要不要通知；其餘情況只是重排。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        AppSettings.init(app)
        val action = intent.action ?: return
        if (action != ReminderScheduler.ACTION_MAIN && action != ReminderScheduler.ACTION_EXTRA) {
            ReminderScheduler.schedule(app)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(app, action)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAlarm(app: Context, action: String) {
        if (action == ReminderScheduler.ACTION_MAIN) ReminderScheduler.schedule(app) // 先排明天，今天出錯也不會斷掉
        val settings = AppSettings.settings.value
        if (!settings.reminderEnabled) return

        RecordRepository.init(app)
        val days = RecordRepository.observeAll().first().map { it.dateEpochDay }.toSet()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val today = LocalDate.now().toEpochDay()
        if (today in days) return // 今天已經運動，不打擾

        val missed = ReminderLogic.missedDays(days, today)
        ReminderNotifier.show(app, missed)

        if (action == ReminderScheduler.ACTION_MAIN && ReminderLogic.needsExtra(missed)) {
            val mainToday = now.withHour(settings.reminderHour).withMinute(settings.reminderMinute).withSecond(0).withNano(0)
            ReminderLogic.extraTrigger(mainToday)?.takeIf { it.isAfter(now) }?.let { ReminderScheduler.scheduleExtra(app, it) }
        }
    }
}
