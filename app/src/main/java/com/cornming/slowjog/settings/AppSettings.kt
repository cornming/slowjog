package com.cornming.slowjog.settings

import com.cornming.slowjog.mascot.MascotStyle
import com.cornming.slowjog.scenery.Scene
import com.cornming.slowjog.scenery.ScenePref
import com.cornming.slowjog.ui.AppTheme
import android.content.Context
import android.content.SharedPreferences
import com.cornming.slowjog.data.Habit
import com.cornming.slowjog.plan.WorkoutPlan
import com.cornming.slowjog.sound.ClickSound
import com.cornming.slowjog.workout.Distance
import com.cornming.slowjog.workout.Target
import com.cornming.slowjog.workout.TargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 開始運動時的音樂行為。 */
enum class MusicMode { ASK, ALWAYS, NEVER }

data class Settings(
    /** 歌單清單（每筆 `spotify:playlist:<id>`）；每次運動隨機挑一個，不會連續兩次一樣。 */
    val playlistUris: List<String> = emptyList(),
    /** 上一次放的歌單，下次避開。 */
    val lastPlaylistUri: String? = null,
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
    /** 每日運動提醒；預設早上 6:30（起床時間），可在設定調整。 */
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 6,
    val reminderMinute: Int = 30,
    /** 每週目標運動天數，用來算習慣分數。 */
    val weeklyTargetDays: Int = Habit.DEFAULT_WEEKLY_TARGET,
    val metronomeSound: ClickSound = ClickSound.CLICK,
    /** 階段式課表：暖身、超慢跑、肌力、伸展各幾分鐘（0 代表略過該階段）。 */
    val planEnabled: Boolean = true,
    val planWarmupMin: Int = WorkoutPlan.DEFAULT_WARMUP,
    val planJogMin: Int = WorkoutPlan.DEFAULT_JOG,
    val planStrengthMin: Int = WorkoutPlan.DEFAULT_STRENGTH,
    val planCooldownMin: Int = WorkoutPlan.DEFAULT_COOLDOWN,
    /** 階段切換時的提示音與震動。 */
    val cuesEnabled: Boolean = true,
    /** 語音播報階段與經過時間。 */
    val voiceEnabled: Boolean = true,
    /** 階段結束前倒數 5 秒，時間到自動開始或停止超慢跑、進入下一階段。 */
    val planAutoEnabled: Boolean = true,
    /** 介面風格。 */
    val appTheme: AppTheme = AppTheme.SUNRISE,
    /** 陪跑小人物的外型。 */
    val mascotStyle: MascotStyle = MascotStyle.BLUE,
    /** 跑步時的風景（固定一種或每天輪換）。 */
    val scenePref: ScenePref = ScenePref.Fixed(Scene.RIVERSIDE),
    /** 風景是否跟著跑步捲動。 */
    val sceneryMoving: Boolean = true,
) {
    fun target(): Target = Target(targetType, targetMinutes, targetDistanceM)

    fun plan(): WorkoutPlan = WorkoutPlan.of(planWarmupMin, planJogMin, planStrengthMin, planCooldownMin)
}

/** 設定都存在本機的 SharedPreferences。 */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_PLAYLIST = "playlistUri" // 舊版只有一個歌單
    private const val KEY_PLAYLISTS = "playlistUris"
    private const val KEY_LAST_PLAYLIST = "lastPlaylistUri"
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
    private const val KEY_WEEKLY_TARGET = "weeklyTargetDays"
    private const val KEY_SOUND = "metronomeSound"
    private const val KEY_PLAN_ON = "planEnabled"
    private const val KEY_PLAN_WARMUP = "planWarmupMin"
    private const val KEY_PLAN_JOG = "planJogMin"
    private const val KEY_PLAN_STRENGTH = "planStrengthMin"
    private const val KEY_PLAN_COOLDOWN = "planCooldownMin"
    private const val KEY_CUES = "cuesEnabled"
    private const val KEY_VOICE = "voiceEnabled"
    private const val KEY_PLAN_AUTO = "planAutoEnabled"
    private const val KEY_THEME = "appTheme"
    private const val KEY_MASCOT = "mascotStyle"
    private const val KEY_SCENE = "scenePref"
    private const val KEY_SCENE_MOVING = "sceneryMoving"

    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = Settings(
            playlistUris = (p.getString(KEY_PLAYLISTS, null) ?: p.getString(KEY_PLAYLIST, null))
                ?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            lastPlaylistUri = p.getString(KEY_LAST_PLAYLIST, null),
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
            reminderHour = p.getInt(KEY_REMINDER_HOUR, 6).coerceIn(0, 23),
            reminderMinute = p.getInt(KEY_REMINDER_MINUTE, 30).coerceIn(0, 59),
            weeklyTargetDays = p.getInt(KEY_WEEKLY_TARGET, Habit.DEFAULT_WEEKLY_TARGET)
                .coerceIn(Habit.MIN_WEEKLY_TARGET, Habit.MAX_WEEKLY_TARGET),
            metronomeSound = runCatching { ClickSound.valueOf(p.getString(KEY_SOUND, null).orEmpty()) }
                .getOrDefault(ClickSound.CLICK),
            planEnabled = p.getBoolean(KEY_PLAN_ON, true),
            planWarmupMin = p.getInt(KEY_PLAN_WARMUP, WorkoutPlan.DEFAULT_WARMUP).coerceIn(0, 60),
            planJogMin = p.getInt(KEY_PLAN_JOG, WorkoutPlan.DEFAULT_JOG).coerceIn(0, 60),
            planStrengthMin = p.getInt(KEY_PLAN_STRENGTH, WorkoutPlan.DEFAULT_STRENGTH).coerceIn(0, 60),
            planCooldownMin = p.getInt(KEY_PLAN_COOLDOWN, WorkoutPlan.DEFAULT_COOLDOWN).coerceIn(0, 60),
            cuesEnabled = p.getBoolean(KEY_CUES, true),
            voiceEnabled = p.getBoolean(KEY_VOICE, true),
            planAutoEnabled = p.getBoolean(KEY_PLAN_AUTO, true),
            appTheme = AppTheme.fromId(p.getString(KEY_THEME, null)),
            mascotStyle = MascotStyle.fromId(p.getString(KEY_MASCOT, null)),
            scenePref = ScenePref.fromId(p.getString(KEY_SCENE, null)),
            sceneryMoving = p.getBoolean(KEY_SCENE_MOVING, true),
        )
    }

    fun setMetronomeSound(value: ClickSound) {
        prefs?.edit()?.putString(KEY_SOUND, value.name)?.apply()
        _settings.value = _settings.value.copy(metronomeSound = value)
    }

    fun setPlanEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PLAN_ON, value)?.apply()
        _settings.value = _settings.value.copy(planEnabled = value)
    }

    /** 設定四個階段的分鐘數（各 0 到 60）。 */
    fun setPlanMinutes(warmup: Int, jog: Int, strength: Int, cooldown: Int) {
        val w = warmup.coerceIn(0, 60)
        val j = jog.coerceIn(0, 60)
        val s = strength.coerceIn(0, 60)
        val c = cooldown.coerceIn(0, 60)
        prefs?.edit()?.putInt(KEY_PLAN_WARMUP, w)?.putInt(KEY_PLAN_JOG, j)?.putInt(KEY_PLAN_STRENGTH, s)
            ?.putInt(KEY_PLAN_COOLDOWN, c)?.apply()
        _settings.value = _settings.value.copy(planWarmupMin = w, planJogMin = j, planStrengthMin = s, planCooldownMin = c)
    }

    fun setCuesEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CUES, value)?.apply()
        _settings.value = _settings.value.copy(cuesEnabled = value)
    }

    fun setAppTheme(value: AppTheme) {
        prefs?.edit()?.putString(KEY_THEME, value.id)?.apply()
        _settings.value = _settings.value.copy(appTheme = value)
    }

    fun setMascotStyle(value: MascotStyle) {
        prefs?.edit()?.putString(KEY_MASCOT, value.id)?.apply()
        _settings.value = _settings.value.copy(mascotStyle = value)
    }

    fun setScenePref(value: ScenePref) {
        prefs?.edit()?.putString(KEY_SCENE, value.id)?.apply()
        _settings.value = _settings.value.copy(scenePref = value)
    }

    fun setSceneryMoving(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SCENE_MOVING, value)?.apply()
        _settings.value = _settings.value.copy(sceneryMoving = value)
    }

    fun setPlanAutoEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PLAN_AUTO, value)?.apply()
        _settings.value = _settings.value.copy(planAutoEnabled = value)
    }

    fun setVoiceEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_VOICE, value)?.apply()
        _settings.value = _settings.value.copy(voiceEnabled = value)
    }

    fun setWeeklyTargetDays(value: Int) {
        val clamped = value.coerceIn(Habit.MIN_WEEKLY_TARGET, Habit.MAX_WEEKLY_TARGET)
        prefs?.edit()?.putInt(KEY_WEEKLY_TARGET, clamped)?.apply()
        _settings.value = _settings.value.copy(weeklyTargetDays = clamped)
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

    fun setPlaylistUris(value: List<String>) {
        prefs?.edit()?.putString(KEY_PLAYLISTS, value.joinToString("\n"))?.remove(KEY_PLAYLIST)?.apply()
        _settings.value = _settings.value.copy(playlistUris = value)
    }

    fun setLastPlaylist(value: String?) {
        prefs?.edit()?.apply { if (value == null) remove(KEY_LAST_PLAYLIST) else putString(KEY_LAST_PLAYLIST, value) }?.apply()
        _settings.value = _settings.value.copy(lastPlaylistUri = value)
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
