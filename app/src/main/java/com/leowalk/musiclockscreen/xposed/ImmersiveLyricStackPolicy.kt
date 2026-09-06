package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸歌词「三行上滑」：上一句 / 当前(+翻译) / 下一句。
 * 独立开关 + 沉浸歌词 + 亮屏；AOD / 息屏退回单行无动画。
 * 共用「切行动画」在未开三行上滑时对普通/沉浸均生效。
 */
internal object ImmersiveLyricStackPolicy {

    /** 邻行相对当前行的不透明度（更透）。 */
    const val NEIGHBOR_ALPHA = 0.28f

    /**
     * 槽位间距相对「单行字高」的固定倍率（不跟换行后的 layout 高度变）。
     * 保证上一句 / 当前 / 下一句中心距恒定。
     */
    const val FIXED_STEP_RATIO = 1.85f

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

    /** 固定槽距：仅依赖字号行高，与各行实际换行高度无关。 */
    fun fixedStepPx(fontLineHeightPx: Float): Float {
        return (fontLineHeightPx.coerceAtLeast(1f) * FIXED_STEP_RATIO).coerceAtLeast(1f)
    }
}
