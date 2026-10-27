package com.cornming.slowjog.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {

    private val sample = """
        {
          "tag_name": "v0.1.3",
          "html_url": "https://github.com/cornming/slowjog/releases/tag/v0.1.3",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10},
            {"name": "slowjog-v0.1.3.apk", "browser_download_url": "https://example.com/slowjog-v0.1.3.apk", "size": 8722347}
          ]
        }
    """.trimIndent()

    @Test
    fun readsVersionAndApkAsset() {
        val info = ReleaseParser.parse(sample)!!
        assertEquals("0.1.3", info.version)
        assertEquals("https://example.com/slowjog-v0.1.3.apk", info.apkUrl)
        assertEquals(8722347L, info.apkSize)
        assertEquals("https://github.com/cornming/slowjog/releases/tag/v0.1.3", info.pageUrl)
    }

    @Test
    fun releaseWithoutApkReturnsNull() {
        assertNull(ReleaseParser.parse("""{"tag_name":"v0.1.3","assets":[]}"""))
    }

    @Test
    fun releaseWithoutTagReturnsNull() {
        assertNull(ReleaseParser.parse("""{"assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""))
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(ReleaseParser.parse("not json"))
    }
}
