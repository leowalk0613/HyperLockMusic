package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
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
    fun miBlurAlphas_onLight_atLeastAsStrongAsDark() {
        val light = MinimalClockTextStylePolicy.miBlurAlphas(onLightBackground = true)
        val dark = MinimalClockTextStylePolicy.miBlurAlphas(onLightBackground = false)
        assertTrue(light.blendAlpha >= dark.blendAlpha)
        assertTrue(light.labAlpha >= dark.labAlpha)
    }

    @Test
    fun readableText_onLight_isBlack_onDark_isWhite() {
        val onLight = MinimalClockTextStylePolicy.readableTextRgb(
            onLightBackground = true,
            tintRgb = MinimalClockTextStylePolicy.rgb(200, 40, 80),
        )
        val onDark = MinimalClockTextStylePolicy.readableTextRgb(
            onLightBackground = false,
            tintRgb = MinimalClockTextStylePolicy.rgb(200, 40, 80),
        )
        assertEquals(0, MinimalClockTextStylePolicy.red(onLight))
        assertEquals(0xFF, MinimalClockTextStylePolicy.red(onDark))
        assertTrue(MinimalClockTextStylePolicy.luminance(onDark) > 230f)
        assertTrue(MinimalClockTextStylePolicy.luminance(onLight) < 10f)
    }

    @Test
    fun lightShadow_usesDarkHalo() {
        val sh = MinimalClockTextStylePolicy.shadowLayer(onLightBackground = true)
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
