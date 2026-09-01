package com.leowalk.musiclockscreen.xposed

/**
 * 简洁时钟文字样式：比歌词更实（更不透明），取色优先可读性。
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

    /** 专辑色混入权重（低于歌词 0.28f，优先保证对比度可读） */
    const val TINT_WEIGHT = 0.14f

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

    fun miBlurAlphas(onLightBackground: Boolean): MiBlurAlphaPair {
        return if (onLightBackground) {
            MiBlurAlphaPair(
                blendAlpha = LYRIC_MI_BLUR_BLEND_LIGHT + 24,
                labAlpha = LYRIC_MI_BLUR_LAB_LIGHT + 20,
            )
        } else {
            MiBlurAlphaPair(
                blendAlpha = LYRIC_MI_BLUR_BLEND_DARK + 22,
                labAlpha = LYRIC_MI_BLUR_LAB_DARK + 24,
            )
        }
    }

    /** 按背景亮度选高对比底色，再 lightly 混专辑色；返回 0xRRGGBB */
    fun readableTextRgb(onLightBackground: Boolean, tintRgb: Int): Int {
        val base = if (onLightBackground) rgb(16, 16, 18) else rgb(255, 255, 255)
        return blendRgb(base, tintRgb, TINT_WEIGHT)
    }

    fun miBlurBlendRgb(onLightBackground: Boolean, tintRgb: Int): Int {
        return if (onLightBackground) {
            blendRgb(rgb(20, 20, 22), tintRgb, 0.18f)
        } else {
            blendRgb(rgb(255, 255, 255), tintRgb, 0.20f)
        }
    }

    fun shadowLayer(onLightBackground: Boolean): ShadowSpec {
        return if (onLightBackground) {
            ShadowSpec(radius = 11f, dy = 2f, colorArgb = argb(140, 255, 255, 255))
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
