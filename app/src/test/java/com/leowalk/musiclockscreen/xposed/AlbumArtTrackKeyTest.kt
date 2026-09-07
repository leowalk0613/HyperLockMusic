package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtTrackKeyTest {

    @Test
    fun isRealTrackSwitch_falseForIdAndQqmusicAlias() {
        assertFalse(AlbumArtResolver.isRealTrackSwitch("id:413273228", "qqmusic:413273228"))
        assertFalse(AlbumArtResolver.isRealTrackSwitch("qqmusic:413273228", "id:413273228"))
    }

    @Test
    fun isRealTrackSwitch_falseForIdAndNeteaseAlias() {
        assertFalse(AlbumArtResolver.isRealTrackSwitch("id:2009805294", "netease:2009805294"))
    }

    @Test
    fun isRealTrackSwitch_trueForDifferentSongs() {
        assertTrue(AlbumArtResolver.isRealTrackSwitch("qqmusic:1", "qqmusic:2"))
        assertTrue(AlbumArtResolver.isRealTrackSwitch("id:1", "id:2"))
    }

    @Test
    fun sameSongIdentity_matchesNumericPrefixes() {
        assertTrue(AlbumArtResolver.sameSongIdentity("id:9", "qqmusic:9"))
        assertFalse(AlbumArtResolver.sameSongIdentity("qqmusic:9", "netease:9"))
    }
}
