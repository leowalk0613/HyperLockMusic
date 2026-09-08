package com.leowalk.musiclockscreen.xposed

/**
 * 歌词超长截断：不显示省略号，在「阅读方向的末尾可视边」做透明度渐隐。
 *
 * 与脚本无关：CJK / 西文 / 西里尔 / 阿语等共用同一套几何。
 * - StaticLayout 的 setMaxLines alone 不会裁掉后续行；须自行按行截字符再建 layout。
 * - 按词换行或不可断长词时，末码点常是空白或落在框外；渐隐必须贴齐布局框边缘，
 *   且宽度至少约 1.25em，否则窄字母上看起来像「渐隐失效」。
 */
internal object LyricTextFadeTruncate {

    /** 相对字号的最小渐隐宽度（覆盖多个窄字形）。 */
    const val MIN_FADE_EM = 1.25f

    data class EndFadeGeometry(
        /** saveLayer / 裁剪左缘 */
        val layerLeft: Float,
        /** saveLayer / 裁剪右缘 */
        val layerRight: Float,
        /** 渐变不透明端 X */
        val fadeOpaqueX: Float,
        /** 渐变透明端 X（贴齐截断边） */
        val fadeTransparentX: Float,
    )

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
     * 多行限制后是否还有未画出的字（应渐隐，而不是画 …）。
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

    /** 行宽超出布局框（不可断长词等）时也需要末尾渐隐。 */
    fun needsEndFadeForLineWidth(lineWidthPx: Float, layoutWidthPx: Float): Boolean {
        return lineWidthPx > layoutWidthPx + 0.5f
    }

    /** 去掉行尾空白 / 换行后的 exclusive end，便于落到真实字形。 */
    fun trimTrailingWhitespaceEnd(text: CharSequence, start: Int, end: Int): Int {
        var e = end.coerceIn(start, text.length)
        val s = start.coerceIn(0, text.length)
        while (e > s) {
            val cp = Character.codePointBefore(text, e)
            if (!Character.isWhitespace(cp)) break
            e -= Character.charCount(cp)
        }
        return e
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

    /**
     * 从行尾（跳过空白）往回量，直到覆盖至少 [minFadeWidthPx]（默认 1.25em）。
     */
    fun resolveFadeWidthPx(
        text: CharSequence,
        lineStart: Int,
        lineEnd: Int,
        textSizePx: Float,
        measure: (start: Int, end: Int) -> Float,
    ): Float {
        val minW = (textSizePx * MIN_FADE_EM).coerceAtLeast(1f)
        val end = trimTrailingWhitespaceEnd(text, lineStart, lineEnd)
        if (end <= lineStart) return minW
        var start = lastCodePointStart(text, lineStart, end)
        var width = measure(start, end).coerceAtLeast(1f)
        while (start > lineStart && width < minW) {
            start = lastCodePointStart(text, lineStart, start)
            width = measure(start, end).coerceAtLeast(1f)
        }
        return maxOf(width, minW)
    }

    /**
     * 渐隐贴齐布局框内的截断边：LTR 在右缘，RTL 在左缘。
     */
    fun endFadeGeometry(
        lineLeft: Float,
        lineRight: Float,
        layoutWidth: Float,
        fadeWidthPx: Float,
        rtl: Boolean = false,
    ): EndFadeGeometry {
        val left = maxOf(lineLeft, 0f)
        val right = minOf(lineRight, layoutWidth).coerceAtLeast(left + 1f)
        val maxFade = (right - left).coerceAtLeast(1f)
        val fadeW = fadeWidthPx.coerceIn(1f, maxFade)
        return if (!rtl) {
            EndFadeGeometry(
                layerLeft = left,
                layerRight = right,
                fadeOpaqueX = right - fadeW,
                fadeTransparentX = right,
            )
        } else {
            EndFadeGeometry(
                layerLeft = left,
                layerRight = right,
                fadeOpaqueX = left + fadeW,
                fadeTransparentX = left,
            )
        }
    }

    /** 末字渐隐起点 = 文字右缘往左一个末字宽（兼容旧调用）。 */
    fun fadeStartX(textLeft: Float, textRight: Float, lastGlyphWidthPx: Float): Float {
        val glyph = lastGlyphWidthPx.coerceAtLeast(1f)
        return (textRight - glyph).coerceAtLeast(textLeft)
    }
}
