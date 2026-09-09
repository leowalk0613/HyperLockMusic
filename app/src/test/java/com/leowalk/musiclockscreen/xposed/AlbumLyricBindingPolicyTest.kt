package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumLyricBindingPolicyTest {

    @Test
    fun bigAlbum_defaultsImmersiveLyric() {
        val d = AlbumLyricBindingPolicy.defaultsForImmersiveAlbum(immersiveAlbumOn = false)
        assertTrue(d.immersiveLyric)
        assertFalse(d.lyricHideBackground)
    }

    @Test
    fun immersiveAlbum_defaultsNormalLyricNoFog() {
        val d = AlbumLyricBindingPolicy.defaultsForImmersiveAlbum(immersiveAlbumOn = true)
        assertFalse(d.immersiveLyric)
        assertTrue(d.lyricHideBackground)
    }

    @Test
    fun defaults_areSymmetricOpposites() {
        val big = AlbumLyricBindingPolicy.defaultsForImmersiveAlbum(false)
        val imm = AlbumLyricBindingPolicy.defaultsForImmersiveAlbum(true)
        assertEquals(big.immersiveLyric, !imm.immersiveLyric)
        assertEquals(big.lyricHideBackground, !imm.lyricHideBackground)
    }
}
