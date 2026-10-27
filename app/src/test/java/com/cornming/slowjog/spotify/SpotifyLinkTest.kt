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
}
