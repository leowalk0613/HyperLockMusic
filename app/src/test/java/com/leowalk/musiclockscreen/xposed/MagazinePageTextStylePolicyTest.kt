package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageTextStylePolicyTest {

    @Test
    fun lightBackground_whiteBlurOnly() {
        val nearWhite = MagazinePageTextStylePolicy.rgb(245, 245, 248)
        val lightGray = MagazinePageTextStylePolicy.rgb(200, 200, 205)
        val mid = MagazinePageTextStylePolicy.rgb(140, 140, 140)
        val dark = MagazinePageTextStylePolicy.rgb(40, 40, 44)

        assertTrue(MagazinePageTextStylePolicy.isLightBackground(nearWhite))
        assertTrue(MagazinePageTextStylePolicy.isLightBackground(lightGray))
        assertFalse(MagazinePageTextStylePolicy.isLightBackground(mid))
        assertFalse(MagazinePageTextStylePolicy.isLightBackground(dark))
    }

    @Test
    fun glyph_onLight_isBlack_onDark_isWhite() {
        val light = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = true)
        val dark = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = false)
        assertEquals(0, MagazinePageTextStylePolicy.red(light))
        assertEquals(0, MagazinePageTextStylePolicy.green(light))
        assertEquals(0, MagazinePageTextStylePolicy.blue(light))
        assertEquals(0xFF, MagazinePageTextStylePolicy.red(dark))
        assertTrue(MagazinePageTextStylePolicy.luminance(dark) > 0.95f)
    }

    @Test
    fun fallback_ignoresAlbumTint() {
        val out = MagazinePageTextStylePolicy.fallbackReadableRgb(
            onLight = false,
            tintRgb = MagazinePageTextStylePolicy.rgb(200, 40, 80),
            lightAccent = MagazinePageTextStylePolicy.rgb(210, 170, 60),
        )
        assertEquals(0xFF, MagazinePageTextStylePolicy.red(out))
        assertEquals(0xFF, MagazinePageTextStylePolicy.green(out))
        assertEquals(0xFF, MagazinePageTextStylePolicy.blue(out))
    }

    @Test
    fun miBlurBlend_isSolidGlyphNotTinted() {
        val blend = MagazinePageTextStylePolicy.miBlurBlendRgb(
            onLight = true,
            tintRgb = MagazinePageTextStylePolicy.rgb(255, 0, 0),
        )
        assertEquals(0, MagazinePageTextStylePolicy.red(blend))
    }

    @Test
    fun lightShadow_isSubtleDarkHalo() {
        val sh = MagazinePageTextStylePolicy.glyphShadow(onLight = true)
        assertEquals(0, MagazinePageTextStylePolicy.red(sh.colorArgb))
    }

    @Test
    fun darkShadow_keepsVisibleHaloForMiBlur() {
        val sh = MagazinePageTextStylePolicy.glyphShadow(onLight = false)
        assertTrue(sh.radius > 0f)
        assertTrue((sh.colorArgb ushr 24) > 0)
    }

    @Test
    fun chrome_alwaysUsesDarkBgWhiteGlyph() {
        // 底栏永白：即使背景被判为浅色，也应走白字路径参数
        val white = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = false)
        assertEquals(0xFF, MagazinePageTextStylePolicy.red(white))
        assertEquals(0xFF, MagazinePageTextStylePolicy.green(white))
        assertEquals(0xFF, MagazinePageTextStylePolicy.blue(white))
    }
}
