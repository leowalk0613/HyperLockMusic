package com.leowalk.musiclockscreen.xposed

/**
 * 媒体控件背景：专辑主色 + 可调透明度（对齐 LyricFocus [AlbumColorExtractor.applyOpacity]）。
 * 纯 JVM，便于单测。
 */
internal object MediaBgAlbumTintPolicy {

    const val DEFAULT_OPACITY_PERCENT = 70
    const val MIN_OPACITY_PERCENT = 5
    const val MAX_OPACITY_PERCENT = 100

    fun coerceOpacity(percent: Int): Int =
        percent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    /** 开关关闭时不改系统媒体背景。 */
    fun shouldApply(enabled: Boolean): Boolean = enabled

    /**
     * 把 RGB 主色套上透明度（百分比）。
     * [rgb] 的 alpha 忽略；[opacityPercent] 100 = 不透明。
     */
    fun colorWithOpacity(rgb: Int, opacityPercent: Int): Int {
        val p = coerceOpacity(opacityPercent)
        val a = (255 * p / 100f).toInt().coerceIn(0, 255)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** SoftGlass / MiBlur 混色用：只取 RGB，透明度由 opacity 另行传入。 */
    fun tintRgb(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
