package com.cornming.slowjog.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cornming.slowjog.settings.Settings
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

/**
 * 請 Spotify 播放設定好的歌單。
 * 有填 Client ID 時用 Spotify App Remote 在背景直接播放；
 * 沒填，或連線失敗（沒裝 Spotify、沒登入、尚未授權等）時，改為開啟 Spotify 的歌單頁，由使用者按播放。
 */
object SpotifyPlayer {

    /** 要填在 Spotify 開發者後台的 Redirect URI。 */
    const val REDIRECT_URI = "slowjog://spotify-callback"

    sealed interface Outcome {
        data object Playing : Outcome
        /** 改為開啟 Spotify 歌單頁，reason 說明原因。 */
        data class OpenedSpotify(val reason: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    fun play(context: Context, settings: Settings, onResult: (Outcome) -> Unit) {
        val uri = settings.playlistUri ?: return onResult(Outcome.Failed("尚未設定歌單"))
        if (settings.spotifyClientId.isBlank()) {
            onResult(openInSpotify(context, uri, "尚未設定 Client ID，已開啟 Spotify 歌單，請按播放"))
            return
        }
        val params = ConnectionParams.Builder(settings.spotifyClientId)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                remote.playerApi.setShuffle(settings.shuffle)
                remote.playerApi.play(uri)
                    .setResultCallback {
                        // 播放由 Spotify App 負責，中斷連線不會停止音樂。
                        SpotifyAppRemote.disconnect(remote)
                        onResult(Outcome.Playing)
                    }
                    .setErrorCallback { error ->
                        SpotifyAppRemote.disconnect(remote)
                        onResult(openInSpotify(context, uri, "無法直接播放（${error.message ?: "未知錯誤"}），已開啟 Spotify 歌單"))
                    }
            }

            override fun onFailure(error: Throwable) {
                onResult(openInSpotify(context, uri, "無法連線 Spotify（${error.message ?: error.javaClass.simpleName}），已開啟歌單頁"))
            }
        })
    }

    private fun openInSpotify(context: Context, playlistUri: String, reason: String): Outcome {
        val app = context.applicationContext
        return try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playlistUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.OpenedSpotify(reason)
        } catch (e: ActivityNotFoundException) {
            try {
                app.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyLink.webUrl(playlistUri))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                Outcome.OpenedSpotify("手機沒有 Spotify，已用瀏覽器開啟歌單")
            } catch (e2: ActivityNotFoundException) {
                Outcome.Failed("找不到可以開啟歌單的 App")
            }
        }
    }
}
