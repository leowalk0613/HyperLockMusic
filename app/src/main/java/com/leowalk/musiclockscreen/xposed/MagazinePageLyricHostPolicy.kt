package com.leowalk.musiclockscreen.xposed

/**
 * 画报右划页挂载 [LockscreenLyricView] 时的显示门闩（无 SystemUI 通知中心 / 壁纸激活态）。
 */
internal object MagazinePageLyricHostPolicy {

    /** 进度回弹时避免切行索引 A↔B 抽搐。 */
    const val LINE_INDEX_HYSTERESIS_MS = 120L

    fun shouldDisplay(
        lyricEnabled: Boolean,
        showLyric: Boolean,
        hasLyric: Boolean,
        playbackOk: Boolean,
        hasDisplayableText: Boolean,
    ): Boolean {
        if (!LyricDisplayPolicy.shouldShowLyric(lyricEnabled, showLyric)) return false
        if (!hasLyric || !hasDisplayableText) return false
        return playbackOk
    }

    /** 已可见时钉住，禁止反复 springFadeIn（alpha=0 重启）造成闪动。 */
    fun shouldSnapKeepVisible(
        visibilityVisible: Boolean,
        hasDisplayableText: Boolean,
    ): Boolean = visibilityVisible && hasDisplayableText

    /** 仅在几乎透明或未显示时才走表面淡入。 */
    fun shouldRestartSurfaceFadeIn(
        visibilityVisible: Boolean,
        alpha: Float,
    ): Boolean {
        if (!visibilityVisible) return true
        return alpha < 0.2f
    }

    /**
     * 画报「普通歌词」底边：与设置滑条一致；上限为控件带玻璃上沿。
     */
    fun bottomAnchorYPercent(
        lyricBgAnchorY: Float,
        maxPercent: Float = 95f,
    ): Float {
        val max = maxPercent.coerceIn(10f, 95f)
        return lyricBgAnchorY.coerceIn(10f, max)
    }

    /**
     * 画报「沉浸歌词」底边：对标普通锁屏沉浸，跟画报专辑锚点，全屏 10–95%。
     */
    fun immersiveBottomAnchorYPercent(albumAnchorY: Float): Float =
        albumAnchorY.coerceIn(10f, 95f)

    fun resolveBottomAnchorYPercent(
        immersiveLyric: Boolean,
        lyricBgAnchorY: Float,
        albumAnchorY: Float,
        normalLyricMaxPercent: Float = 95f,
    ): Float =
        if (immersiveLyric) {
            immersiveBottomAnchorYPercent(albumAnchorY)
        } else {
            bottomAnchorYPercent(lyricBgAnchorY, normalLyricMaxPercent)
        }

    /**
     * 稳定切行索引：前进立即；回退需进度明显早于当前行起点，抑制 Session 进度回弹。
     */
    fun stabilizeLineIndex(
        rawIndex: Int,
        heldIndex: Int,
        posMs: Long,
        lineTimeMsAt: (Int) -> Long,
        lineCount: Int,
        hysteresisMs: Long = LINE_INDEX_HYSTERESIS_MS,
    ): Int {
        if (lineCount <= 0) return -1
        val raw = rawIndex.coerceIn(-1, lineCount - 1)
        if (raw < 0) return heldIndex.coerceIn(-1, lineCount - 1)
        if (heldIndex < 0 || heldIndex >= lineCount) return raw
        if (raw == heldIndex) return heldIndex
        if (raw > heldIndex) return raw
        val heldTime = lineTimeMsAt(heldIndex)
        return if (posMs + hysteresisMs < heldTime) raw else heldIndex
    }
}
