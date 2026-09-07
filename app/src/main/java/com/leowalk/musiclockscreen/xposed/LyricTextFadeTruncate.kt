package com.leowalk.musiclockscreen.xposed

/**
 * 歌词超宽截断：用末端渐隐代替「…」省略号。
 */
internal object LyricTextFadeTruncate {

    /** 渐隐宽度相对字号的倍数。 */
    const val FADE_EM = 1.35f

    fun fadeWidthPx(textSizePx: Float): Float {
        return (textSizePx * FADE_EM).coerceAtLeast(12f)
    }

    fun needsEndFade(textWidthPx: Float, maxWidthPx: Float): Boolean {
        return textWidthPx > maxWidthPx + 0.5f
    }

    /**
     * 多行截断（原 TruncateAt.END 场景）：行数被压住，或末行贴满可用宽度且仍有溢出。
     */
    fun needsEndFadeForClippedLayout(
        unrestrictedLineCount: Int,
        clippedLineCount: Int,
        maxLines: Int,
        lastLineWidthPx: Float,
        contentWidthPx: Float,
    ): Boolean {
        if (unrestrictedLineCount > clippedLineCount && clippedLineCount >= maxLines) return true
        if (clippedLineCount >= maxLines &&
            needsEndFade(lastLineWidthPx, contentWidthPx)
        ) {
            return true
        }
        return false
    }

    /** 渐隐起点相对内容右缘的 inset（= fade 宽度）。 */
    fun fadeStartInsetPx(textSizePx: Float, contentWidthPx: Float): Float {
        return fadeWidthPx(textSizePx).coerceAtMost(contentWidthPx * 0.45f)
    }
}
