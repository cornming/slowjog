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
)

/** 設定都存在本機的 SharedPreferences。 */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_PLAYLIST = "playlistUri"
    private const val KEY_MODE = "musicMode"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_CLIENT_ID = "spotifyClientId"

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
}
