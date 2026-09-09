package com.leowalk.musiclockscreen.xposed

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 专辑/壁纸取色策略：对比度用背景亮度代表色，染色用有彩度的 accent。
 * 避免「白封面取白、黑封面取黑」再拿去混字色。
 *
 * 不依赖 [android.graphics.Color] 的 HSV API，便于 JVM 单测。
 */
internal object AlbumTintExtractPolicy {

    data class TintPair(val contrast: Int, val accent: Int)

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
     * 把 accent 拉到可读染色区间：抬饱和、钳制明度，禁止近白/近黑当「主题色」。
     */
    fun normalizeAccentTint(color: Int): Int {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        val sat = hsv[1]
        val value = hsv[2]
        when {
            sat < 0.12f -> {
                // 纯灰：中性中明度，混进字色几乎无感，避免变白/变黑字
                hsv[1] = 0.06f
                hsv[2] = 0.55f
            }
            value < 0.14f || value > 0.90f || sat < 0.22f -> {
                hsv[1] = sat.coerceIn(0.38f, 1f)
                hsv[2] = value.coerceIn(0.42f, 0.72f)
            }
            else -> {
                hsv[1] = (sat * 1.22f).coerceIn(0.40f, 1f)
                hsv[2] = value.coerceIn(0.40f, 0.74f)
            }
        }
        return hsvToColor(hsv)
    }

    fun isNearWhiteOrBlack(color: Int): Boolean {
        val hsv = rgbToHsv(red(color), green(color), blue(color))
        return hsv[2] < 0.12f || hsv[2] > 0.92f
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
