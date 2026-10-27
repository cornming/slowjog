package com.cornming.slowjog.settings

import android.content.Context
import android.content.SharedPreferences
import com.cornming.slowjog.workout.Distance
import com.cornming.slowjog.workout.Target
import com.cornming.slowjog.workout.TargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 開始運動時的音樂行為。 */
enum class MusicMode { ASK, ALWAYS, NEVER }

data class Settings(
    /** `spotify:playlist:<id>`；未設定為 null。 */
    val playlistUri: String? = null,
    val musicMode: MusicMode = MusicMode.ASK,
    /** 目標的種類：整段運動時間，或超慢跑的估算距離。 */
    val targetType: TargetType = TargetType.TIME,
    /** 整段運動的目標時間（分鐘），0 代表不設目標。 */
    val targetMinutes: Int = 30,
    /** 超慢跑的目標距離（公尺，估算值），0 代表不設目標。 */
    val targetDistanceM: Int = 0,
    /** 估算距離用的步幅（公分）。 */
    val strideCm: Int = Distance.DEFAULT_STRIDE_CM,
    /** 到目標時間時自動停止音樂與節拍器。 */
    val autoStopAtTarget: Boolean = true,
    /** 每日運動提醒；預設早上 8:30（起床後的運動時間），可在設定調整。 */
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 30,
) {
    fun target(): Target = Target(targetType, targetMinutes, targetDistanceM)
}

/** 設定都存在本機的 SharedPreferences。 */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_PLAYLIST = "playlistUri"
    private const val KEY_MODE = "musicMode"
    private const val KEY_TARGET_TYPE = "targetType"
    private const val KEY_TARGET = "targetMinutes"
    private const val KEY_TARGET_DISTANCE = "targetDistanceM"
    private const val KEY_STRIDE = "strideCm"
    private const val KEY_AUTO_STOP = "autoStopAtTarget"
    private const val KEY_REMINDER_ON = "reminderEnabled"
    private const val KEY_REMINDER_HOUR = "reminderHour"
    private const val KEY_REMINDER_MINUTE = "reminderMinute"
    private const val KEY_NOTIFICATION_ASKED = "notificationAsked"

    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = Settings(
            playlistUri = p.getString(KEY_PLAYLIST, null),
            musicMode = runCatching { MusicMode.valueOf(p.getString(KEY_MODE, null).orEmpty()) }
                .getOrDefault(MusicMode.ASK),
            targetType = runCatching { TargetType.valueOf(p.getString(KEY_TARGET_TYPE, null).orEmpty()) }
                .getOrDefault(TargetType.TIME),
            targetMinutes = p.getInt(KEY_TARGET, 30),
            targetDistanceM = p.getInt(KEY_TARGET_DISTANCE, 0),
            strideCm = p.getInt(KEY_STRIDE, Distance.DEFAULT_STRIDE_CM)
                .coerceIn(Distance.MIN_STRIDE_CM, Distance.MAX_STRIDE_CM),
            autoStopAtTarget = p.getBoolean(KEY_AUTO_STOP, true),
            reminderEnabled = p.getBoolean(KEY_REMINDER_ON, true),
            reminderHour = p.getInt(KEY_REMINDER_HOUR, 8).coerceIn(0, 23),
            reminderMinute = p.getInt(KEY_REMINDER_MINUTE, 30).coerceIn(0, 59),
        )
    }

    fun setReminderEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_REMINDER_ON, value)?.apply()
        _settings.value = _settings.value.copy(reminderEnabled = value)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        prefs?.edit()?.putInt(KEY_REMINDER_HOUR, h)?.putInt(KEY_REMINDER_MINUTE, m)?.apply()
        _settings.value = _settings.value.copy(reminderHour = h, reminderMinute = m)
    }

    /** 第一次啟動時只主動詢問一次通知權限，之後不再自動跳出。 */
    fun notificationAsked(): Boolean = prefs?.getBoolean(KEY_NOTIFICATION_ASKED, false) ?: true

    fun markNotificationAsked() {
        prefs?.edit()?.putBoolean(KEY_NOTIFICATION_ASKED, true)?.apply()
    }

    fun setPlaylistUri(value: String?) {
        prefs?.edit()?.apply { if (value == null) remove(KEY_PLAYLIST) else putString(KEY_PLAYLIST, value) }?.apply()
        _settings.value = _settings.value.copy(playlistUri = value)
    }

    fun setMusicMode(value: MusicMode) {
        prefs?.edit()?.putString(KEY_MODE, value.name)?.apply()
        _settings.value = _settings.value.copy(musicMode = value)
    }

    fun setTargetType(value: TargetType) {
        prefs?.edit()?.putString(KEY_TARGET_TYPE, value.name)?.apply()
        _settings.value = _settings.value.copy(targetType = value)
    }

    fun setTargetDistanceM(value: Int) {
        val clamped = value.coerceIn(0, 20_000)
        prefs?.edit()?.putInt(KEY_TARGET_DISTANCE, clamped)?.apply()
        _settings.value = _settings.value.copy(targetDistanceM = clamped)
    }

    fun setStrideCm(value: Int) {
        val clamped = value.coerceIn(Distance.MIN_STRIDE_CM, Distance.MAX_STRIDE_CM)
        prefs?.edit()?.putInt(KEY_STRIDE, clamped)?.apply()
        _settings.value = _settings.value.copy(strideCm = clamped)
    }

    fun setTargetMinutes(value: Int) {
        val clamped = value.coerceIn(0, 180)
        prefs?.edit()?.putInt(KEY_TARGET, clamped)?.apply()
        _settings.value = _settings.value.copy(targetMinutes = clamped)
    }

    fun setAutoStopAtTarget(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_STOP, value)?.apply()
        _settings.value = _settings.value.copy(autoStopAtTarget = value)
    }
}
