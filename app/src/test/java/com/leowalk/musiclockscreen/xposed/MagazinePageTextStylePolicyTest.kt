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
    fun lightBgGlyph_isReadableMidGrayNotNearWhiteOrDeepBlack() {
        val light = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = true)
        val dark = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight = false)
        assertEquals(118, MagazinePageTextStylePolicy.red(light))
        assertEquals(0xFF, MagazinePageTextStylePolicy.red(dark))
        val lum = MagazinePageTextStylePolicy.luminance(light)
        // 中灰：白底可读，且远亮于深黑墨色
        assertTrue(lum in 0.40f..0.55f)
        assertTrue(MagazinePageTextStylePolicy.luminance(dark) > 0.95f)
        assertTrue(lum > MagazinePageTextStylePolicy.luminance(MagazinePageTextStylePolicy.rgb(28, 28, 30)))
    }

    @Test
    fun fallbackOnLight_staysMidGrayFamily() {
        val fallback = MagazinePageTextStylePolicy.fallbackReadableRgb(
            onLight = true,
            tintRgb = MagazinePageTextStylePolicy.rgb(200, 180, 160),
        )
        val lum = MagazinePageTextStylePolicy.luminance(fallback)
        assertTrue(lum in 0.35f..0.60f)
    }

    @Test
    fun lightShadow_isDarkHaloNotWhiteGlow() {
        val sh = MagazinePageTextStylePolicy.glyphShadow(onLight = true)
        assertEquals(0, MagazinePageTextStylePolicy.red(sh.colorArgb))
        assertTrue((sh.colorArgb ushr 24) and 0xFF >= 200)
    }

    @Test
    fun lightBgWithProminentAccent_usesAccentNotGray() {
        val goldGlyph = AlbumTintExtractPolicy.accentForLightGlyph(
            AlbumTintExtractPolicy.rgb(210, 170, 60),
        )!!
        val withAccent = MagazinePageTextStylePolicy.glyphBaseRgb(onLight = true, goldGlyph)
        val grayFallback = MagazinePageTextStylePolicy.glyphBaseRgb(onLight = true, null)
        assertEquals(goldGlyph and 0xFFFFFF, withAccent and 0xFFFFFF)
        assertEquals(AlbumTintExtractPolicy.LIGHT_BG_GRAY_FALLBACK and 0xFFFFFF, grayFallback and 0xFFFFFF)
        assertTrue(withAccent != grayFallback)
    }

    @Test
    fun fallbackOnLight_withAccent_staysNearAccent() {
        val goldGlyph = AlbumTintExtractPolicy.accentForLightGlyph(
            AlbumTintExtractPolicy.rgb(210, 170, 60),
        )!!
        val out = MagazinePageTextStylePolicy.fallbackReadableRgb(
            onLight = true,
            tintRgb = MagazinePageTextStylePolicy.rgb(250, 250, 250),
            lightAccent = goldGlyph,
        )
        // 几乎直接用突出色（权重 0.05），应明显偏金而非中灰
        assertTrue(MagazinePageTextStylePolicy.red(out) > MagazinePageTextStylePolicy.blue(out) + 20)
        assertTrue(MagazinePageTextStylePolicy.luminance(out) < 0.55f)
    }
}
