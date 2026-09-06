package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸歌词「三行上滑」：上一句 / 当前 / 下一句。
 * 仅亮屏 + 沉浸歌词 + 开关开启；AOD / 息屏退回单行无动画。
 */
internal object ImmersiveLyricStackPolicy {

    /** 邻行相对当前行的不透明度（更透）。 */
    const val NEIGHBOR_ALPHA = 0.28f

    /** 行距相对当前行高的比例。 */
    const val ROW_GAP_RATIO = 0.45f

    fun shouldUseStack(
        immersiveLyric: Boolean,
        stackEnabled: Boolean,
        screenInteractive: Boolean,
    ): Boolean = immersiveLyric && stackEnabled && screenInteractive

    data class Triplet(val prev: String, val current: String, val next: String)

    /**
     * @param swapEnabled 与「歌词翻译互换」一致：有翻译时三行都用翻译文案。
     */
    fun resolveTriplet(
        lines: List<LineText>,
        index: Int,
        swapEnabled: Boolean,
    ): Triplet {
        if (lines.isEmpty()) return Triplet("", " ", "")
        val i = index.coerceIn(0, lines.lastIndex)
        fun display(line: LineText): String {
            val trans = line.translation.trim()
            val raw = line.text.trim().ifBlank { " " }
            return if (swapEnabled && trans.isNotEmpty()) trans else raw
        }
        val prev = if (i > 0) display(lines[i - 1]) else ""
        val current = display(lines[i])
        val next = if (i + 1 < lines.size) display(lines[i + 1]) else ""
        return Triplet(prev, current, next)
    }

    /** 与 [LockscreenLyricView.LyricLine] 解耦的最小行文本。 */
    data class LineText(val text: String, val translation: String = "")

    fun stepPx(currentLineHeight: Float): Float {
        return (currentLineHeight * (1f + ROW_GAP_RATIO)).coerceAtLeast(1f)
    }
}
