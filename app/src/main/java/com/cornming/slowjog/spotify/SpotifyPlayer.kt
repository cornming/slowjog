package com.cornming.slowjog.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 開啟 Spotify 的歌單頁，由使用者在 Spotify 按播放。
 * 不再透過 Spotify App Remote 連線，所以不需要 Client ID、也沒有等待時間：
 * 呼叫後立刻有結果。
 */
object SpotifyPlayer {

    sealed interface Outcome {
        /** 已用 Spotify App 開啟歌單頁。 */
        data object Opened : Outcome

        /** 手機沒有 Spotify，改用瀏覽器開啟網頁版歌單。 */
        data object OpenedInBrowser : Outcome

        data class Failed(val reason: String) : Outcome
    }

    fun open(context: Context, playlistUri: String?): Outcome {
        if (playlistUri == null) return Outcome.Failed("尚未設定歌單")
        val app = context.applicationContext
        return try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playlistUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.Opened
        } catch (e: ActivityNotFoundException) {
            try {
                app.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyLink.webUrl(playlistUri))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                Outcome.OpenedInBrowser
            } catch (e2: ActivityNotFoundException) {
                Outcome.Failed("找不到可以開啟歌單的 App")
            }
        }
    }
}
