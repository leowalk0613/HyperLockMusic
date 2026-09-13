package com.leowalk.musiclockscreen.xposed

/**
 * 媒体控件背景：给系统玻璃混色实时染专辑色（不拆 MiBlur / SoftGlass）。
 *
 * SystemUI [NotificationUtil.applyElementViewBlend] 的 int[] 布局为
 * `[color0, mode0, color1, mode1, …]`（见 MiBlurCompat.setMiBackgroundBlendColors）。
 * 透明度滑杆按比例缩放系统原 alpha，mode 原样保留。
 */
internal object MediaBgAlbumTintPolicy {

    const val DEFAULT_OPACITY_PERCENT = 70
    const val MIN_OPACITY_PERCENT = 5
    const val MAX_OPACITY_PERCENT = 100

    fun coerceOpacity(percent: Int): Int =
        percent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    fun shouldApply(enabled: Boolean): Boolean = enabled

    /**
     * 把 RGB 主色套上透明度（百分比）。
     * 纯色路径备用；玻璃染色主路径用 [retintBlendPairs]。
     */
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
     * 保留玻璃 blend 的 mode，只把每档 color 换成专辑 RGB，
     * alpha = 系统原 alpha × opacityPercent/100。
     */
    fun retintBlendPairs(source: IntArray, tintRgb: Int, opacityPercent: Int): IntArray {
        if (source.size < 2) return source.copyOf()
        val opacity = coerceOpacity(opacityPercent) / 100f
        val r = (tintRgb shr 16) and 0xFF
        val g = (tintRgb shr 8) and 0xFF
        val b = tintRgb and 0xFF
        val out = source.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val origA = (out[i] ushr 24) and 0xFF
            val newA = (origA * opacity).toInt().coerceIn(0, 255)
            out[i] = (newA shl 24) or (r shl 16) or (g shl 8) or b
            // out[i + 1] = mode，原样
            i += 2
        }
        return out
    }
}
