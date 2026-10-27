package com.cornming.slowjog.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 開始運動時的音樂行為。 */
enum class MusicMode { ASK, ALWAYS, NEVER }

data class Settings(
    /** `spotify:playlist:<id>`；未設定為 null。 */
    val playlistUri: String? = null,
    val musicMode: MusicMode = MusicMode.ASK,
    val shuffle: Boolean = true,
    /** Spotify 開發者後台的 Client ID；沒填時只能開啟 Spotify 歌單頁，不能直接播放。 */
    val spotifyClientId: String = "",
    /** 整段運動的目標時間（分鐘），0 代表不設目標。 */
    val targetMinutes: Int = 30,
    /** 到目標時間時自動停止音樂與節拍器。 */
    val autoStopAtTarget: Boolean = true,
)

/** 設定都存在本機的 SharedPreferences。 */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_PLAYLIST = "playlistUri"
    private const val KEY_MODE = "musicMode"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_CLIENT_ID = "spotifyClientId"
    private const val KEY_TARGET = "targetMinutes"
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
            shuffle = p.getBoolean(KEY_SHUFFLE, true),
            spotifyClientId = p.getString(KEY_CLIENT_ID, "").orEmpty(),
            targetMinutes = p.getInt(KEY_TARGET, 30),
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

    fun setShuffle(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SHUFFLE, value)?.apply()
        _settings.value = _settings.value.copy(shuffle = value)
    }

    fun setSpotifyClientId(value: String) {
        val clean = value.trim()
        prefs?.edit()?.putString(KEY_CLIENT_ID, clean)?.apply()
        _settings.value = _settings.value.copy(spotifyClientId = clean)
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
