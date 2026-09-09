package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTintExtractPolicyTest {

    @Test
    fun chromaWeight_zeroForNearWhiteBlackGray() {
        assertEquals(0f, AlbumTintExtractPolicy.chromaWeight(250, 250, 250), 0f)
        assertEquals(0f, AlbumTintExtractPolicy.chromaWeight(8, 8, 8), 0f)
        assertEquals(0f, AlbumTintExtractPolicy.chromaWeight(128, 128, 128), 0f)
    }

    @Test
    fun chromaWeight_positiveForSaturatedMid() {
        assertTrue(AlbumTintExtractPolicy.chromaWeight(220, 40, 60) > 0.2f)
        assertTrue(AlbumTintExtractPolicy.chromaWeight(30, 160, 220) > 0.2f)
    }

    @Test
    fun normalizeAccent_rejectsNearWhiteAndBlack() {
        val white = AlbumTintExtractPolicy.rgb(255, 255, 255)
        val black = AlbumTintExtractPolicy.rgb(0, 0, 0)
        val fromWhite = AlbumTintExtractPolicy.normalizeAccentTint(white)
        val fromBlack = AlbumTintExtractPolicy.normalizeAccentTint(black)
        assertFalse(AlbumTintExtractPolicy.isNearWhiteOrBlack(fromWhite))
        assertFalse(AlbumTintExtractPolicy.isNearWhiteOrBlack(fromBlack))

        val hsvW = AlbumTintExtractPolicy.rgbToHsv(
            AlbumTintExtractPolicy.red(fromWhite),
            AlbumTintExtractPolicy.green(fromWhite),
            AlbumTintExtractPolicy.blue(fromWhite),
        )
        assertTrue(hsvW[2] in 0.35f..0.8f)

        val hsvB = AlbumTintExtractPolicy.rgbToHsv(
            AlbumTintExtractPolicy.red(fromBlack),
            AlbumTintExtractPolicy.green(fromBlack),
            AlbumTintExtractPolicy.blue(fromBlack),
        )
        assertTrue(hsvB[2] in 0.35f..0.8f)
    }

    @Test
    fun normalizeAccent_keepsHueBoostsSat() {
        val crimson = AlbumTintExtractPolicy.rgb(180, 40, 60)
        val out = AlbumTintExtractPolicy.normalizeAccentTint(crimson)
        val hsvIn = AlbumTintExtractPolicy.rgbToHsv(180, 40, 60)
        val hsvOut = AlbumTintExtractPolicy.rgbToHsv(
            AlbumTintExtractPolicy.red(out),
            AlbumTintExtractPolicy.green(out),
            AlbumTintExtractPolicy.blue(out),
        )
        assertEquals(hsvIn[0], hsvOut[0], 2f)
        assertTrue(hsvOut[1] >= hsvIn[1] - 0.01f)
        assertTrue(hsvOut[2] in 0.40f..0.74f)
    }
}
