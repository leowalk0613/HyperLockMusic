package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸歌词「三行上滑」：上一句 / 当前(+翻译) / 下一句。
 * 独立开关 + 沉浸歌词 + 亮屏；AOD / 息屏退回单行无动画。
 * 共用「切行动画」在未开三行上滑时对普通/沉浸均生效。
 */
internal object ImmersiveLyricStackPolicy {

    /** 邻行相对当前行的不透明度（更透）。 */
    const val NEIGHBOR_ALPHA = 0.28f

    fun shouldUseStack(
        immersiveLyric: Boolean,
        stackEnabled: Boolean,
        screenInteractive: Boolean,
    ): Boolean = immersiveLyric && stackEnabled && screenInteractive

    data class Triplet(
        val prev: String,
        val current: String,
        /** 当前行的另一语文案（翻译或原文）；空则无翻译行 */
        val currentSecondary: String,
        val next: String,
    )

    /**
     * @param swapEnabled 与「歌词翻译互换」一致：主行优先翻译时，副行显示原文。
     */
    fun resolveTriplet(
        lines: List<LineText>,
        index: Int,
        swapEnabled: Boolean,
    ): Triplet {
        if (lines.isEmpty()) return Triplet("", " ", "", "")
        val i = index.coerceIn(0, lines.lastIndex)
        fun primary(line: LineText): String {
            val trans = line.translation.trim()
            val raw = line.text.trim().ifBlank { " " }
            return if (swapEnabled && trans.isNotEmpty()) trans else raw
        }
        fun secondary(line: LineText): String {
            val trans = line.translation.trim()
            if (trans.isEmpty()) return ""
            val raw = line.text.trim()
            return if (swapEnabled) raw else trans
        }
        val cur = lines[i]
        val prev = if (i > 0) primary(lines[i - 1]) else ""
        val next = if (i + 1 < lines.size) primary(lines[i + 1]) else ""
        return Triplet(
            prev = prev,
            current = primary(cur),
            currentSecondary = secondary(cur),
            next = next,
        )
    }

    /** 与 [LockscreenLyricView.LyricLine] 解耦的最小行文本。 */
    data class LineText(val text: String, val translation: String = "")

    /**
     * 切行上滑距离：约一行字高 + 邻行空隙。
     * [gapPx] 与翻译↔下一句、上一句↔当前行共用（如 [LockscreenLyricView] 的 lineGapPx）。
     */
    fun scrollStepPx(fontLineHeightPx: Float, gapPx: Float): Float {
        val line = fontLineHeightPx.coerceAtLeast(1f)
        return line + gapPx.coerceAtLeast(0f)
    }

    /**
     * 上一句顶边：当前行顶边上方留 [gapPx]，再减去上一句高度。
     * 保证 prev 底边 ↔ current 顶边间距恒为 gap（与翻译↔下一句一致）。
     */
    fun prevTopPx(currentTop: Float, prevHeight: Float, gapPx: Float): Float {
        return currentTop - gapPx.coerceAtLeast(0f) - prevHeight.coerceAtLeast(0f)
    }
}
