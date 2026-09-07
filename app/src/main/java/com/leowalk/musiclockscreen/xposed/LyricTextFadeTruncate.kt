package com.leowalk.musiclockscreen.xposed

/**
 * 歌词超宽截断：末端文字本身渐隐（不是只在省略号小区域淡）。
 * 截断仍用 StaticLayout TruncateAt.END 判定；绘制时去掉「…」，按满行排字再对末尾字形做透明度渐隐。
 */
internal object LyricTextFadeTruncate {

    /** 渐隐宽度相对字号的倍数（覆盖约 2 个字）。 */
    const val FADE_EM = 2.2f

    fun fadeWidthPx(textSizePx: Float): Float {
        return (textSizePx * FADE_EM).coerceAtLeast(24f)
    }

    fun needsEndFade(textWidthPx: Float, maxWidthPx: Float): Boolean {
        return textWidthPx > maxWidthPx + 0.5f
    }

    fun lineHasEllipsis(ellipsisCount: Int): Boolean = ellipsisCount > 0

    fun layoutHasEllipsis(lineCount: Int, ellipsisCountAt: (Int) -> Int): Boolean {
        for (i in 0 until lineCount) {
            if (lineHasEllipsis(ellipsisCountAt(i))) return true
        }
        return false
    }

    /**
     * 渐隐起点：相对「文字右缘」往左 inset，而不是相对整行空白。
     */
    fun fadeStartX(textLeft: Float, textRight: Float, textSizePx: Float): Float {
        val span = (textRight - textLeft).coerceAtLeast(1f)
        val inset = fadeWidthPx(textSizePx).coerceAtMost(span * 0.55f)
        return textRight - inset
    }
}
