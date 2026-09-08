package com.leowalk.musiclockscreen.xposed

/**
 * 解锁 / 通知中心 / 控制中心与「隐藏普通通知」的时机。
 *
 * 反编译对照（HyperOS SystemUI）：
 * - [KeyguardViewMediatorInjector.keyguardUnlockHide]：解锁时常已是 STATUS_SHADE，
 *   早于 KeyguardManager 解锁；SHADE 上应立刻 release，勿再等 KM。
 * - [ControlCenterImpl] 经 [ShadeWrapper.OnExpandChangedListener] 展开，
 *   **不**改 StatusBarState；音乐锁屏下仍 KEYGUARD，若继续 onLayout 过滤会与动画抢帧。
 * - 面板临时展开时应停主动 hide，但勿 release（行保持 GONE，收回面板后再 hide）。
 */
internal object NotificationReleasePolicy {

    /**
     * 是否应在 layout / StackHook 上主动藏普通通知。
     * 通知中心或控制中心展开时必须为 false，否则会与展开动画打架掉帧。
     */
    fun shouldActivelyHideNotifications(
        musicWallpaperShowing: Boolean,
        onKeyguard: Boolean,
        notificationShadeOpen: Boolean,
        controlCenterOpen: Boolean,
    ): Boolean {
        if (!musicWallpaperShowing || !onKeyguard) return false
        if (notificationShadeOpen || controlCenterOpen) return false
        return true
    }

    /**
     * 进入 STATUS_SHADE 时是否应立刻交还通知栈（解锁竞态 + 通知中心需要可见行）。
     */
    fun shouldReleaseOnStatusShade(moduleHidden: Boolean): Boolean = moduleHidden

    /**
     * 过滤条件已失效但仍标记 hidden 时是否 release。
     * [temporaryPanelOpen]：仍在音乐锁屏 keyguard 上，仅 shade/CC 临时展开 → 不清 GONE。
     */
    fun shouldReleaseWhenFilterInactive(
        shouldFilter: Boolean,
        moduleHidden: Boolean,
        temporaryPanelOpen: Boolean = false,
    ): Boolean {
        if (!moduleHidden) return false
        if (temporaryPanelOpen) return false
        return !shouldFilter
    }
}
