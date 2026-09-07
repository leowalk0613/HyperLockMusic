package com.leowalk.musiclockscreen.xposed

/**
 * 歌词超长截断：不显示省略号，只让「最后一个能显示的字」透明度渐隐。
 *
 * 注意：StaticLayout 的 setMaxLines  alone 不会裁掉后续行；必须自行按行截字符再建 layout。
 */
internal object LyricTextFadeTruncate {

    fun needsEndFade(textWidthPx: Float, maxWidthPx: Float): Boolean {
        return textWidthPx > maxWidthPx + 0.5f
    }

    /**
     * 取满布局中前 [maxLines] 行能覆盖到的文字结束下标（exclusive）。
     * [lineEndAt] 对应 [android.text.Layout.getLineEnd]。
     */
    fun visibleTextEndOffset(
        fullTextLength: Int,
        unrestrictedLineCount: Int,
        maxLines: Int,
        lineEndAt: (line: Int) -> Int,
    ): Int {
        if (fullTextLength <= 0 || unrestrictedLineCount <= 0 || maxLines <= 0) return 0
        val lastVisible = (minOf(maxLines, unrestrictedLineCount) - 1).coerceAtLeast(0)
        return lineEndAt(lastVisible).coerceIn(0, fullTextLength)
    }

    /**
     * 多行限制后是否还有未画出的字（应渐隐末字，而不是画 …）。
     */
    fun needsEndFadeForClippedLayout(
        unrestrictedLineCount: Int,
        clippedLineCount: Int,
        maxLines: Int,
        clippedTextEndOffset: Int,
        fullTextLength: Int,
    ): Boolean {
        if (clippedTextEndOffset < fullTextLength) return true
        if (unrestrictedLineCount > clippedLineCount && clippedLineCount >= maxLines) return true
        return false
    }

    /** 最后一个 code point 的起始下标（[start, end)）。 */
    fun lastCodePointStart(text: CharSequence, start: Int, end: Int): Int {
        if (end <= start) return start
        return try {
            Character.offsetByCodePoints(text, end, -1).coerceIn(start, end)
        } catch (_: Throwable) {
            (end - 1).coerceAtLeast(start)
        }
    }

    /** 末字渐隐起点 = 文字右缘往左一个末字宽。 */
    fun fadeStartX(textLeft: Float, textRight: Float, lastGlyphWidthPx: Float): Float {
        val glyph = lastGlyphWidthPx.coerceAtLeast(1f)
        return (textRight - glyph).coerceAtLeast(textLeft)
    }
}
