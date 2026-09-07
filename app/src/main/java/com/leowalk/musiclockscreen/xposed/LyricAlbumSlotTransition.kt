package com.leowalk.musiclockscreen.xposed

/**
 * 锁屏歌词 ↔ 方形专辑 交叉淡入淡出 / AOD 电源切换钉住参数。
 */
internal object LyricAlbumSlotTransition {

    const val CROSSFADE_MS = 220L

    /** 播放态丢失后的短暂保持（Session 滞后 / AOD 联动窗口）。 */
    const val PLAYBACK_HOLD_MS = 1800L

    /**
     * 锁屏↔AOD 电源切换后额外钉住歌词可见性的时长。
     * 比 linkage（1.5s）略长，覆盖 overlay / Session 在联动结束后的尾抖。
     */
    const val AOD_VISIBILITY_PIN_MS = 2800L

    /** 切歌后优先占歌词位、等待首句的最长时间。 */
    const val PREFER_LYRIC_WAIT_MS = TrackLyricGate.WAIT_TIMEOUT_MS

    /**
     * 是否仍应按「播放中」对待歌词显示。
     * 电源联动窗口内忽略假暂停；确认暂停仅在非联动时立刻收起。
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
        if (!musicLockscreenActive || !onKeyguard) return false
        if (!hasLyricData || !hasDisplayableText) return false
        // 息屏/亮屏联动期 Session 常误报 PAUSED：有词则保持
        if (inScreenPowerTransition) return true
        if (confirmedPaused) return false
        return nowElapsedMs < playbackHoldUntilMs
    }

    /**
     * 锁屏↔AOD 切换窗口：已有可显示歌词时钉住 overlay，避免
     * shouldShowKeyguardOverlays / 播放态抖动导致「消失再出现」。
     * 钉住期内忽略 Session 假暂停。
     */
    fun shouldPinLyricVisibility(
        nowElapsedMs: Long,
        pinUntilMs: Long,
        showLyricEnabled: Boolean,
        musicLockscreenActive: Boolean,
        onKeyguard: Boolean,
        hasLyricData: Boolean,
        hasDisplayableText: Boolean,
        confirmedPaused: Boolean,
        inScreenPowerTransition: Boolean,
    ): Boolean {
        if (!showLyricEnabled || !musicLockscreenActive || !onKeyguard) return false
        if (!hasLyricData || !hasDisplayableText) return false
        if (inScreenPowerTransition) return true
        val pinAlive = pinUntilMs > 0L && nowElapsedMs < pinUntilMs
        if (pinAlive) return true
        return false
    }

    /** 电源切换中已有可见歌词时，禁止走淡出再淡入。 */
    fun shouldSnapKeepVisible(
        alreadyVisible: Boolean,
        pinActive: Boolean,
        hasDisplayableText: Boolean,
    ): Boolean {
        return pinActive && alreadyVisible && hasDisplayableText
    }
}
