package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtMatcherTest {

    @Test
    fun netEaseImageKey_extractsPath() {
        val key = AlbumArtMatcher.netEaseImageKey(
            "https://p2.music.126.net/abcDEF123/109951168000000.jpg"
        )
        assertNotNull(key)
        assertTrue(key!!.contains("abcDEF123") || key.isNotBlank())
    }

    @Test
    fun sameNetEaseImage_ignoresHostAndQuery() {
        assertTrue(
            AlbumArtMatcher.sameNetEaseImage(
                "https://p1.music.126.net/SameKey/cover.jpg",
                "http://p2.music.126.net/SameKey/cover.jpg?param=200y200"
            )
        )
        assertFalse(
            AlbumArtMatcher.sameNetEaseImage(
                "https://p1.music.126.net/KeyA/x.jpg",
                "https://p1.music.126.net/KeyB/x.jpg"
            )
        )
    }

    @Test
    fun trackKeyPrefix_isNetease() {
        assertEquals("netease:12345", NetEaseSongIdResolver.trackKey(12345L))
        assertEquals(12345L, NetEaseSongIdResolver.parseSongIdFromTrackKey("netease:12345"))
    }
}
