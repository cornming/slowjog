package com.cornming.slowjog.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cornming.slowjog.settings.AppSettings

/** 開機、App 更新或時區變更後，鬧鐘會被系統清掉，這裡依設定重排每日提醒。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        AppSettings.init(app)
        ReminderScheduler.schedule(app)
    }
}
