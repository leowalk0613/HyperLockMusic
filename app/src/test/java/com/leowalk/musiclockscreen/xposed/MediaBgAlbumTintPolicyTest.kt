package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBgAlbumTintPolicyTest {

    @Test
    fun shouldApply_followsSwitch() {
        assertTrue(MediaBgAlbumTintPolicy.shouldApply(true))
        assertFalse(MediaBgAlbumTintPolicy.shouldApply(false))
    }

    @Test
    fun coerceOpacity_clampsRange() {
        assertEquals(
            MediaBgAlbumTintPolicy.MIN_OPACITY_PERCENT,
            MediaBgAlbumTintPolicy.coerceOpacity(0),
        )
        assertEquals(
            MediaBgAlbumTintPolicy.MAX_OPACITY_PERCENT,
            MediaBgAlbumTintPolicy.coerceOpacity(200),
        )
        assertEquals(55, MediaBgAlbumTintPolicy.coerceOpacity(55))
    }

    @Test
    fun colorWithOpacity_setsAlphaKeepsRgb() {
        val rgb = 0xFF3366AA.toInt()
        val out = MediaBgAlbumTintPolicy.colorWithOpacity(rgb, 50)
        assertEquals(127, (out ushr 24) and 0xFF) // 50% ≈ 127
        assertEquals(0x33, (out shr 16) and 0xFF)
        assertEquals(0x66, (out shr 8) and 0xFF)
        assertEquals(0xAA, out and 0xFF)
    }

    @Test
    fun colorWithOpacity_fullIsOpaque() {
        val rgb = 0xFF112233.toInt()
        val out = MediaBgAlbumTintPolicy.colorWithOpacity(rgb, 100)
        assertEquals(255, (out ushr 24) and 0xFF)
        assertEquals(0x11, (out shr 16) and 0xFF)
    }

    @Test
    fun tintRgb_forcesOpaque() {
        val translucent = 0x803366AA.toInt()
        val out = MediaBgAlbumTintPolicy.tintRgb(translucent)
        assertEquals(0xFF, (out ushr 24) and 0xFF)
        assertEquals(0x33, (out shr 16) and 0xFF)
        assertEquals(0x66, (out shr 8) and 0xFF)
        assertEquals(0xAA, out and 0xFF)
    }
}
