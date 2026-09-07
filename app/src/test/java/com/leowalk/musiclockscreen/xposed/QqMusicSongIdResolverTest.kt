package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QqMusicSongIdResolverTest {

    @Test
    fun trackKey_roundTrip() {
        assertEquals("qqmusic:375040598", QqMusicSongIdResolver.trackKey(375040598L))
        assertEquals(375040598L, QqMusicSongIdResolver.parseSongIdFromTrackKey("qqmusic:375040598"))
        assertNull(QqMusicSongIdResolver.parseSongIdFromTrackKey("netease:1"))
        assertNull(QqMusicSongIdResolver.parseSongIdFromTrackKey("id:1"))
    }

    @Test
    fun parseSongId_rejectsBlank() {
        assertNull(QqMusicSongIdResolver.parseSongId(null))
        assertNull(QqMusicSongIdResolver.parseSongId(""))
        assertNull(QqMusicSongIdResolver.parseSongId("abc"))
        assertEquals(375040598L, QqMusicSongIdResolver.parseSongId("375040598"))
    }

    @Test
    fun coverUrlForAlbumMid_stripsPmidSuffix() {
        val url = QqMusicSongIdResolver.coverUrlForAlbumMid("001kEV5C49E66Z_1", 1500)
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R1500x1500M000001kEV5C49E66Z.jpg",
            url,
        )
        assertTrue(url.contains("T002R1500x1500"))
    }
}
