package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageTextStylePolicyTest {

    @Test
    fun lightBackground_includesPastelNotOnlyNearWhite() {
        val nearWhite = MagazinePageTextStylePolicy.rgb(245, 245, 248)
        val pastel = MagazinePageTextStylePolicy.rgb(220, 200, 190) // lum ~0.8
        val midGray = MagazinePageTextStylePolicy.rgb(140, 140, 140)
        val dark = MagazinePageTextStylePolicy.rgb(40, 40, 44)

        assertTrue(MagazinePageTextStylePolicy.isLightBackground(nearWhite))
        assertTrue(MagazinePageTextStylePolicy.isLightBackground(pastel))
        assertFalse(MagazinePageTextStylePolicy.isLightBackground(midGray))
        assertFalse(MagazinePageTextStylePolicy.isLightBackground(dark))
    }

    @Test
    fun lightGlyph_isDarkerThanMidGray() {
        val ink = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = true)
        assertTrue(MagazinePageTextStylePolicy.luminance(ink) < 0.12f)
        assertEquals(0xFF, MagazinePageTextStylePolicy.red(MagazinePageTextStylePolicy.glyphPrimaryRgb(false)))
    }

    @Test
    fun miBlurAlphas_strongerOnLight() {
        val light = MagazinePageTextStylePolicy.miBlurAlphas(true)
        val dark = MagazinePageTextStylePolicy.miBlurAlphas(false)
        assertTrue(light.blendAlpha > dark.blendAlpha)
        assertTrue(light.labAlpha > dark.labAlpha)
    }
}
