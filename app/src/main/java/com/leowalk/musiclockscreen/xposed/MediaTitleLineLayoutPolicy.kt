package com.leowalk.musiclockscreen.xposed

import android.graphics.Color

/**
 * 媒体歌名「分行」：主标题单行；有括号才显示副标题。
 * 副标题刻意小于歌手行，避免和 artist 抢层级。
 */
internal object MediaTitleLineLayoutPolicy {

    /**
     * 副标题 / 主标题。系统 song=18sp、artist=12sp；
     * 副标题取约 10sp（10/18），再额外缩小 [SUB_SIZE_SHRINK_DP]。
     */
    const val SUB_SIZE_RATIO = 10f / 18f

    /** 副标题在比例字号上再减的 dp。 */
    const val SUB_SIZE_SHRINK_DP = 1f

    /** 比 artist 的 α0.8 更淡，退一层。 */
    const val SUB_ALPHA = 160

    /**
     * 主标题 → 副标题间距（dp）。
     * 正值几乎看不出（字体 ascent/descent 已占缝）；要变窄必须用负 margin 上收。
     */
    const val SUB_TOP_MARGIN_DP = -4f

    /**
     * 有副标题时歌手 topMargin 上拉比例（相对副标题字号）。
     * 多吃一点行高，避免整块把歌手顶太靠下；仍留一点空隙。
     */
    const val ARTIST_PULL_RATIO = 0.7f

    fun shouldShowSubtitle(sub: String): Boolean = sub.isNotBlank()

    fun resolveSubPx(titlePx: Float, artistPx: Float?, density: Float = 1f): Int {
        val fromRatio = titlePx * SUB_SIZE_RATIO
        // 不超过歌手字号的 90%，避免和 artist 一样大
        val capped = artistPx?.takeIf { it > 0f }?.let { minOf(fromRatio, it * 0.9f) } ?: fromRatio
        val shrink = SUB_SIZE_SHRINK_DP * density.coerceAtLeast(0.5f)
        return (capped - shrink).toInt().coerceAtLeast(1)
    }

    fun subtitleColor(titleColor: Int, alpha: Int = SUB_ALPHA): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(titleColor),
            Color.green(titleColor),
            Color.blue(titleColor),
        )
    }

    fun tightenedArtistTopMargin(baseMarginPx: Int, subPx: Int): Int {
        val pull = (subPx * ARTIST_PULL_RATIO).toInt().coerceAtLeast(0)
        return baseMarginPx - pull
    }
}
