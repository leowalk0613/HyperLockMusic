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
        assertEquals(127, (out ushr 24) and 0xFF)
        assertEquals(0x33, (out shr 16) and 0xFF)
        assertEquals(0x66, (out shr 8) and 0xFF)
        assertEquals(0xAA, out and 0xFF)
    }

    @Test
    fun retintBlendPairs_keepsModesSwapsRgbScalesAlpha() {
        val mode1 = 101
        val mode2 = 105
        val source = intArrayOf(
            0x80FFFFFF.toInt(), mode1,
            0x40FF0000.toInt(), mode2,
        )
        val tint = 0xFF3366AA.toInt()
        val out = MediaBgAlbumTintPolicy.retintBlendPairs(source, tint, 50)

        assertEquals(mode1, out[1])
        assertEquals(mode2, out[3])
        // alpha 0x80 * 50% = 0x40
        assertEquals(0x40, (out[0] ushr 24) and 0xFF)
        assertEquals(0x33, (out[0] shr 16) and 0xFF)
        assertEquals(0x66, (out[0] shr 8) and 0xFF)
        assertEquals(0xAA, out[0] and 0xFF)
        // alpha 0x40 * 50% = 0x20
        assertEquals(0x20, (out[2] ushr 24) and 0xFF)
        assertEquals(0x33, (out[2] shr 16) and 0xFF)
    }

    @Test
    fun retintBlendPairs_fullOpacityKeepsSystemAlpha() {
        val src = intArrayOf(0xAA112233.toInt(), 101)
        val out = MediaBgAlbumTintPolicy.retintBlendPairs(src, 0xFF445566.toInt(), 100)
        assertEquals(0xAA, (out[0] ushr 24) and 0xFF)
        assertEquals(0x44, (out[0] shr 16) and 0xFF)
        assertEquals(0x55, (out[0] shr 8) and 0xFF)
        assertEquals(0x66, out[0] and 0xFF)
        assertEquals(101, out[1])
    }
}
