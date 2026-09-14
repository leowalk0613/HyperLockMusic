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

    /** 歌名旁小封面：左封面、右三行文案；封面边长=三行槽位总高。 */
    const val INFO_TITLE_SP = 16f
    const val INFO_ARTIST_SP = 11f
    const val INFO_SUBTITLE_SP = 10f
    const val INFO_TITLE_ARTIST_GAP_DP = 2
    const val INFO_SUBTITLE_GAP_DP = 1
    const val INFO_ALBUM_CORNER_DP = 5f
    const val INFO_ALBUM_GAP_DP = 10
    const val INFO_ALBUM_ELEVATION_DP = 8f

    /**
     * 行槽相对字号的倍率：MiSans 实际字形常高于 sp，槽位略高避免裁切。
     */
    const val INFO_LINE_HEIGHT_FACTOR = 1.28f

    /**
     * 歌曲信息竖直度量：三行（含间距）总高 == 小封面边长，文字不得超过封面。
     * 副标题显隐时仍预留副标题槽，保证封面尺寸稳定。
     */
    data class InfoTextMetrics(
        val albumSizePx: Int,
        val titleHeightPx: Int,
        val subtitleGapPx: Int,
        val subtitleHeightPx: Int,
        val titleArtistGapPx: Int,
        val artistHeightPx: Int,
    ) {
        /** 副标题行槽（间距+行高），GONE 时也占位。 */
        val subtitleSlotPx: Int get() = subtitleGapPx + subtitleHeightPx

        fun textBlockHeightPx(): Int =
            titleHeightPx + subtitleSlotPx + titleArtistGapPx + artistHeightPx
    }

    /** 单行槽高：字号 × 倍率，保证字形完整落在槽内。 */
    fun infoLineSlotHeightPx(sp: Float, density: Float, scaledDensity: Float): Int {
        val d = density.coerceAtLeast(0.01f)
        val sd = scaledDensity.coerceAtLeast(0.01f)
        val fromSp = (sp * sd * INFO_LINE_HEIGHT_FACTOR).toInt()
        val floor = (sp * d * 0.9f).toInt().coerceAtLeast(1)
        return fromSp.coerceAtLeast(floor)
    }

    fun infoTextMetrics(
        density: Float,
        scaledDensity: Float,
    ): InfoTextMetrics {
        val d = density.coerceAtLeast(0.01f)
        val titleH = infoLineSlotHeightPx(INFO_TITLE_SP, density, scaledDensity)
        val artistH = infoLineSlotHeightPx(INFO_ARTIST_SP, density, scaledDensity)
        val subtitleH = infoLineSlotHeightPx(INFO_SUBTITLE_SP, density, scaledDensity)
        val subtitleGap = (INFO_SUBTITLE_GAP_DP * d).toInt().coerceAtLeast(0)
        val titleArtistGap = (INFO_TITLE_ARTIST_GAP_DP * d).toInt().coerceAtLeast(0)
        val album = (titleH + subtitleGap + subtitleH + titleArtistGap + artistH)
            .coerceAtLeast((32f * d).toInt())
        return InfoTextMetrics(
            albumSizePx = album,
            titleHeightPx = titleH,
            subtitleGapPx = subtitleGap,
            subtitleHeightPx = subtitleH,
            titleArtistGapPx = titleArtistGap,
            artistHeightPx = artistH,
        )
    }

    /**
     * 小封面边长：与 [infoTextMetrics] 三行总高一致。
     */
    fun infoAlbumArtSizePx(
        density: Float,
        scaledDensity: Float,
        @Suppress("UNUSED_PARAMETER") includeSubtitle: Boolean = true,
    ): Int = infoTextMetrics(density, scaledDensity).albumSizePx

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

    /**
     * 歌名/副标题/歌手可用最大宽：信息行上限减去左侧小封面与间距。
     * 文字本身 wrap，整块（封面+文字）在行内水平居中。
     */
    fun infoTextMaxWidthPx(
        infoRowMaxPx: Int,
        albumArtWidthPx: Int,
        albumGapPx: Int,
        minTextPx: Int = 1,
    ): Int {
        if (infoRowMaxPx <= 0) return 0
        val album = albumArtWidthPx.coerceAtLeast(0)
        val gap = if (album > 0) albumGapPx.coerceAtLeast(0) else 0
        return (infoRowMaxPx - album - gap).coerceAtLeast(minTextPx.coerceAtLeast(1))
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
