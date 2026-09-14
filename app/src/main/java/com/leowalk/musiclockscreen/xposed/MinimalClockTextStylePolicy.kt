package com.leowalk.musiclockscreen.xposed

/**
 * 简洁时钟：对比只看模糊底。白模糊底 → 黑字；其余 → 白字。
 */
internal object MinimalClockTextStylePolicy {

    const val LYRIC_MI_BLUR_BLEND_LIGHT = 200
    const val LYRIC_MI_BLUR_LAB_LIGHT = 230
    const val LYRIC_MI_BLUR_BLEND_DARK = 180
    const val LYRIC_MI_BLUR_LAB_DARK = 170
    const val LYRIC_MAIN_TEXT_ALPHA = 255
    const val CLOCK_TEXT_ALPHA = 255

    val LIGHT_BG_GLYPH_RGB: Int get() = MagazinePageTextStylePolicy.LIGHT_BG_GLYPH_RGB
    val DARK_BG_GLYPH_RGB: Int get() = MagazinePageTextStylePolicy.DARK_BG_GLYPH_RGB

    val CLOCK_TYPEFACE_PATHS: Array<String> = arrayOf(
        "/system/fonts/MiSans-Bold.ttf",
        "/system/fonts/MiSans-Semibold.ttf",
        "/system/fonts/MiSans-Demibold.ttf",
        "/product/fonts/MiSans-Bold.ttf",
        "/system/fonts/MiSans-Medium.ttf",
        "/system/fonts/MiSans-Regular.ttf",
        "/product/fonts/MiSans-Regular.ttf",
    )

    fun clockTypefaceFallbackBold(): Boolean = true

    fun miBlurAlphas(@Suppress("UNUSED_PARAMETER") onLightBackground: Boolean): MiBlurAlphaPair {
        return MiBlurAlphaPair(
            blendAlpha = LYRIC_MI_BLUR_BLEND_DARK + 22,
            labAlpha = LYRIC_MI_BLUR_LAB_DARK + 24,
        )
    }

    fun glyphBaseRgb(onLightBackground: Boolean, lightAccent: Int? = null): Int =
        MagazinePageTextStylePolicy.glyphBaseRgb(onLightBackground, lightAccent)

    @Suppress("UNUSED_PARAMETER")
    fun readableTextRgb(
        onLightBackground: Boolean,
        tintRgb: Int,
        lightAccent: Int? = null,
    ): Int = glyphBaseRgb(onLightBackground, lightAccent)

    fun miBlurBlendRgb(
        onLightBackground: Boolean,
        tintRgb: Int,
        lightAccent: Int? = null,
    ): Int = MagazinePageTextStylePolicy.miBlurBlendRgb(onLightBackground, tintRgb, lightAccent)

    fun shadowLayer(onLightBackground: Boolean): ShadowSpec {
        val sh = MagazinePageTextStylePolicy.glyphShadow(onLightBackground)
        return ShadowSpec(radius = sh.radius, dy = sh.dy, colorArgb = sh.colorArgb)
    }

    fun luminance(rgb: Int): Float {
        val r = red(rgb)
        val g = green(rgb)
        val b = blue(rgb)
        return (0.2126f * r + 0.7152f * g + 0.0722f * b)
    }

    data class MiBlurAlphaPair(val blendAlpha: Int, val labAlpha: Int)

    data class ShadowSpec(val radius: Float, val dy: Float, val colorArgb: Int)

    fun rgb(r: Int, g: Int, b: Int): Int {
        return ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return ((a and 0xFF) shl 24) or rgb(r, g, b)
    }

    fun red(color: Int): Int = (color shr 16) and 0xFF

    fun green(color: Int): Int = (color shr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF
}
