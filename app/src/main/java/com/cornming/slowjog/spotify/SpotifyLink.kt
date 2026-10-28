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

    /** 解析多行（或用逗號、空白分隔）貼上的連結：回傳有效歌單（去重、保持順序）與無法辨識的項目數。 */
    fun parsePlaylists(input: String): Pair<List<String>, Int> {
        val valid = LinkedHashSet<String>()
        var bad = 0
        for (token in input.split('\n', ',', ' ', '\t', '\r', '、', '，')) {
            if (token.isBlank()) continue
            val uri = parsePlaylist(token)
            if (uri == null) bad++ else valid.add(uri)
        }
        return valid.toList() to bad
    }

    /**
     * 從歌單清單挑這次要放的：隨機，但不會和上一次（[last]）一樣；只有一個歌單時就是它。
     * [rand] 為 0 以上、小於 1 的亂數；清單為空回傳 null。
     */
    fun pick(playlists: List<String>, last: String?, rand: Double): String? {
        if (playlists.isEmpty()) return null
        val candidates = if (playlists.size > 1) playlists.filter { it != last } else playlists
        return candidates[(rand * candidates.size).toInt().coerceIn(0, candidates.size - 1)]
    }

    /** 沒有 Spotify App 時，用瀏覽器開啟的網址。 */
    fun webUrl(playlistUri: String): String = "https://open.spotify.com/playlist/" + playlistUri.substringAfterLast(':')
}
