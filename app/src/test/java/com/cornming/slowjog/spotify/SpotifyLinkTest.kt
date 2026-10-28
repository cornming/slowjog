package com.cornming.slowjog.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLinkTest {

    private val id = "37i9dQZF1DXdPec7aLTmlC"

    @Test
    fun parsesShareLinkWithTrackingQuery() {
        assertEquals("spotify:playlist:$id", SpotifyLink.parsePlaylist("https://open.spotify.com/playlist/$id?si=abc123def456"))
    }

    @Test
    fun parsesPlainWebLinkAndSpotifyUri() {
        assertEquals("spotify:playlist:$id", SpotifyLink.parsePlaylist("https://open.spotify.com/playlist/$id"))
        assertEquals("spotify:playlist:$id", SpotifyLink.parsePlaylist("spotify:playlist:$id"))
    }

    @Test
    fun parsesLinkWithRegionPrefixAndSurroundingSpaces() {
        assertEquals("spotify:playlist:$id", SpotifyLink.parsePlaylist("  https://open.spotify.com/intl-zh/playlist/$id?si=x  "))
    }

    @Test
    fun rejectsOtherSpotifyContent() {
        assertNull(SpotifyLink.parsePlaylist("https://open.spotify.com/track/$id"))
        assertNull(SpotifyLink.parsePlaylist("spotify:album:$id"))
    }

    @Test
    fun rejectsNonsense() {
        assertNull(SpotifyLink.parsePlaylist(""))
        assertNull(SpotifyLink.parsePlaylist("hello"))
        assertNull(SpotifyLink.parsePlaylist("https://open.spotify.com/playlist/short"))
        assertNull(SpotifyLink.parsePlaylist("https://example.com/playlist/$id"))
    }

    @Test
    fun webUrlRebuildsFromUri() {
        assertEquals("https://open.spotify.com/playlist/$id", SpotifyLink.webUrl("spotify:playlist:$id"))
    }

    private val id2 = "5nJ0XyvB3D1PaWLDbx5mXz"

    @Test
    fun parsesSeveralLinesAndCountsBadOnes() {
        val text = "https://open.spotify.com/playlist/$id?si=1\n\nspotify:playlist:$id2\nhello\nhttps://open.spotify.com/playlist/$id"
        val (list, bad) = SpotifyLink.parsePlaylists(text)
        assertEquals(listOf("spotify:playlist:$id", "spotify:playlist:$id2"), list) // 重複的只留一個
        assertEquals(1, bad)
    }

    @Test
    fun emptyTextHasNoPlaylists() {
        val (list, bad) = SpotifyLink.parsePlaylists("  \n ")
        assertEquals(emptyList<String>(), list)
        assertEquals(0, bad)
    }

    @Test
    fun pickNeverRepeatsTheLastOneWhenThereAreSeveral() {
        val a = "spotify:playlist:$id"
        val b = "spotify:playlist:$id2"
        for (r in listOf(0.0, 0.3, 0.5, 0.99)) {
            assertEquals(b, SpotifyLink.pick(listOf(a, b), a, r))
            assertEquals(a, SpotifyLink.pick(listOf(a, b), b, r))
        }
    }

    @Test
    fun pickCoversEveryCandidate() {
        val c = "spotify:playlist:AAAAAAAAAAAAAAAAAAAAAA"
        val all = listOf("spotify:playlist:$id", "spotify:playlist:$id2", c)
        val seen = listOf(0.0, 0.4, 0.9).mapNotNull { SpotifyLink.pick(all, null, it) }.toSet()
        assertEquals(all.toSet(), seen)
    }

    @Test
    fun pickWithOneOrNone() {
        val a = "spotify:playlist:$id"
        assertEquals(a, SpotifyLink.pick(listOf(a), a, 0.7)) // 只有一個就一直是它
        assertNull(SpotifyLink.pick(emptyList(), null, 0.5))
    }

    @Test
    fun pickIgnoresALastThatIsNoLongerInTheList() {
        val a = "spotify:playlist:$id"
        val b = "spotify:playlist:$id2"
        assertEquals(a, SpotifyLink.pick(listOf(a, b), "spotify:playlist:BBBBBBBBBBBBBBBBBBBBBB", 0.0))
    }
}
