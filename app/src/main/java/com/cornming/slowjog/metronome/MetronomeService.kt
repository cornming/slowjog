package com.cornming.slowjog.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cornming.slowjog.MainActivity
import com.cornming.slowjog.R

/**
 * 前景服務（媒體播放類型）：讓節拍器在鎖定螢幕或切到其他 App 後仍持續運作。
 * 通知上有「停止」按鈕。
 */
class MetronomeService : Service() {

    override fun onCreate() {
        super.onCreate()
        alive = true
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val hold = intent?.action == ACTION_HOLD
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(hold),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (hold) return START_NOT_STICKY // 只是待命，節拍等開始超慢跑才響
        MetronomeController.engine.start()
        MetronomeController.onEngineStarted()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        alive = false
        MetronomeController.onServiceDestroyed()
        MetronomeController.engine.stop()
        MetronomeController.onEngineStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(hold: Boolean = false): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bpm = MetronomeController.state.value.bpm

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (hold) R.string.notification_title_hold else R.string.notification_title))
            .setContentText(getString(if (hold) R.string.notification_text_hold else R.string.notification_text, bpm))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.cornming.slowjog.action.START"
        const val ACTION_STOP = "com.cornming.slowjog.action.STOP"
        const val ACTION_HOLD = "com.cornming.slowjog.action.HOLD"

        /** 服務目前是否開著（待命或出聲）。 */
        @Volatile
        var alive = false
            private set
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 1
    }
}
