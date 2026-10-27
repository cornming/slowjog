package com.cornming.slowjog.spotify

/** 解析使用者貼上的 Spotify 歌單連結。 */
object SpotifyLink {

    private const val ID = "[A-Za-z0-9]{22}"
    private val uriPattern = Regex("^spotify:playlist:($ID)$")
    private val webPattern =
        Regex("^https?://open\\.spotify\\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?playlist/($ID)(?:[/?#].*)?$", RegexOption.IGNORE_CASE)

    /** 回傳 `spotify:playlist:<id>`；不是歌單連結時回傳 null。 */
    fun parsePlaylist(input: String): String? {
        val text = input.trim()
        val id = uriPattern.matchEntire(text)?.groupValues?.get(1)
            ?: webPattern.matchEntire(text)?.groupValues?.get(1)
            ?: return null
        return "spotify:playlist:$id"
    }

    /** 沒有 Spotify App 時，用瀏覽器開啟的網址。 */
    fun webUrl(playlistUri: String): String = "https://open.spotify.com/playlist/" + playlistUri.substringAfterLast(':')
}
