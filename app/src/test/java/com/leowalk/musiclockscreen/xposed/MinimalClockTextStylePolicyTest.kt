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
    fun miBlurAlphas_strongerThanLyricReference() {
        val light = MinimalClockTextStylePolicy.miBlurAlphas(onLightBackground = true)
        assertTrue(light.blendAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_BLEND_LIGHT)
        assertTrue(light.labAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_LAB_LIGHT)
        val dark = MinimalClockTextStylePolicy.miBlurAlphas(onLightBackground = false)
        assertTrue(dark.blendAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_BLEND_DARK)
        assertTrue(dark.labAlpha >= MinimalClockTextStylePolicy.LYRIC_MI_BLUR_LAB_DARK)
    }

    @Test
    fun readableText_onLightBackground_isDarkEnough() {
        val color = MinimalClockTextStylePolicy.readableTextRgb(
            onLightBackground = true,
            tintRgb = MinimalClockTextStylePolicy.rgb(255, 255, 255),
        )
        assertTrue(MinimalClockTextStylePolicy.luminance(color) < 80f)
    }

    @Test
    fun readableText_onDarkBackground_staysBright() {
        val color = MinimalClockTextStylePolicy.readableTextRgb(
            onLightBackground = false,
            tintRgb = MinimalClockTextStylePolicy.rgb(40, 40, 40),
        )
        assertTrue(MinimalClockTextStylePolicy.luminance(color) > 180f)
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
