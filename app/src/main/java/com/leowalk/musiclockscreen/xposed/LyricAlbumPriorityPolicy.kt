package com.leowalk.musiclockscreen.xposed

/**
 * 歌词开关开启时：有歌词优先占专辑位；切歌等待期暂藏专辑；确认无词后立刻让出专辑。
 */
internal object LyricAlbumPriorityPolicy {

    fun shouldHideSquareAlbum(
        showLyricEnabled: Boolean,
        musicLockscreenActive: Boolean,
        onKeyguard: Boolean,
        lyricCurrentlyDisplayed: Boolean,
        trackGatePhase: TrackLyricGate.Phase,
        hasLyricData: Boolean,
        hasDisplayableText: Boolean,
    ): Boolean {
        if (!showLyricEnabled || !musicLockscreenActive || !onKeyguard) return false
        if (lyricCurrentlyDisplayed) return true
        if (hasLyricData && hasDisplayableText) return true
        // 切歌等待中：暂藏专辑，避免旧封面抢先；超时/确认无词后 phase=IDLE 且无歌词 → 显示专辑
        if (trackGatePhase == TrackLyricGate.Phase.WAITING) return true
        return false
    }
}
