package com.leowalk.musiclockscreen.xposed

/**
 * 歌词超宽截断：用末端渐隐代替「…」省略号。
 * 截断仍交给 StaticLayout TruncateAt.END，绘制时去掉省略号并做透明度渐隐。
 */
internal object LyricTextFadeTruncate {

    /** 渐隐宽度相对字号的倍数。 */
    const val FADE_EM = 1.5f

    fun fadeWidthPx(textSizePx: Float): Float {
        return (textSizePx * FADE_EM).coerceAtLeast(16f)
    }

    fun needsEndFade(textWidthPx: Float, maxWidthPx: Float): Boolean {
        return textWidthPx > maxWidthPx + 0.5f
    }

    /** StaticLayout 某行是否被 TruncateAt.END 截断（有省略号）。 */
    fun lineHasEllipsis(ellipsisCount: Int): Boolean = ellipsisCount > 0

    fun layoutHasEllipsis(lineCount: Int, ellipsisCountAt: (Int) -> Int): Boolean {
        for (i in 0 until lineCount) {
            if (lineHasEllipsis(ellipsisCountAt(i))) return true
        }
        return false
    }

    /** 渐隐起点相对内容右缘的 inset（= fade 宽度）。 */
    fun fadeStartInsetPx(textSizePx: Float, contentWidthPx: Float): Float {
        return fadeWidthPx(textSizePx).coerceAtMost(contentWidthPx * 0.5f)
    }
}
