package com.leowalk.musiclockscreen.xposed

/**
 * 音乐锁屏「隐藏普通通知」状态机与时机（对齐 HyperOS SystemUI 反编译）。
 *
 * StatusBarState：0 SHADE / 1 KEYGUARD / 2 SHADE_LOCKED。
 * - 干净音乐 KEYGUARD：主动 GONE 非媒体行，并尽量同步 ViewState.gone（否则 applyToView 会打回 VISIBLE）。
 * - SHADE / SHADE_LOCKED：通知中心或解锁路径，必须 release（SHADE 常早于 KM 解锁）。
 * - 控制中心：不改 StatusBarState，停过滤但保持 GONE，勿 release。
 */
internal object NotificationReleasePolicy {

    const val STATUS_SHADE = 0
    const val STATUS_KEYGUARD = 1
    const val STATUS_SHADE_LOCKED = 2

    enum class HidePhase {
        /** 未干预 */
        IDLE,
        /** 干净音乐锁屏，行已 GONE */
        HIDDEN,
        /** 控制中心展开：保持 GONE，不做 layout 过滤 */
        PANEL_CC,
        /** 已交还 SystemUI（NC / 解锁 / 退出音乐锁屏） */
        RELEASED,
    }

    /** 仅 KEYGUARD 且未开控制中心时主动藏通知。 */
    fun shouldActivelyHideNotifications(
        musicWallpaperShowing: Boolean,
        statusBarState: Int,
        controlCenterOpen: Boolean,
    ): Boolean {
        if (!musicWallpaperShowing) return false
        if (statusBarState != STATUS_KEYGUARD) return false
        if (controlCenterOpen) return false
        return true
    }

    fun shouldReleaseOnStatusBarState(
        newState: Int,
        phase: HidePhase,
    ): Boolean {
        if (phase != HidePhase.HIDDEN && phase != HidePhase.PANEL_CC) return false
        return newState == STATUS_SHADE || newState == STATUS_SHADE_LOCKED
    }

    fun phaseAfterStatusBarState(
        newState: Int,
        musicWallpaperShowing: Boolean,
        controlCenterOpen: Boolean,
        current: HidePhase,
    ): HidePhase {
        return when (newState) {
            STATUS_SHADE, STATUS_SHADE_LOCKED -> HidePhase.RELEASED
            STATUS_KEYGUARD -> when {
                !musicWallpaperShowing -> HidePhase.IDLE
                controlCenterOpen -> HidePhase.PANEL_CC
                current == HidePhase.HIDDEN || current == HidePhase.PANEL_CC -> HidePhase.HIDDEN
                else -> HidePhase.HIDDEN // 将执行 hide
            }
            else -> current
        }
    }

    fun phaseAfterControlCenter(
        expanded: Boolean,
        musicWallpaperShowing: Boolean,
        statusBarState: Int,
        current: HidePhase,
    ): HidePhase {
        if (!musicWallpaperShowing || statusBarState != STATUS_KEYGUARD) {
            return if (expanded) current else current
        }
        return if (expanded) {
            if (current == HidePhase.HIDDEN || current == HidePhase.PANEL_CC) HidePhase.PANEL_CC
            else current
        } else {
            if (current == HidePhase.PANEL_CC) HidePhase.HIDDEN else current
        }
    }

    fun phaseAfterHideApplied(controlCenterOpen: Boolean): HidePhase {
        return if (controlCenterOpen) HidePhase.PANEL_CC else HidePhase.HIDDEN
    }

    fun phaseAfterRelease(musicWallpaperShowing: Boolean): HidePhase {
        return if (musicWallpaperShowing) HidePhase.RELEASED else HidePhase.IDLE
    }

    /** 过滤失效时是否 release：临时 CC 面板保持 GONE。 */
    fun shouldReleaseWhenFilterInactive(
        shouldFilter: Boolean,
        phase: HidePhase,
        temporaryPanelOpen: Boolean,
    ): Boolean {
        if (phase != HidePhase.HIDDEN && phase != HidePhase.PANEL_CC) return false
        if (temporaryPanelOpen) return false
        return !shouldFilter
    }

    fun isIntervening(phase: HidePhase): Boolean {
        return phase == HidePhase.HIDDEN || phase == HidePhase.PANEL_CC
    }

    /** 只应对当前 VISIBLE 行执行模块隐藏（GONE/INVISIBLE 留给 SystemUI）。 */
    fun shouldHideVisibleRowOnly(visibility: Int): Boolean {
        return visibility == android.view.View.VISIBLE
    }
}
