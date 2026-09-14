package com.leowalk.musiclockscreen.xposed

/**
 * 画报页歌词 + 歌曲信息共用文字样式。
 * 深底近白；白/浅底用浅灰字（不取深黑），靠阴影保对比。
 */
internal object MagazinePageTextStylePolicy {

    /** 亮度 ≥ 此值视为浅底。 */
    const val LIGHT_LUM_THRESHOLD = 0.55f

    /** 浅底主字：略灰于纯白，仍属浅色系。 */
    val LIGHT_BG_GLYPH_RGB: Int = rgb(216, 216, 220)

    /** 深底主字。 */
    val DARK_BG_GLYPH_RGB: Int = rgb(255, 255, 255)

    data class MiBlurAlphas(val blendAlpha: Int, val labAlpha: Int)

    data class ShadowSpec(val radius: Float, val dy: Float, val colorArgb: Int)

    fun isLightBackground(color: Int): Boolean =
        luminance(color) >= LIGHT_LUM_THRESHOLD

    fun glyphBaseRgb(onLight: Boolean): Int =
        if (onLight) LIGHT_BG_GLYPH_RGB else DARK_BG_GLYPH_RGB

    fun miBlurPrimaryRgb(onLight: Boolean): Int = glyphBaseRgb(onLight)

    fun miBlurOverArgb(onLight: Boolean): Int =
        if (onLight) {
            argb(120, red(LIGHT_BG_GLYPH_RGB), green(LIGHT_BG_GLYPH_RGB), blue(LIGHT_BG_GLYPH_RGB))
        } else {
            argb(140, 255, 255, 255)
        }

    fun miBlurBlendRgb(onLight: Boolean, tintRgb: Int): Int =
        blendRgb(glyphBaseRgb(onLight), tintRgb, AlbumTintExtractPolicy.MIBLUR_BLEND_WEIGHT_ON_DARK)

    fun miBlurAlphas(@Suppress("UNUSED_PARAMETER") onLight: Boolean): MiBlurAlphas =
        MiBlurAlphas(blendAlpha = 180, labAlpha = 170)

    fun glyphPrimaryRgb(onLight: Boolean): Int = glyphBaseRgb(onLight)

    fun glyphSecondaryArgb(onLight: Boolean): Int {
        val p = glyphPrimaryRgb(onLight)
        val a = if (onLight) 190 else 170
        return argb(a, red(p), green(p), blue(p))
    }

    fun glyphShadow(onLight: Boolean): ShadowSpec =
        if (onLight) {
            ShadowSpec(radius = 16f, dy = 4f, colorArgb = argb(245, 0, 0, 0))
        } else {
            ShadowSpec(radius = 14f, dy = 5f, colorArgb = argb(230, 0, 0, 0))
        }

    fun fallbackReadableRgb(onLight: Boolean, tintRgb: Int): Int =
        blendRgb(glyphBaseRgb(onLight), tintRgb, AlbumTintExtractPolicy.GLYPH_TINT_WEIGHT_ON_DARK)

    fun fallbackSecondaryArgb(onLight: Boolean, mainRgb: Int): Int {
        val a = if (onLight) 190 else 160
        return argb(a, red(mainRgb), green(mainRgb), blue(mainRgb))
    }

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
