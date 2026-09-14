package com.leowalk.musiclockscreen.xposed

/**
 * 画报页歌词 + 歌曲信息共用文字样式。
 * 一律近白字（可带极淡专辑色相）；浅底用更深阴影保可读，不取深黑墨色。
 */
internal object MagazinePageTextStylePolicy {

    /** 亮度 ≥ 此值视为浅底（用于阴影强弱，不再切深色字）。 */
    const val LIGHT_LUM_THRESHOLD = 0.55f

    data class MiBlurAlphas(val blendAlpha: Int, val labAlpha: Int)

    data class ShadowSpec(val radius: Float, val dy: Float, val colorArgb: Int)

    fun isLightBackground(color: Int): Boolean =
        luminance(color) >= LIGHT_LUM_THRESHOLD

    /** 始终白/近白 primary，忽略 onLight。 */
    fun miBlurPrimaryRgb(@Suppress("UNUSED_PARAMETER") onLight: Boolean): Int =
        rgb(255, 255, 255)

    fun miBlurOverArgb(@Suppress("UNUSED_PARAMETER") onLight: Boolean): Int =
        argb(140, 255, 255, 255)

    fun miBlurBlendRgb(@Suppress("UNUSED_PARAMETER") onLight: Boolean, tintRgb: Int): Int =
        blendRgb(rgb(255, 255, 255), tintRgb, AlbumTintExtractPolicy.MIBLUR_BLEND_WEIGHT_ON_DARK)

    fun miBlurAlphas(@Suppress("UNUSED_PARAMETER") onLight: Boolean): MiBlurAlphas =
        MiBlurAlphas(blendAlpha = 180, labAlpha = 170)

    fun glyphPrimaryRgb(onLight: Boolean): Int = miBlurPrimaryRgb(onLight)

    fun glyphSecondaryArgb(@Suppress("UNUSED_PARAMETER") onLight: Boolean): Int {
        val p = glyphPrimaryRgb(false)
        return argb(170, red(p), green(p), blue(p))
    }

    fun glyphShadow(onLight: Boolean): ShadowSpec =
        if (onLight) {
            // 浅底白字：更深黑晕抬对比
            ShadowSpec(radius = 16f, dy = 4f, colorArgb = argb(245, 0, 0, 0))
        } else {
            ShadowSpec(radius = 14f, dy = 5f, colorArgb = argb(230, 0, 0, 0))
        }

    /** MiBlur 未套上：白底混近白浅彩。 */
    fun fallbackReadableRgb(
        @Suppress("UNUSED_PARAMETER") onLight: Boolean,
        tintRgb: Int,
    ): Int = blendRgb(rgb(255, 255, 255), tintRgb, AlbumTintExtractPolicy.GLYPH_TINT_WEIGHT_ON_DARK)

    fun fallbackSecondaryArgb(
        @Suppress("UNUSED_PARAMETER") onLight: Boolean,
        mainRgb: Int,
    ): Int = argb(160, red(mainRgb), green(mainRgb), blue(mainRgb))

    fun fallbackShadow(onLight: Boolean): ShadowSpec =
        if (onLight) {
            ShadowSpec(radius = 14f, dy = 3f, colorArgb = argb(240, 0, 0, 0))
        } else {
            ShadowSpec(radius = 10f, dy = 3f, colorArgb = argb(230, 0, 0, 0))
        }

    fun luminance(color: Int): Float {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun rgb(r: Int, g: Int, b: Int): Int =
        ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or rgb(r, g, b)

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
