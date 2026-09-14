package com.leowalk.musiclockscreen.xposed

/**
 * 简洁时钟文字样式：一律近白字，不取深黑墨色；浅底靠更深阴影保对比。
 */
internal object MinimalClockTextStylePolicy {

    /** 歌词 MiBlur / 主行参考（LockscreenLyricView） */
    const val LYRIC_MI_BLUR_BLEND_LIGHT = 200
    const val LYRIC_MI_BLUR_LAB_LIGHT = 230
    const val LYRIC_MI_BLUR_BLEND_DARK = 180
    const val LYRIC_MI_BLUR_LAB_DARK = 170
    const val LYRIC_MAIN_TEXT_ALPHA = 255

    /** 简洁时钟：paint 与 MiBlur 均不低于歌词，视觉上更实 */
    const val CLOCK_TEXT_ALPHA = 255

    /** 专辑色混入权重（近白浅彩） */
    const val TINT_WEIGHT = AlbumTintExtractPolicy.GLYPH_TINT_WEIGHT_ON_DARK

    /** 简洁时钟字重：优先 Bold / Semibold，比歌词主行 Medium 更醒目 */
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

    /** 始终近白字 + 浅彩混色。 */
    fun readableTextRgb(
        @Suppress("UNUSED_PARAMETER") onLightBackground: Boolean,
        tintRgb: Int,
    ): Int = blendRgb(rgb(255, 255, 255), tintRgb, TINT_WEIGHT)

    fun miBlurBlendRgb(
        @Suppress("UNUSED_PARAMETER") onLightBackground: Boolean,
        tintRgb: Int,
    ): Int = blendRgb(rgb(255, 255, 255), tintRgb, AlbumTintExtractPolicy.MIBLUR_BLEND_WEIGHT_ON_DARK)

    fun shadowLayer(onLightBackground: Boolean): ShadowSpec {
        return if (onLightBackground) {
            ShadowSpec(radius = 16f, dy = 4f, colorArgb = argb(245, 0, 0, 0))
        } else {
            ShadowSpec(radius = 14f, dy = 5f, colorArgb = argb(250, 0, 0, 0))
        }
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

    private fun blendRgb(base: Int, tint: Int, weight: Float): Int {
        val w = weight.coerceIn(0f, 1f)
        val inv = 1f - w
        return rgb(
            (red(base) * inv + red(tint) * w).toInt().coerceIn(0, 255),
            (green(base) * inv + green(tint) * w).toInt().coerceIn(0, 255),
            (blue(base) * inv + blue(tint) * w).toInt().coerceIn(0, 255),
        )
    }
}
