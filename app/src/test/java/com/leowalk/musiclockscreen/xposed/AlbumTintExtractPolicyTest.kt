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
    fun normalizeAccent_washesTowardNearWhite() {
        val white = AlbumTintExtractPolicy.rgb(255, 255, 255)
        val black = AlbumTintExtractPolicy.rgb(0, 0, 0)
        val crimson = AlbumTintExtractPolicy.rgb(180, 40, 60)

        assertTrue(AlbumTintExtractPolicy.isWashedNearWhite(AlbumTintExtractPolicy.normalizeAccentTint(white)))
        assertTrue(AlbumTintExtractPolicy.isWashedNearWhite(AlbumTintExtractPolicy.normalizeAccentTint(black)))
        assertTrue(AlbumTintExtractPolicy.isWashedNearWhite(AlbumTintExtractPolicy.normalizeAccentTint(crimson)))
    }

    @Test
    fun normalizeAccent_keepsHueSoftensSat() {
        val crimson = AlbumTintExtractPolicy.rgb(180, 40, 60)
        val out = AlbumTintExtractPolicy.normalizeAccentTint(crimson)
        val hsvIn = AlbumTintExtractPolicy.rgbToHsv(180, 40, 60)
        val hsvOut = AlbumTintExtractPolicy.rgbToHsv(
            AlbumTintExtractPolicy.red(out),
            AlbumTintExtractPolicy.green(out),
            AlbumTintExtractPolicy.blue(out),
        )
        assertEquals(hsvIn[0], hsvOut[0], 15f)
        assertTrue(hsvOut[1] < hsvIn[1])
        assertTrue(hsvOut[1] <= 0.18f)
        assertTrue(hsvOut[2] >= 0.88f)
    }

    @Test
    fun washAccentTowardWhite_staysNearWhiteGray() {
        val vivid = AlbumTintExtractPolicy.rgb(30, 160, 220)
        val washed = AlbumTintExtractPolicy.washAccentTowardWhite(
            AlbumTintExtractPolicy.normalizeAccentTint(vivid)
        )
        assertTrue(AlbumTintExtractPolicy.isWashedNearWhite(washed))
        assertFalse(AlbumTintExtractPolicy.isNearWhiteOrBlack(AlbumTintExtractPolicy.rgb(40, 40, 44)))
    }
}
