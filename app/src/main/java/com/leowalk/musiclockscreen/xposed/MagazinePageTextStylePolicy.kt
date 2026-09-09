package com.leowalk.musiclockscreen.xposed

/**
 * 画报页歌词 + 歌曲信息共用的文字样式（真·MiBlur / 浅色底对比度）。
 * 浅彩底也走深色字，避免白字发虚。
 */
internal object MagazinePageTextStylePolicy {

    /** 亮度 ≥ 此值视为浅底（含浅彩，不限近白）。 */
    const val LIGHT_LUM_THRESHOLD = 0.55f

    data class MiBlurAlphas(val blendAlpha: Int, val labAlpha: Int)

    data class ShadowSpec(val radius: Float, val dy: Float, val colorArgb: Int)

    fun isLightBackground(color: Int): Boolean =
        luminance(color) >= LIGHT_LUM_THRESHOLD

    fun miBlurPrimaryRgb(onLight: Boolean): Int =
        if (onLight) rgb(12, 12, 14) else rgb(255, 255, 255)

    fun miBlurOverArgb(onLight: Boolean): Int =
        if (onLight) argb(210, 0, 0, 0) else argb(140, 255, 255, 255)

    fun miBlurBlendRgb(onLight: Boolean, tintRgb: Int): Int =
        if (onLight) {
            blendRgb(rgb(14, 14, 16), tintRgb, 0.18f)
        } else {
            blendRgb(rgb(255, 255, 255), tintRgb, 0.36f)
        }

    fun miBlurAlphas(onLight: Boolean): MiBlurAlphas =
        if (onLight) {
            MiBlurAlphas(blendAlpha = 230, labAlpha = 245)
        } else {
            MiBlurAlphas(blendAlpha = 180, labAlpha = 170)
        }

    /** MiBlur 生效后的字形底色（与 primary 一致）。 */
    fun glyphPrimaryRgb(onLight: Boolean): Int = miBlurPrimaryRgb(onLight)

    fun glyphSecondaryArgb(onLight: Boolean): Int {
        val p = glyphPrimaryRgb(onLight)
        val a = if (onLight) 220 else 170
        return argb(a, red(p), green(p), blue(p))
    }

    fun glyphShadow(onLight: Boolean): ShadowSpec =
        if (onLight) {
            // 浅底：白边晕提高对比
            ShadowSpec(radius = 12f, dy = 1f, colorArgb = argb(200, 255, 255, 255))
        } else {
            ShadowSpec(radius = 14f, dy = 5f, colorArgb = argb(230, 0, 0, 0))
        }

    /** MiBlur 未套上时的可读混色。 */
    fun fallbackReadableRgb(onLight: Boolean, tintRgb: Int): Int =
        if (onLight) {
            blendRgb(rgb(10, 10, 12), tintRgb, 0.12f)
        } else {
            blendRgb(rgb(255, 255, 255), tintRgb, 0.28f)
        }

    fun fallbackSecondaryArgb(onLight: Boolean, mainRgb: Int): Int {
        val a = if (onLight) 210 else 160
        return argb(a, red(mainRgb), green(mainRgb), blue(mainRgb))
    }

    fun fallbackShadow(onLight: Boolean): ShadowSpec =
        if (onLight) {
            ShadowSpec(radius = 11f, dy = 1f, colorArgb = argb(180, 255, 255, 255))
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
