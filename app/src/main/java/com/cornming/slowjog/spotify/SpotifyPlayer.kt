package com.cornming.slowjog.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cornming.slowjog.settings.Settings
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import java.util.concurrent.atomic.AtomicBoolean

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

    private const val TAG = "SlowJogSpotify"
    private const val HINT_MS = 5_000L
    private const val TIMEOUT_MS = 25_000L
    private val main = Handler(Looper.getMainLooper())

    /**
     * 請 Spotify 播放歌單。結果一定會在主執行緒回報一次：
     * SDK 的回呼可能在背景執行緒，也可能完全沒有回應，所以另外設了逾時，逾時就改開歌單頁。
     */
    fun play(
        context: Context,
        settings: Settings,
        onProgress: (String) -> Unit = {},
        onResult: (Outcome) -> Unit,
    ) {
        val uri = settings.playlistUri
        if (uri == null) {
            onResult(Outcome.Failed("尚未設定歌單"))
            return
        }
        if (settings.spotifyClientId.isBlank()) {
            onResult(openInSpotify(context, uri, "尚未設定 Client ID，已開啟 Spotify 歌單，請按播放"))
            return
        }

        val done = AtomicBoolean(false)
        var remoteRef: SpotifyAppRemote? = null
        fun finish(build: () -> Outcome) {
            if (!done.compareAndSet(false, true)) return
            main.post {
                main.removeCallbacksAndMessages(null)
                remoteRef?.let { SpotifyAppRemote.disconnect(it) }
                onResult(build())
            }
        }
        main.postDelayed({
            if (!done.get()) onProgress("還在等待 Spotify 回應。如果 Spotify 跳出授權畫面，請按同意；也可以先手動開啟 Spotify 再試。")
        }, HINT_MS)
        main.postDelayed({
            finish { openInSpotify(context, uri, "Spotify 在 25 秒內沒有回應，已開啟歌單頁，請按播放") }
        }, TIMEOUT_MS)

        if (!SpotifyAppRemote.isSpotifyInstalled(context)) {
            finish { openInSpotify(context, uri, "這支手機沒有安裝 Spotify") }
            return
        }
        try {
            val params = ConnectionParams.Builder(settings.spotifyClientId)
                .setRedirectUri(REDIRECT_URI)
                .showAuthView(true)
                .build()
            Log.d(TAG, "connect")
            SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    Log.d(TAG, "connected")
                    remoteRef = remote
                    remote.playerApi.setShuffle(settings.shuffle)
                    remote.playerApi.play(uri)
                        .setResultCallback {
                            Log.d(TAG, "play ok")
                            finish { Outcome.Playing }
                        }
                        .setErrorCallback { error ->
                            Log.d(TAG, "play error", error)
                            finish {
                                openInSpotify(context, uri, "無法直接播放（${describe(error)}），已開啟 Spotify 歌單")
                            }
                        }
                }

                override fun onFailure(error: Throwable) {
                    Log.d(TAG, "connect failed", error)
                    finish {
                        openInSpotify(context, uri, "無法連線 Spotify（${describe(error)}），已開啟歌單頁")
                    }
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "connect threw", e)
            finish { openInSpotify(context, uri, "無法連線 Spotify（${describe(e)}），已開啟歌單頁") }
        }
    }

    private fun describe(error: Throwable): String =
        error.javaClass.simpleName + (error.message?.let { ": $it" } ?: "")

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
