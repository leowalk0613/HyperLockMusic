package com.leowalk.musiclockscreen.xposed

/**
 * 歌词接收侧：LyricFocus 已写入 Provider 后，锁屏如何接受/拒绝/上屏。
 * 禁止把「空标题」当成同曲，否则会把上一首 ctx 合并进新轻量包。
 */
internal object LyricReceivePolicy {

    /** 两边标题都有且匹配才算确认同曲。 */
    fun titlesConfirmedSame(a: String, b: String): Boolean {
        val left = a.trim()
        val right = b.trim()
        if (left.isEmpty() || right.isEmpty()) return false
        return TrackLyricGate.titlesMatch(left, right)
    }

    /**
     * 轻量包缺 ctx 时是否可并入上一包全量时间轴。
     * 切歌等待中、标题未确认同曲：一律不合并。
     */
    fun shouldMergePreviousCtx(
        incomingHasCtx: Boolean,
        previousHasCtx: Boolean,
        titlesConfirmedSame: Boolean,
        waitingForNewTrack: Boolean,
    ): Boolean {
        if (waitingForNewTrack) return false
        if (incomingHasCtx || !previousHasCtx) return false
        return titlesConfirmedSame
    }

    /**
     * 轻量焦点行对不上缓存时间轴：时间轴属于上一首，应丢掉改用轻量行。
     * 焦点能对上则保留（已是本曲全量）。
     */
    fun shouldDropCachedTimeline(
        lightMain: String,
        cachedLineCount: Int,
        lightMatchesCachedLine: Boolean,
        waitingForNewTrack: Boolean,
    ): Boolean {
        if (cachedLineCount <= 0) return false
        if (lightMatchesCachedLine) return false
        if (waitingForNewTrack) return true
        return lightMain.trim().isNotEmpty()
    }

    /** Session 歌名变化（双方非空且不匹配）视为切歌，不依赖 trackKey。 */
    fun shouldResetForMediaTitleChange(previousMediaTitle: String, newMediaTitle: String): Boolean {
        val prev = previousMediaTitle.trim()
        val next = newMediaTitle.trim()
        if (prev.isEmpty() || next.isEmpty()) return false
        return !TrackLyricGate.titlesMatch(prev, next)
    }
}
