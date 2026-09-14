package com.leowalk.musiclockscreen.xposed

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 专辑/壁纸取色策略：对比度用背景亮度代表色，染色用「近白浅彩」accent。
 * 保留一点色相提示，整体趋近白/浅灰，避免浓艳主题色抢戏。
 *
 * 不依赖 [android.graphics.Color] 的 HSV API，便于 JVM 单测。
 */
internal object AlbumTintExtractPolicy {

    data class TintPair(val contrast: Int, val accent: Int)

    /** 深底歌词/时钟混入 accent 的权重（越小越白）。 */
    const val GLYPH_TINT_WEIGHT_ON_DARK = 0.10f

    /** 浅底深色字路径已废弃；保留常量兼容旧调用。 */
    const val GLYPH_TINT_WEIGHT_ON_LIGHT = 0.08f

    /** MiBlur blend 在深底上的 accent 权重。 */
    const val MIBLUR_BLEND_WEIGHT_ON_DARK = 0.14f

    /** MiBlur blend 在浅底上的 accent 权重。 */
    const val MIBLUR_BLEND_WEIGHT_ON_LIGHT = 0.10f

    /** 近灰/近白/近黑：彩度权重为 0，不参与 accent 加权。 */
    fun chromaWeight(r: Int, g: Int, b: Int): Float {
        val maxC = max(r, max(g, b)).toFloat()
        val minC = min(r, min(g, b)).toFloat()
        if (maxC < 1f) return 0f
        val sat = (maxC - minC) / maxC
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        if (sat < 0.14f) return 0f
        if (lum < 0.10f || lum > 0.92f) return 0f
        // 偏好中等亮度、高饱和
        val mid = 1f - abs(lum - 0.45f) / 0.45f
        return sat * sat * mid.coerceIn(0.15f, 1f)
    }

    /**
     * 把 accent 洗成近白浅彩：保住色相，压饱和、抬明度。
     * 禁止浓艳中明度「主题色」直接混字。
     */
    fun normalizeAccentTint(color: Int): Int {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        val sat = hsv[1]
        when {
            sat < 0.10f -> {
                // 无彩：近白灰
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
     * 字形混色前再洗一遍：进一步趋近白，只留极淡色相。
     * 替代旧版「抬饱和」的 boostAlbumTint。
     */
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

    /** 规范化后的 accent 是否落在近白浅彩区间。 */
    fun isWashedNearWhite(color: Int): Boolean {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        return hsv[1] <= 0.18f && hsv[2] >= 0.88f
    }

    /** HSV：h∈[0,360)，s/v∈[0,1]。 */
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
