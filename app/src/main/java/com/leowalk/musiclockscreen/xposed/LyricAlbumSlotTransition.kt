package com.leowalk.musiclockscreen.xposed

/**
 * 锁屏歌词 ↔ 方形专辑 交叉淡入淡出 / AOD 电源切换钉住参数。
 */
internal object LyricAlbumSlotTransition {

    /** 与 [LyricMotionPolicy.SLOT_CROSSFADE_MS] 对齐（AMLL 弹簧 settle） */
    val CROSSFADE_MS: Long get() = LyricMotionPolicy.SLOT_CROSSFADE_MS

    /** 播放态丢失后的短暂保持（Session 滞后 / AOD 联动窗口）。 */
    const val PLAYBACK_HOLD_MS = 1800L

    /**
     * 锁屏↔AOD 电源切换后额外钉住歌词可见性的时长。
     * 比 linkage（1.5s）略长，覆盖 overlay / Session 在联动结束后的尾抖。
     */
    const val AOD_VISIBILITY_PIN_MS = 2800L

    /** 切歌后优先占歌词位的最长时间；网络源歌词可更晚到达，门闩另见 [TrackLyricGate.WAIT_TIMEOUT_MS]。 */
    const val PREFER_LYRIC_WAIT_MS = 2500L

    /**
     * 等词超时：退出 WAITING → IDLE，保留切歌快照供迟到 lyric_fd。
     * 若只清 [preferLyricUntilResolved] 而留在 WAITING，[LyricAlbumPriorityPolicy] 会一直藏专辑。
     */
    fun trackGatePhaseAfterPreferWaitTimeout(
        phase: TrackLyricGate.Phase,
    ): TrackLyricGate.Phase {
        return if (phase == TrackLyricGate.Phase.WAITING) {
            TrackLyricGate.Phase.IDLE
        } else {
            phase
        }
    }

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

    /**
     * 可见性刷新后是否还要强制 [MusicLockscreenManager.showAlbumOverlay]。
     * 歌词占槽时禁止：否则会把正在淡出的专辑 alpha snap 回 1。
     */
    fun shouldForceShowAlbumAfterVisibilityUpdate(
        lyricPriorityOverAlbum: Boolean,
    ): Boolean = !lyricPriorityOverAlbum

    /**
     * [View.setVisibility] 副作用是否应跑：同值重入 / 内部静默设值时禁止，
     * 否则 VISIBLE→refreshNow→updateVisibilityState→再设 VISIBLE 会 StackOverflow 拖死 SystemUI。
     */
    fun shouldRunSetVisibilitySideEffects(
        previousVisibility: Int,
        newVisibility: Int,
        suppressingSideEffects: Boolean,
    ): Boolean {
        if (suppressingSideEffects) return false
        return previousVisibility != newVisibility
    }
}
