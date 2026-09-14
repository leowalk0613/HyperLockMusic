package com.leowalk.musiclockscreen.xposed

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 专辑/壁纸取色策略：
 * - 深底：accent 洗成近白浅彩，混进白字
 * - 浅/过白底：少量突出色掺进偏白浅灰字；没有对比色才回退浅灰
 *
 * 不依赖 [android.graphics.Color] 的 HSV API，便于 JVM 单测。
 */
internal object AlbumTintExtractPolicy {

    data class TintPair(val contrast: Int, val accent: Int)

    /** 深底歌词/时钟混入 accent 的权重（越小越白）。 */
    const val GLYPH_TINT_WEIGHT_ON_DARK = 0.10f

    /** 浅底用突出色时，混入权重（突出色已可读，轻混即可）。 */
    const val GLYPH_TINT_WEIGHT_ON_LIGHT = 0.08f

    /** MiBlur blend 在深底上的 accent 权重。 */
    const val MIBLUR_BLEND_WEIGHT_ON_DARK = 0.14f

    /** MiBlur blend 在浅底上的 accent 权重。 */
    const val MIBLUR_BLEND_WEIGHT_ON_LIGHT = 0.12f

    /** 相对采样像素，chroma 加权总和超过此比例视为「有突出色」。 */
    const val PROMINENT_ACCENT_RATIO = 0.008f

    /** 过白图上：至少这么多带彩度像素才认作线稿/色点突出色。 */
    const val OVERWHITE_MIN_CHROMA_PIXELS = 4

    /** 过白图上：单像素最大 chroma 权重下限。 */
    const val OVERWHITE_MIN_MAX_CHROMA = 0.035f

    /** 浅底无突出色时的回退浅灰（偏白一点，靠阴影保对比）。 */
    val LIGHT_BG_GRAY_FALLBACK: Int = rgb(158, 158, 162)

    /** 近灰/近白/近黑：彩度权重为 0；过白图上的淡金/线稿仍给一点权重。 */
    fun chromaWeight(r: Int, g: Int, b: Int): Float {
        val maxC = max(r, max(g, b)).toFloat()
        val minC = min(r, min(g, b)).toFloat()
        if (maxC < 1f) return 0f
        val sat = (maxC - minC) / maxC
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        if (sat < 0.08f) return 0f
        if (lum < 0.08f) return 0f
        // 近白但有彩度：降权保留，避免被整片白底淹没
        if (lum > 0.88f) {
            if (sat < 0.10f) return 0f
            // 越接近纯白权重越低，但仍保留淡金线
            val nearWhitePenalty = ((lum - 0.88f) / 0.12f).coerceIn(0f, 1f)
            return sat * sat * (0.55f - 0.25f * nearWhitePenalty)
        }
        val mid = 1f - abs(lum - 0.45f) / 0.45f
        return sat * sat * mid.coerceIn(0.15f, 1f)
    }

    data class ChromaStats(
        val opaqueCount: Int,
        val chromaticCount: Int,
        val accentWeightSum: Double,
        val maxChromaWeight: Float,
    )

    fun hasProminentAccent(accentWeightSum: Double, opaquePixelCount: Int): Boolean {
        if (opaquePixelCount <= 0) return false
        return accentWeightSum / opaquePixelCount.toDouble() >= PROMINENT_ACCENT_RATIO
    }

    /**
     * 过白封面常见细线稿：缩放后占比极低，需用「彩度像素数 + 峰值」兜底。
     */
    fun hasProminentAccent(stats: ChromaStats, overWhiteContrast: Boolean): Boolean {
        if (stats.opaqueCount <= 0) return false
        if (hasProminentAccent(stats.accentWeightSum, stats.opaqueCount)) return true
        if (!overWhiteContrast) return false
        if (stats.chromaticCount >= OVERWHITE_MIN_CHROMA_PIXELS &&
            stats.maxChromaWeight >= OVERWHITE_MIN_MAX_CHROMA
        ) {
            return true
        }
        return stats.chromaticCount >= 2 &&
            stats.maxChromaWeight >= 0.10f &&
            stats.accentWeightSum >= 0.35
    }

    fun isProminentRawAccent(color: Int): Boolean {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        // 淡金线稿平均后可能偏亮，放宽上限
        return hsv[1] >= 0.10f && hsv[2] in 0.10f..0.98f
    }

    /**
     * 深底雾色/混色：洗成近白浅彩。
     */
    fun normalizeAccentTint(color: Int): Int {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        val sat = hsv[1]
        when {
            sat < 0.10f -> {
                hsv[1] = 0.02f
                hsv[2] = 0.94f
            }
            else -> {
                hsv[1] = (sat * 0.28f).coerceIn(0.04f, 0.16f)
                hsv[2] = 0.93f
            }
        }
        return hsvToColor(hsv)
    }

    /**
     * 浅底字形：只掺少量突出色相，整体仍偏浅灰白（可读靠阴影）。
     * 非突出色返回 null，由调用方回退浅灰。
     */
    fun accentForLightGlyph(rawAccent: Int): Int? {
        if (!isProminentRawAccent(rawAccent)) return null
        val hsv = rgbToHsv(red(rawAccent), green(rawAccent), blue(rawAccent))
        // 低饱和 + 高明度：视角上偏白，仅留一点专辑色
        hsv[1] = (hsv[1] * 0.16f).coerceIn(0.04f, 0.12f)
        hsv[2] = 0.80f
        return hsvToColor(hsv)
    }

    /** 字形混色前再洗一遍（深底路径）。 */
    fun washAccentTowardWhite(color: Int): Int {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        hsv[1] = (hsv[1] * 0.55f).coerceIn(0f, 0.12f)
        hsv[2] = max(hsv[2], 0.90f).coerceIn(0.90f, 0.98f)
        if (hsv[1] < 0.03f) {
            hsv[1] = 0.015f
            hsv[2] = 0.95f
        }
        return hsvToColor(hsv)
    }

    fun isNearWhiteOrBlack(color: Int): Boolean {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        return hsv[2] < 0.12f || hsv[2] > 0.92f
    }

    fun isWashedNearWhite(color: Int): Boolean {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        return hsv[1] <= 0.18f && hsv[2] >= 0.88f
    }

    fun luminance(color: Int): Float {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun isOverWhiteContrast(contrast: Int): Boolean = luminance(contrast) >= 0.82f

    fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val h = when {
            delta < 1e-6f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (maxC < 1e-6f) 0f else delta / maxC
        return floatArrayOf(h, s, maxC)
    }

    fun hsvToColor(hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (rp, gp, bp) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(
            ((rp + m) * 255f).toInt().coerceIn(0, 255),
            ((gp + m) * 255f).toInt().coerceIn(0, 255),
            ((bp + m) * 255f).toInt().coerceIn(0, 255),
        )
    }

    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
