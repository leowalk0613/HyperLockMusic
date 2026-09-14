package com.leowalk.musiclockscreen.xposed

/**
 * 歌词 / 歌曲信息文字：对比只看模糊底（壁纸采样），不从专辑取色。
 * 白/浅模糊底 → 黑字；其余 → 白字。可读靠 MiBlur + 阴影。
 */
internal object MagazinePageTextStylePolicy {

    /** 亮度 ≥ 此值视为「白色模糊底」。 */
    const val LIGHT_LUM_THRESHOLD = 0.72f

    /** 浅模糊底：黑字。 */
    val LIGHT_BG_GLYPH_RGB: Int = rgb(0, 0, 0)

    /** 深/其他模糊底：白字。 */
    val DARK_BG_GLYPH_RGB: Int = rgb(255, 255, 255)

    data class MiBlurAlphas(val blendAlpha: Int, val labAlpha: Int)

    data class ShadowSpec(val radius: Float, val dy: Float, val colorArgb: Int)

    fun isLightBackground(color: Int): Boolean =
        luminance(color) >= LIGHT_LUM_THRESHOLD

    /** @param lightAccent 已废弃，忽略；保留签名以免大面积改调用方。 */
    @Suppress("UNUSED_PARAMETER")
    fun glyphBaseRgb(onLight: Boolean, lightAccent: Int? = null): Int =
        if (onLight) LIGHT_BG_GLYPH_RGB else DARK_BG_GLYPH_RGB

    fun miBlurPrimaryRgb(onLight: Boolean, lightAccent: Int? = null): Int =
        glyphBaseRgb(onLight, lightAccent)

    fun miBlurOverArgb(onLight: Boolean, lightAccent: Int? = null): Int {
        val base = glyphBaseRgb(onLight, lightAccent)
        // 浅底黑字：稍高的 over alpha，让 colorDark MiBlur 更明显
        return argb(if (onLight) 160 else 140, red(base), green(base), blue(base))
    }

    /** MiBlur blend：直接用黑/白主色，不再混专辑 tint。 */
    @Suppress("UNUSED_PARAMETER")
    fun miBlurBlendRgb(onLight: Boolean, tintRgb: Int, lightAccent: Int? = null): Int =
        glyphBaseRgb(onLight, lightAccent)

    fun miBlurAlphas(onLight: Boolean): MiBlurAlphas =
        if (onLight) {
            MiBlurAlphas(blendAlpha = 210, labAlpha = 200)
        } else {
            MiBlurAlphas(blendAlpha = 180, labAlpha = 170)
        }

    fun glyphPrimaryRgb(onLight: Boolean, lightAccent: Int? = null): Int =
        glyphBaseRgb(onLight, lightAccent)

    fun glyphSecondaryArgb(onLight: Boolean, lightAccent: Int? = null): Int {
        val p = glyphPrimaryRgb(onLight, lightAccent)
        val a = if (onLight) 200 else 170
        return argb(a, red(p), green(p), blue(p))
    }

    fun glyphShadow(onLight: Boolean): ShadowSpec =
        if (onLight) {
            // 黑字：极淡暗边即可
            ShadowSpec(radius = 8f, dy = 2f, colorArgb = argb(60, 0, 0, 0))
        } else {
            ShadowSpec(radius = 14f, dy = 5f, colorArgb = argb(230, 0, 0, 0))
        }

    @Suppress("UNUSED_PARAMETER")
    fun fallbackReadableRgb(onLight: Boolean, tintRgb: Int, lightAccent: Int? = null): Int =
        glyphBaseRgb(onLight, lightAccent)

    fun fallbackSecondaryArgb(onLight: Boolean, mainRgb: Int): Int {
        val a = if (onLight) 190 else 160
        return argb(a, red(mainRgb), green(mainRgb), blue(mainRgb))
    }

    fun fallbackShadow(onLight: Boolean): ShadowSpec = glyphShadow(onLight)

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
}
