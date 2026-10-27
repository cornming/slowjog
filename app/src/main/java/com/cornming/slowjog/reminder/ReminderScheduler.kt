package com.cornming.slowjog.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cornming.slowjog.settings.AppSettings
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 用 AlarmManager 排每日提醒。使用「允許在休眠時觸發」的非精準鬧鐘，不需要額外權限，
 * 但系統可能讓它晚幾分鐘才響。每次響完由 [ReminderReceiver] 排下一次。
 */
object ReminderScheduler {

    const val ACTION_MAIN = "com.cornming.slowjog.REMINDER_MAIN"
    const val ACTION_EXTRA = "com.cornming.slowjog.REMINDER_EXTRA"

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (action == ACTION_MAIN) 1 else 2,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun alarms(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 依目前設定重排：關閉時取消，開啟時排下一個提醒時間。可重複呼叫。 */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val settings = AppSettings.settings.value
        if (!settings.reminderEnabled) {
            cancel(app)
            return
        }
        val next = ReminderLogic.nextTrigger(ZonedDateTime.now(ZoneId.systemDefault()), settings.reminderHour, settings.reminderMinute)
        alarms(app).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(app, ACTION_MAIN))
    }

    fun scheduleExtra(context: Context, at: ZonedDateTime) {
        val app = context.applicationContext
        alarms(app).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(app, ACTION_EXTRA))
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        alarms(app).cancel(pending(app, ACTION_MAIN))
        alarms(app).cancel(pending(app, ACTION_EXTRA))
    }
}
