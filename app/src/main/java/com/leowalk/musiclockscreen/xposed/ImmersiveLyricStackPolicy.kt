package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸歌词「三行上滑」：上一句 / 当前(+翻译) / 下一句。
 *
 * 布局自底向上：歌词区域底边固定，内容增高时往上长。
 * 切行：当前行先单独上滑到落点，再到齐后展开邻行/翻译。
 */
internal object ImmersiveLyricStackPolicy {

    /** 邻行相对当前行的不透明度（更透）。 */
    const val NEIGHBOR_ALPHA = 0.28f

    /** 切行动画中「上滑」占前段比例，其后为展开。 */
    const val FOCUS_FRACTION = 0.55f

    fun shouldUseStack(
        immersiveLyric: Boolean,
        stackEnabled: Boolean,
        screenInteractive: Boolean,
    ): Boolean = immersiveLyric && stackEnabled && screenInteractive

    data class Triplet(
        val prev: String,
        val current: String,
        val currentSecondary: String,
        val next: String,
    )

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

    data class LineText(val text: String, val translation: String = "")

    /**
     * 自底向上几何。
     * [expand] 0=仅当前行（落点贴底）；1=完整 prev/当前/翻译/下一句。
     */
    data class BottomGeometry(
        val heightPx: Float,
        val prevTop: Float,
        val currentTop: Float,
        val secondaryTop: Float,
        val nextTop: Float,
        val showPrev: Boolean,
        val showSecondary: Boolean,
        val showNext: Boolean,
    )

    fun bottomGeometry(
        vPaddingPx: Float,
        gapPx: Float,
        prevHeightPx: Float,
        currentHeightPx: Float,
        secondaryHeightPx: Float,
        nextHeightPx: Float,
        expand: Float,
    ): BottomGeometry {
        val e = expand.coerceIn(0f, 1f)
        val gap = gapPx.coerceAtLeast(0f)
        val curH = currentHeightPx.coerceAtLeast(1f)
        val prevH = prevHeightPx.coerceAtLeast(0f)
        val secH = secondaryHeightPx.coerceAtLeast(0f)
        val nextH = nextHeightPx.coerceAtLeast(0f)

        val showNext = e > 0.001f && nextH > 0f
        val showSecondary = e > 0.001f && secH > 0f
        val showPrev = e > 0.001f && prevH > 0f

        // 展开高度：邻行/翻译高度按 expand 插值，底边始终在内容底部
        val nextBlock = if (nextH > 0f) (nextH + gap) * e else 0f
        val secBlock = if (secH > 0f) (secH + gap) * e else 0f
        val prevBlock = if (prevH > 0f) (prevH + gap) * e else 0f
        val height = vPaddingPx * 2f + prevBlock + curH + secBlock + nextBlock

        var y = height - vPaddingPx
        val nextTop: Float
        if (nextH > 0f && e > 0f) {
            val drawnNext = nextH * e
            y -= drawnNext
            nextTop = y
            y -= gap * e
        } else {
            nextTop = y
        }
        val secondaryTop: Float
        if (secH > 0f && e > 0f) {
            val drawnSec = secH * e
            y -= drawnSec
            secondaryTop = y
            y -= gap * e
        } else {
            secondaryTop = y
        }
        y -= curH
        val currentTop = y
        val prevTop = if (prevH > 0f && e > 0f) {
            currentTop - gap * e - prevH * e
        } else {
            currentTop - gap - prevH
        }
        return BottomGeometry(
            heightPx = height.coerceAtLeast(1f),
            prevTop = prevTop,
            currentTop = currentTop,
            secondaryTop = secondaryTop,
            nextTop = nextTop,
            showPrev = showPrev,
            showSecondary = showSecondary,
            showNext = showNext,
        )
    }

    /** 上滑段落进度 0→1（整段动画进度映射）。 */
    fun focusProgress(animProgress: Float): Float {
        val p = animProgress.coerceIn(0f, 1f)
        val f = FOCUS_FRACTION.coerceIn(0.2f, 0.85f)
        return (p / f).coerceIn(0f, 1f)
    }

    /** 展开段落进度 0→1。 */
    fun expandProgress(animProgress: Float): Float {
        val p = animProgress.coerceIn(0f, 1f)
        val f = FOCUS_FRACTION.coerceIn(0.2f, 0.85f)
        if (p <= f) return 0f
        return ((p - f) / (1f - f)).coerceIn(0f, 1f)
    }

    /** 当前行自下方滑入：focus=0 在落点下方一步，focus=1 在落点。 */
    fun currentSlideOffsetPx(focusProgress: Float, stepPx: Float): Float {
        val f = focusProgress.coerceIn(0f, 1f)
        return stepPx.coerceAtLeast(0f) * (1f - f)
    }

    fun scrollStepPx(currentHeightPx: Float, gapPx: Float): Float {
        return currentHeightPx.coerceAtLeast(1f) + gapPx.coerceAtLeast(0f)
    }

    fun prevTopPx(currentTop: Float, prevHeight: Float, gapPx: Float): Float {
        return currentTop - gapPx.coerceAtLeast(0f) - prevHeight.coerceAtLeast(0f)
    }

    fun neighborAlphaForExpand(expand: Float): Float {
        return NEIGHBOR_ALPHA * expand.coerceIn(0f, 1f)
    }

    fun secondaryAlphaForExpand(expand: Float): Float {
        return 0.72f * expand.coerceIn(0f, 1f)
    }
}
