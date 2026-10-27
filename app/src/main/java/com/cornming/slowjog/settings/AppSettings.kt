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
        )
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
