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
    fun lightBgGlyph_isSoftGrayNotDeepBlack() {
        val light = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = true)
        val dark = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = false)
        assertEquals(216, MagazinePageTextStylePolicy.red(light))
        assertEquals(0xFF, MagazinePageTextStylePolicy.red(dark))
        // 浅灰：明显亮于中灰，又略暗于纯白
        assertTrue(MagazinePageTextStylePolicy.luminance(light) in 0.80f..0.95f)
        assertTrue(MagazinePageTextStylePolicy.luminance(dark) > 0.95f)
        assertTrue(MagazinePageTextStylePolicy.luminance(light) < MagazinePageTextStylePolicy.luminance(dark))
    }

    @Test
    fun fallbackOnLight_staysLightGrayFamily() {
        val fallback = MagazinePageTextStylePolicy.fallbackReadableRgb(
            onLight = true,
            tintRgb = MagazinePageTextStylePolicy.rgb(40, 40, 40),
        )
        assertTrue(MagazinePageTextStylePolicy.luminance(fallback) > 0.75f)
        assertTrue(MagazinePageTextStylePolicy.luminance(fallback) < 0.98f)
    }

    @Test
    fun lightShadow_isDarkHaloNotWhiteGlow() {
        val sh = MagazinePageTextStylePolicy.glyphShadow(onLight = true)
        assertEquals(0, MagazinePageTextStylePolicy.red(sh.colorArgb))
        assertTrue((sh.colorArgb ushr 24) and 0xFF >= 200)
    }
}
