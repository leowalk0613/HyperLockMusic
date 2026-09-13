package com.leowalk.musiclockscreen.xposed

/**
 * 媒体控件背景：给系统玻璃混色实时染专辑色（不拆 MiBlur）。
 *
 * SystemUI 混色对为 `[color, mode, …]`；View 层
 * [addMiBackgroundBlendColor(color, mode)] 才是最终落点。
 */
internal object MediaBgAlbumTintPolicy {

    const val DEFAULT_OPACITY_PERCENT = 70
    const val MIN_OPACITY_PERCENT = 5
    const val MAX_OPACITY_PERCENT = 100

    /** 资源读不到时的兜底 blend mode（与 SoftGlass / 通知玻璃常见 101 一致）。 */
    const val FALLBACK_BLEND_MODE = 101

    fun coerceOpacity(percent: Int): Int =
        percent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    fun shouldApply(enabled: Boolean): Boolean = enabled

    fun colorWithOpacity(rgb: Int, opacityPercent: Int): Int {
        val p = coerceOpacity(opacityPercent)
        val a = (255 * p / 100f).toInt().coerceIn(0, 255)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun tintRgb(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 单档混色：保留/缩放原 alpha，RGB 换成专辑色。
     * [opacityPercent] 按系统原 alpha 比例缩放。
     */
    fun retintBlendColor(origColor: Int, tintRgb: Int, opacityPercent: Int): Int {
        val opacity = coerceOpacity(opacityPercent) / 100f
        val origA = (origColor ushr 24) and 0xFF
        val newA = (origA * opacity).toInt().coerceIn(0, 255)
        val r = (tintRgb shr 16) and 0xFF
        val g = (tintRgb shr 8) and 0xFF
        val b = tintRgb and 0xFF
        return (newA shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 保留 mode，只改 color 档。
     * [source] = `[color0, mode0, color1, mode1, …]`
     */
    fun retintBlendPairs(source: IntArray, tintRgb: Int, opacityPercent: Int): IntArray {
        if (source.size < 2) return source.copyOf()
        val out = source.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            out[i] = retintBlendColor(out[i], tintRgb, opacityPercent)
            i += 2
        }
        return out
    }

    /** 无系统模板时：用专辑色 + 强度生成一档玻璃混色。 */
    fun buildFallbackBlendPairs(tintRgb: Int, opacityPercent: Int): IntArray {
        val color = colorWithOpacity(tintRgb, opacityPercent)
        return intArrayOf(color, FALLBACK_BLEND_MODE)
    }
}
