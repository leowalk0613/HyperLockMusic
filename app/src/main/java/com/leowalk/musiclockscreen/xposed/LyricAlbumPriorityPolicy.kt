package com.leowalk.musiclockscreen.xposed

/**
 * 歌词开关开启时的方形专辑占位策略：
 * - 播放中：有词 / 等待首句 / 切歌未确认无词 → 优先占专辑位
 * - 暂停：立刻让出专辑
 * - 确认无词：显示专辑
 */
internal object LyricAlbumPriorityPolicy {

    fun shouldHideSquareAlbum(
        showLyricEnabled: Boolean,
        musicLockscreenActive: Boolean,
        onKeyguard: Boolean,
        isPlaying: Boolean,
        lyricCurrentlyDisplayed: Boolean,
        trackGatePhase: TrackLyricGate.Phase,
        hasLyricData: Boolean,
        hasDisplayableText: Boolean,
        preferLyricUntilResolved: Boolean = false,
    ): Boolean {
        if (!showLyricEnabled || !musicLockscreenActive || !onKeyguard) return false
        // 暂停：专辑；播放态由调用方含电源切换 sticky
        if (!isPlaying) return false
        if (lyricCurrentlyDisplayed) return true
        if (hasLyricData && hasDisplayableText) return true
        if (trackGatePhase == TrackLyricGate.Phase.WAITING) return true
        if (preferLyricUntilResolved) return true
        return false
    }
}
