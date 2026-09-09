package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸歌词「三行栈」：上一句 / 当前(+翻译展开) / 下一句。
 *
 * 切行：先换上「已展开」的新 triplet，再整块从下方滑入落点（一步到位）。
 * 避免「先单行下一句再展开翻译」；当前块相对视口底边锚点稳定。
 */
internal object ImmersiveLyricStackPolicy {

    /** 邻行相对当前行的不透明度。 */
    const val NEIGHBOR_ALPHA = 0.28f

    /** 当前行翻译副行静止透明度。 */
    const val SECONDARY_ALPHA = 0.72f

    /** 单段上滑时长。 */
    const val PROMOTION_MS = 220L

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
     * 自底向上完整几何（始终展开当前行：主行+翻译）。
     * 视口底边固定时：下一句贴底，当前块在其上方；当前变高只往上长。
     */
    data class BottomGeometry(
        val heightPx: Float,
        val prevTop: Float,
        val currentTop: Float,
        val secondaryTop: Float,
        val nextTop: Float,
        /** 当前主行底边（不含翻译），相对内容顶。 */
        val currentBottom: Float,
    )

    fun bottomGeometry(
        vPaddingPx: Float,
        gapPx: Float,
        prevHeightPx: Float,
        currentHeightPx: Float,
        secondaryHeightPx: Float,
        nextHeightPx: Float,
    ): BottomGeometry {
        val gap = gapPx.coerceAtLeast(0f)
        val curH = currentHeightPx.coerceAtLeast(1f)
        val prevH = prevHeightPx.coerceAtLeast(0f)
        val secH = secondaryHeightPx.coerceAtLeast(0f)
        val nextH = nextHeightPx.coerceAtLeast(0f)

        val nextBlock = if (nextH > 0f) nextH + gap else 0f
        val secBlock = if (secH > 0f) secH + gap else 0f
        val prevBlock = if (prevH > 0f) prevH + gap else 0f
        val height = vPaddingPx * 2f + prevBlock + curH + secBlock + nextBlock

        var y = height - vPaddingPx
        val nextTop: Float
        if (nextH > 0f) {
            y -= nextH
            nextTop = y
            y -= gap
        } else {
            nextTop = y
        }
        val secondaryTop: Float
        if (secH > 0f) {
            y -= secH
            secondaryTop = y
            y -= gap
        } else {
            secondaryTop = y
        }
        y -= curH
        val currentTop = y
        val currentBottom = currentTop + curH
        val prevTop = if (prevH > 0f) currentTop - gap - prevH else currentTop - gap
        return BottomGeometry(
            heightPx = height.coerceAtLeast(1f),
            prevTop = prevTop,
            currentTop = currentTop,
            secondaryTop = secondaryTop,
            nextTop = nextTop,
            currentBottom = currentBottom,
        )
    }

    /**
     * 滑入步长：按「已展开当前块」高度，整块从下方进入。
     */
    fun promotionStepPx(
        currentHeightPx: Float,
        secondaryHeightPx: Float,
        gapPx: Float,
    ): Float {
        val gap = gapPx.coerceAtLeast(0f)
        val cur = currentHeightPx.coerceAtLeast(1f)
        val sec = secondaryHeightPx.coerceAtLeast(0f)
        return if (sec > 0f) cur + gap + sec + gap else cur + gap
    }

    /**
     * progress 0→1：offset 从 +step 收到 0（先换已展开新词，再上滑到位）。
     */
    fun scrollOffsetPx(progress: Float, stepPx: Float): Float {
        return stepPx.coerceAtLeast(0f) * (1f - progress.coerceIn(0f, 1f))
    }

    /**
     * 内容贴视口底：originY + 几何坐标 = 画布坐标。
     * 视口变高时只增加上方空白，当前块相对底边位置不变。
     */
    fun contentOriginY(viewportHeightPx: Float, contentHeightPx: Float): Float {
        return (viewportHeightPx - contentHeightPx).coerceAtLeast(0f)
    }

    fun prevTopPx(currentTop: Float, prevHeight: Float, gapPx: Float): Float {
        return currentTop - gapPx.coerceAtLeast(0f) - prevHeight.coerceAtLeast(0f)
    }
}
