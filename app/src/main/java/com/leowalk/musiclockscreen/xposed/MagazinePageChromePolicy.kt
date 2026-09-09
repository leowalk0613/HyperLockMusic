package com.leowalk.musiclockscreen.xposed

import android.media.session.PlaybackState

/**
 * 画报页控件带：歌曲信息 + 控制钮。
 * 整块悬浮于距屏幕底 1/5 处（不贴底）；高度随内容包裹。
 * 控制行：关闭 / 上一曲 / 播放(略大) / 下一曲 / 歌词，空隙等权均分。
 */
internal object MagazinePageChromePolicy {

    /** 控件带底边距占屏高比例：距底部 1/8。 */
    const val CHROME_BOTTOM_OFFSET_FRACTION = 1f / 8f

    /** 歌词 / 切歌 / 退出 边长（dp）。 */
    const val CONTROL_BTN_SIZE_DP = 32

    /** 播放/暂停略大一档（dp）。 */
    const val PLAY_BTN_SIZE_DP = 40

    /** 兼容旧名。 */
    const val EDGE_BTN_SIZE_DP = CONTROL_BTN_SIZE_DP
    const val TRANSPORT_BTN_SIZE_DP = CONTROL_BTN_SIZE_DP

    /**
     * 相邻两钮之间的最小间距（dp）；实际由 4 段等权弹性空白均分。
     */
    const val CONTROL_GAP_MIN_DP = 12

    /** 兼容旧测试名。 */
    const val TRANSPORT_GAP_DP = CONTROL_GAP_MIN_DP
    const val EDGE_TO_TRANSPORT_MIN_GAP_DP = CONTROL_GAP_MIN_DP
    const val EDGE_BTN_HEIGHT_DP = EDGE_BTN_SIZE_DP
    const val TRANSPORT_BTN_HEIGHT_DP = TRANSPORT_BTN_SIZE_DP
    const val PLAY_BTN_HEIGHT_DP = PLAY_BTN_SIZE_DP

    /**
     * 内容区内边距（dp）：上下左右同一套。
     */
    const val CONTENT_PAD_DP = 12
    const val CHROME_HORIZONTAL_PAD_DP = CONTENT_PAD_DP
    const val CONTENT_PAD_TOP_DP = CONTENT_PAD_DP
    const val CONTENT_PAD_BOTTOM_DP = CONTENT_PAD_DP

    /** 歌曲信息行 → 按钮行间距（dp）。 */
    const val INFO_TO_CONTROLS_GAP_DP = 18

    /**
     * 歌曲信息行最大宽度占「整带可用宽」比例，居中。
     */
    const val INFO_ROW_MAX_WIDTH_FRACTION = 0.80f

    /** 按钮行最大宽度占「整带可用宽」比例，居中。 */
    const val CONTROLS_ROW_MAX_WIDTH_FRACTION = 0.85f

    /** 歌名旁小封面（对标 aodchange：左封面、右歌名/歌手，高度跨两行）。 */
    const val INFO_TITLE_SP = 20f
    const val INFO_ARTIST_SP = 13f
    const val INFO_SUBTITLE_SP = 12f
    const val INFO_TITLE_ARTIST_GAP_DP = 3
    const val INFO_SUBTITLE_GAP_DP = 2
    const val INFO_ALBUM_CORNER_DP = 5f
    const val INFO_ALBUM_GAP_DP = 10
    const val INFO_ALBUM_ELEVATION_DP = 8f

    /**
     * 小封面边长：固定按歌名 + 副标题 + 歌手三行合计，不随副标题显隐伸缩。
     */
    fun infoAlbumArtSizePx(
        density: Float,
        scaledDensity: Float,
        includeSubtitle: Boolean = true,
    ): Int {
        val d = density.coerceAtLeast(0.01f)
        val sd = scaledDensity.coerceAtLeast(0.01f)
        val titleH = (INFO_TITLE_SP * sd).toInt().coerceAtLeast((18f * d).toInt())
        val artistH = (INFO_ARTIST_SP * sd).toInt().coerceAtLeast((10f * d).toInt())
        val gap = (INFO_TITLE_ARTIST_GAP_DP * d).toInt()
        // 始终按三行高度：副标题行 + 间距，保证封面尺寸稳定
        val sub = (INFO_SUBTITLE_SP * sd).toInt() + (INFO_SUBTITLE_GAP_DP * d).toInt()
        return (titleH + artistH + gap + sub).coerceAtLeast((32f * d).toInt())
    }

    /** 控件带底边距屏幕底的像素距离。 */
    fun chromeBottomOffsetPx(screenHeightPx: Int): Int {
        if (screenHeightPx <= 0) return 0
        return (screenHeightPx * CHROME_BOTTOM_OFFSET_FRACTION).toInt()
            .coerceIn(0, screenHeightPx)
    }

    /**
     * 控件带内容估算高度（像素）：用于歌词锚点，实际布局为 wrap_content。
     */
    fun estimatedChromeContentHeightPx(density: Float, scaledDensity: Float): Int {
        val d = density.coerceAtLeast(0.01f)
        val pad = (CONTENT_PAD_DP * 2 * d).toInt()
        val gap = (INFO_TO_CONTROLS_GAP_DP * d).toInt()
        val info = infoAlbumArtSizePx(density, scaledDensity)
        val btns = (PLAY_BTN_SIZE_DP * d).toInt()
        return (pad + info + gap + btns).coerceAtLeast(1)
    }

    /** 控件带顶边 Y（估算）：屏高 − 底边距 − 内容高。 */
    fun chromeBandTopYPx(
        screenHeightPx: Int,
        density: Float = 3f,
        scaledDensity: Float = density,
    ): Int {
        if (screenHeightPx <= 0) return 0
        val bottom = chromeBottomOffsetPx(screenHeightPx)
        val h = estimatedChromeContentHeightPx(density, scaledDensity)
        return (screenHeightPx - bottom - h).coerceIn(0, screenHeightPx)
    }

    /** @deprecated 布局已改为 wrap；保留给旧调用估算高度。 */
    fun chromeBandHeightPx(
        screenHeightPx: Int,
        density: Float = 3f,
        scaledDensity: Float = density,
    ): Int {
        if (screenHeightPx <= 0) return 0
        return estimatedChromeContentHeightPx(density, scaledDensity)
            .coerceAtMost(screenHeightPx - chromeBottomOffsetPx(screenHeightPx))
            .coerceAtLeast(1)
    }

    /** 歌曲信息行最大宽度（像素），相对整带可用宽居中截断。 */
    fun infoRowMaxWidthPx(areaWidthPx: Int): Int {
        if (areaWidthPx <= 0) return 0
        return (areaWidthPx * INFO_ROW_MAX_WIDTH_FRACTION).toInt().coerceAtLeast(1)
    }

    /** 按钮行最大宽度（像素）。 */
    fun controlsRowMaxWidthPx(areaWidthPx: Int): Int {
        if (areaWidthPx <= 0) return 0
        return (areaWidthPx * CONTROLS_ROW_MAX_WIDTH_FRACTION).toInt().coerceAtLeast(1)
    }

    /**
     * 普通歌词底边锚点上限（% 屏高）：不得超过控件带玻璃上沿。
     * @param screenHeightPx 屏高；未知时用估算屏高仍给出合理上限
     */
    fun lyricBottomAnchorMaxPercent(
        screenHeightPx: Int,
        density: Float = 3f,
        scaledDensity: Float = density,
    ): Float {
        val h = screenHeightPx.coerceAtLeast(1)
        val topY = chromeBandTopYPx(h, density, scaledDensity)
        return (topY * 100f / h).coerceIn(40f, 95f)
    }

    /** 无屏高时的兼容入口（按常见全高估算）。 */
    fun lyricBottomAnchorMaxPercent(): Float =
        lyricBottomAnchorMaxPercent(screenHeightPx = 2400, density = 3f, scaledDensity = 3f)

    fun displayArtist(artist: String?): String =
        artist?.trim()?.takeIf { it.isNotEmpty() } ?: "未知艺人"

    /**
     * 按歌名括号设置拆分主/副标题。
     * @return Triple(mainTitle, subtitleOrEmpty, showSubtitleLine)
     */
    fun resolveTitleDisplay(
        rawTitle: String?,
        bracketMode: String,
    ): Triple<String, String, Boolean> {
        val raw = rawTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "未知歌曲"
        val (main, sub) = TitleBracketHelper.splitBrackets(raw)
        return when (bracketMode) {
            "hide" -> {
                val t = main.ifEmpty { raw }
                Triple(t, "", false)
            }
            "line" -> {
                if (sub.isEmpty()) Triple(main.ifEmpty { raw }, "", false)
                else Triple(main.ifEmpty { raw }, sub, true)
            }
            "shrink" -> {
                // 主/副拆开，由 View 拼 Spannable；不单独占副标题行
                if (sub.isEmpty()) Triple(main.ifEmpty { raw }, "", false)
                else Triple(main.ifEmpty { raw }, sub, false)
            }
            else -> Triple(raw, "", false)
        }
    }

    /** shrink 模式是否应对括号段做 RelativeSizeSpan。 */
    fun shouldApplyInlineShrink(bracketMode: String, subtitle: String): Boolean =
        bracketMode == "shrink" && subtitle.isNotEmpty()

    fun isPlaying(playbackState: Int?): Boolean =
        playbackState == PlaybackState.STATE_PLAYING ||
            playbackState == PlaybackState.STATE_BUFFERING ||
            playbackState == PlaybackState.STATE_CONNECTING

    fun nextShowLyric(currentlyShowing: Boolean): Boolean = !currentlyShowing

    fun lyricButtonAlpha(featureEnabled: Boolean, showing: Boolean): Float = when {
        !featureEnabled -> 0.28f
        showing -> 1f
        else -> 0.45f
    }
}
