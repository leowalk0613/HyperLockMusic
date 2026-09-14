package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertTrue
import org.junit.Test

class MinimalClockTextStylePolicyTest {

    @Test
    fun clockTextAlpha_notLessOpaqueThanLyricMain() {
        assertTrue(
            MinimalClockTextStylePolicy.CLOCK_TEXT_ALPHA >=
                MinimalClockTextStylePolicy.LYRIC_MAIN_TEXT_ALPHA
        )
    }

    @Test
    fun miBlurAlphas_notWeakerThanLyricDarkReference() {
        val light = MinimalClockTextStylePolicy.miBlurAlphas(onLightBackground = true)
        assertTrue(light.blendAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_BLEND_DARK)
        assertTrue(light.labAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_LAB_DARK)
        val dark = MinimalClockTextStylePolicy.miBlurAlphas(onLightBackground = false)
        assertTrue(dark.blendAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_BLEND_DARK)
        assertTrue(dark.labAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_LAB_DARK)
    }

    @Test
    fun readableText_onLight_isSoftGray_onDark_isNearWhite() {
        val onLight = MinimalClockTextStylePolicy.readableTextRgb(
            onLightBackground = true,
            tintRgb = MinimalClockTextStylePolicy.rgb(40, 40, 40),
        )
        val onDark = MinimalClockTextStylePolicy.readableTextRgb(
            onLightBackground = false,
            tintRgb = MinimalClockTextStylePolicy.rgb(40, 40, 40),
        )
        assertTrue(MinimalClockTextStylePolicy.luminance(onLight) in 180f..245f)
        assertTrue(MinimalClockTextStylePolicy.luminance(onDark) > 230f)
        assertTrue(
            MinimalClockTextStylePolicy.luminance(onLight) <
                MinimalClockTextStylePolicy.luminance(onDark)
        )
    }

    @Test
    fun lightShadow_usesDarkHalo() {
        val sh = MinimalClockTextStylePolicy.shadowLayer(onLightBackground = true)
        assertTrue((sh.colorArgb ushr 24) and 0xFF >= 200)
        assertTrue(MinimalClockTextStylePolicy.red(sh.colorArgb) == 0)
    }

    @Test
    fun clockTypefacePrefersBoldPaths() {
        assertTrue(
            MinimalClockTextStylePolicy.CLOCK_TYPEFACE_PATHS.first().contains("Bold", ignoreCase = true)
        )
    }

    @Test
    fun clockTypefaceFallbackIsBold() {
        assertTrue(MinimalClockTextStylePolicy.clockTypefaceFallbackBold())
    }
}
