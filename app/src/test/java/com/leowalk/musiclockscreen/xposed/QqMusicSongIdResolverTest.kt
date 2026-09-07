package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertNull(QqMusicSongIdResolver.parseSongIdFromTrackKey("qqmusic:mid:002Wrx7Z2ljXix"))
    }

    @Test
    fun trackKeyFromSongMid_roundTrip() {
        assertEquals(
            "qqmusic:mid:002Wrx7Z2ljXix",
            QqMusicSongIdResolver.trackKeyFromSongMid("002Wrx7Z2ljXix"),
        )
        assertEquals(
            "002Wrx7Z2ljXix",
            QqMusicSongIdResolver.parseSongMidFromTrackKey("qqmusic:mid:002Wrx7Z2ljXix"),
        )
        assertNull(QqMusicSongIdResolver.parseSongMidFromTrackKey("qqmusic:375040598"))
    }

    @Test
    fun parseSongId_rejectsBlank() {
        assertNull(QqMusicSongIdResolver.parseSongId(null))
        assertNull(QqMusicSongIdResolver.parseSongId(""))
        assertNull(QqMusicSongIdResolver.parseSongId("abc"))
        assertEquals(375040598L, QqMusicSongIdResolver.parseSongId("375040598"))
    }

    @Test
    fun parseSongMidFromShareJson_miuiFocus() {
        val json = """
            {"param_v2":{"param_island":{"shareData":{
              "title":"NEO",
              "shareContent":"https://i.y.qq.com/n2/m/musiclite/playsong/index.html?app_type=qmlite&&songmid=002Wrx7Z2ljXix"
            }}}}
        """.trimIndent()
        assertEquals("002Wrx7Z2ljXix", QqMusicSongIdResolver.parseSongMidFromShareJson(json))
    }

    @Test
    fun parseSongMidFromUrl_queryParam() {
        assertEquals(
            "003uYs9U1Y1ud2",
            QqMusicSongIdResolver.parseSongMidFromUrl(
                "https://i.y.qq.com/n2/m/musiclite/playsong/index.html?app_type=qmlite&&songmid=003uYs9U1Y1ud2",
            ),
        )
        assertNull(QqMusicSongIdResolver.parseSongMidFromUrl("https://y.music.163.com/m/song?id=1"))
    }

    @Test
    fun isQqCatalogPackage_includesMiuiPlayer() {
        assertTrue(QqMusicSongIdResolver.isQqCatalogPackage("com.tencent.qqmusic"))
        assertTrue(QqMusicSongIdResolver.isQqCatalogPackage("com.miui.player"))
        assertFalse(QqMusicSongIdResolver.isQqCatalogPackage("cn.wenyu.bodian"))
        assertFalse(QqMusicSongIdResolver.isQqCatalogPackage(null))
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

    @Test
    fun sameSongIdentity_qqSongMidKeys() {
        assertTrue(
            AlbumArtResolver.sameSongIdentity(
                "qqmusic:mid:002Wrx7Z2ljXix",
                "qqmusic:mid:002Wrx7Z2ljXix",
            ),
        )
        assertFalse(
            AlbumArtResolver.sameSongIdentity(
                "qqmusic:mid:002Wrx7Z2ljXix",
                "qqmusic:mid:003uYs9U1Y1ud2",
            ),
        )
        assertFalse(
            AlbumArtResolver.isRealTrackSwitch(
                "qqmusic:mid:002Wrx7Z2ljXix",
                "qqmusic:mid:002Wrx7Z2ljXix",
            ),
        )
    }
}
