package com.leowalk.musiclockscreen.xposed

/**
 * 锁屏歌词 ↔ 方形专辑 交叉淡入淡出参数。
 */
internal object LyricAlbumSlotTransition {

    const val CROSSFADE_MS = 220L

    /** 播放态丢失后的短暂保持（Session 滞后 / AOD 联动窗口）。 */
    const val PLAYBACK_HOLD_MS = 1800L

    /** 切歌后优先占歌词位、等待首句的最长时间。 */
    const val PREFER_LYRIC_WAIT_MS = TrackLyricGate.WAIT_TIMEOUT_MS

    /**
     * 是否仍应按「播放中」对待歌词显示。
     * 确认暂停立即 false；电源联动窗口或 hold 期内有词则保持，避免闪灭。
     */
    fun isPlaybackOkForLyricSlot(
        isPlaying: Boolean,
        confirmedPaused: Boolean,
        nowElapsedMs: Long,
        playbackHoldUntilMs: Long,
        inScreenPowerTransition: Boolean,
        musicLockscreenActive: Boolean,
        onKeyguard: Boolean,
        hasLyricData: Boolean,
        hasDisplayableText: Boolean,
    ): Boolean {
        if (isPlaying) return true
        if (confirmedPaused) return false
        if (!musicLockscreenActive || !onKeyguard) return false
        if (!hasLyricData || !hasDisplayableText) return false
        if (inScreenPowerTransition) return true
        return nowElapsedMs < playbackHoldUntilMs
    }
}
