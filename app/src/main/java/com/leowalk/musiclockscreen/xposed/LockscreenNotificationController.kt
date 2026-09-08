package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup

/**
 * 锁屏通知控制器（HyperOS 4）
 *
 * 隐藏策略对齐 SystemUI StatusBarState + ViewState.gone（见 [NotificationReleasePolicy]）。
 * 仅隐藏非媒体 [ExpandableNotificationRow]；[MiuiMediaHeaderView] 保持可见。
 */
object LockscreenNotificationController {

    private const val tag = "HyperLockMusic_NotifCtrl"

    private var notificationStackView: ViewGroup? = null
    private var phase: NotificationReleasePolicy.HidePhase = NotificationReleasePolicy.HidePhase.IDLE
    private var statusBarState: Int = NotificationReleasePolicy.STATUS_KEYGUARD
    private var controlCenterOpen: Boolean = false
    /** 兼容 NumState / overlay：STATUS_SHADE 时为 true。 */
    private var notificationShadeOpen: Boolean = false

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun setStatusBarState(state: Int) {
        statusBarState = state
        notificationShadeOpen =
            state == NotificationReleasePolicy.STATUS_SHADE ||
                state == NotificationReleasePolicy.STATUS_SHADE_LOCKED
    }

    fun setNotificationShadeOpen(open: Boolean) {
        if (notificationShadeOpen == open) return
        notificationShadeOpen = open
        NumStateViewController.syncVisibility()
        if (WallpaperController.isShowing() && isKeyguardUi()) {
            syncKeyguardOverlayVisibility()
        }
    }

    fun setControlCenterOpen(open: Boolean) {
        if (controlCenterOpen == open) return
        controlCenterOpen = open
        phase = NotificationReleasePolicy.phaseAfterControlCenter(
            expanded = open,
            musicWallpaperShowing = WallpaperController.isShowing(),
            statusBarState = statusBarState,
            current = phase,
        )
        NumStateViewController.syncVisibility()
        if (WallpaperController.isShowing() && isKeyguardUi()) {
            syncKeyguardOverlayVisibility()
        }
    }

    fun isNotificationShadeOpen(): Boolean = notificationShadeOpen

    fun isControlCenterOpen(): Boolean = controlCenterOpen

    fun isTemporaryPanelOpen(): Boolean =
        controlCenterOpen ||
            statusBarState == NotificationReleasePolicy.STATUS_SHADE ||
            statusBarState == NotificationReleasePolicy.STATUS_SHADE_LOCKED

    fun shouldFilterNotifications(): Boolean {
        return NotificationReleasePolicy.shouldActivelyHideNotifications(
            musicWallpaperShowing = WallpaperController.isShowing(),
            statusBarState = statusBarState,
            controlCenterOpen = controlCenterOpen,
        )
    }

    fun isNotificationListVisible(): Boolean {
        if (!isKeyguardUi()) return false
        val stack = notificationStackView ?: return false
        for (i in 0 until stack.childCount) {
            val child = stack.getChildAt(i)
            if (NotificationStackChildClassifier.isMiuiMediaHeaderView(child)) continue
            if (!NotificationStackChildClassifier.isExpandableNotificationRow(child)) continue
            if (SystemNotificationAnimator.isHidden(child)) continue
            if (child.visibility == View.VISIBLE &&
                child.alpha > 0.05f &&
                child.scaleY > 0.05f
            ) {
                return true
            }
        }
        return false
    }

    fun shouldShowNumState(): Boolean {
        if (!isKeyguardUi()) return false
        if (WallpaperController.isShowing()) return false
        if (isTemporaryPanelOpen()) return false
        return true
    }

    fun isNotificationCenterVisible(): Boolean {
        return shouldFilterNotifications() && isNotificationListVisible()
    }

    fun shouldShowKeyguardOverlays(): Boolean {
        return shouldFilterNotifications() && !isNotificationCenterVisible()
    }

    fun syncKeyguardOverlayVisibility() {
        val shouldShow = shouldShowKeyguardOverlays()
        if (!KeyguardOverlayVisibilitySync.shouldApply(shouldShow)) return
        if (shouldShow) {
            MusicLockscreenManager.resumeAlbumOverlay()
        } else if (WallpaperController.isShowing() && isKeyguardUi()) {
            MusicLockscreenManager.pauseAlbumOverlay()
        }
        (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.refreshVisibility()
        LockscreenClockController.sync()
    }

    fun setNotificationStackView(view: ViewGroup?) {
        notificationStackView?.removeOnLayoutChangeListener(layoutChangeListener)
        notificationStackView?.setOnHierarchyChangeListener(null)
        notificationStackView = view
        if (view != null) {
            view.addOnLayoutChangeListener(layoutChangeListener)
            view.setOnHierarchyChangeListener(hierarchyChangeListener)
            // 仅退出音乐锁屏时交还；勿在 rebind 时因 phase=HIDDEN 误 release 闪一下
            if (!WallpaperController.isShowing() &&
                NotificationReleasePolicy.isIntervening(phase)
            ) {
                releaseToSystemUi()
            }
            MediaFollowController.bindMediaView(findMiuiMediaHeaderView())
        }
        logI("notificationStackView set: ${view != null}, childCount=${view?.childCount ?: 0}")
    }

    fun findMiuiMediaHeaderView(): View? {
        val stack = notificationStackView ?: return null
        for (i in 0 until stack.childCount) {
            val child = stack.getChildAt(i)
            if (NotificationStackChildClassifier.isMiuiMediaHeaderView(child)) {
                return child
            }
        }
        return null
    }

    private val hierarchyChangeListener = object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            if (child == null || !shouldFilterNotifications()) return
            if (NotificationStackChildClassifier.shouldHideNotificationRow(child) &&
                child.visibility == View.VISIBLE
            ) {
                SystemNotificationAnimator.hideImmediately(child)
                phase = NotificationReleasePolicy.phaseAfterHideApplied(controlCenterOpen)
            }
        }

        override fun onChildViewRemoved(parent: View?, child: View?) = Unit
    }

    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val stack = notificationStackView ?: return@OnLayoutChangeListener
        stack.post {
            if (isTemporaryPanelOpen()) return@post
            if (!shouldFilterNotifications()) {
                if (NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                        shouldFilter = false,
                        phase = phase,
                        temporaryPanelOpen = isTemporaryPanelOpen(),
                    )
                ) {
                    releaseToSystemUi()
                }
                return@post
            }
            // 仅当有 VISIBLE 应藏行时再 hide（ViewState 偶发打回时的安全网）
            var needRehide = false
            for (i in 0 until stack.childCount) {
                val child = stack.getChildAt(i)
                if (NotificationStackChildClassifier.shouldHideNotificationRow(child) &&
                    child.visibility == View.VISIBLE
                ) {
                    needRehide = true
                    break
                }
            }
            if (needRehide) {
                doHide()
            }
        }
    }

    /** UI 是否处于锁屏态（优先 StatusBarState，避免 KM 滞后）。 */
    private fun isKeyguardUi(): Boolean {
        if (statusBarState == NotificationReleasePolicy.STATUS_KEYGUARD) return true
        if (statusBarState == NotificationReleasePolicy.STATUS_SHADE ||
            statusBarState == NotificationReleasePolicy.STATUS_SHADE_LOCKED
        ) {
            return false
        }
        return try {
            val stack = notificationStackView ?: return false
            val km = stack.context.getSystemService(android.app.KeyguardManager::class.java)
            km?.isKeyguardLocked == true
        } catch (_: Throwable) {
            false
        }
    }

    fun forceHideNormalNotifications() {
        if (!WallpaperController.isShowing()) {
            logI("skip hide: music wallpaper not active")
            return
        }
        if (!shouldFilterNotifications()) {
            logI("skip hide: not clean music keyguard (state=$statusBarState cc=$controlCenterOpen)")
            return
        }
        doHide()
    }

    private fun doHide() {
        try {
            val stack = notificationStackView
            if (stack == null) {
                logE("doHide failed: notificationStackView is null")
                return
            }

            var hiddenCount = 0
            var keptCount = 0
            for (i in 0 until stack.childCount) {
                val child = stack.getChildAt(i)
                when {
                    NotificationStackChildClassifier.isMiuiMediaHeaderView(child) -> {
                        // 媒体 header 交 SystemUI；勿每帧 ensureVisible
                        keptCount++
                    }
                    NotificationStackChildClassifier.shouldHideNotificationRow(child) -> {
                        // 只藏 VISIBLE；已 GONE 的交给 SystemUI（锁屏敏感/静音等）
                        if (child.visibility == View.VISIBLE) {
                            SystemNotificationAnimator.hideImmediately(child)
                            hiddenCount++
                        }
                    }
                    NotificationStackChildClassifier.isExpandableNotificationRow(child) -> {
                        keptCount++
                    }
                }
            }
            phase = NotificationReleasePolicy.phaseAfterHideApplied(controlCenterOpen)
            logI("hidden $hiddenCount rows, kept $keptCount, phase=$phase")
            MediaFollowController.bindMediaView(findMiuiMediaHeaderView())
            syncKeyguardOverlayVisibility()
            NumStateViewController.syncVisibility()
        } catch (e: Throwable) {
            logE("doHide error", e)
        }
    }

    /**
     * 解锁 / 通知中心 / 退出音乐锁屏：撤销干预，恢复 SystemUI 默认行为。
     */
    fun releaseToSystemUi() {
        try {
            val stack = notificationStackView
            if (stack == null) {
                logE("release failed: notificationStackView is null")
                phase = NotificationReleasePolicy.HidePhase.IDLE
                SystemNotificationAnimator.reset()
                return
            }

            phase = NotificationReleasePolicy.phaseAfterRelease(WallpaperController.isShowing())
            // 只还原本模块标记的行；绝不强行 VISIBLE 掉 SystemUI 自己的锁屏隐藏行
            val restoredRows = SystemNotificationAnimator.releaseMarkedInStack(stack)
            SystemNotificationAnimator.scheduleReleaseSweep(stack)
            logI("released to SystemUI, restored $restoredRows row(s), phase=$phase")
            KeyguardOverlayVisibilitySync.reset()
            syncKeyguardOverlayVisibility()
            NumStateViewController.syncVisibility()
        } catch (e: Throwable) {
            logE("releaseToSystemUi error", e)
            phase = NotificationReleasePolicy.HidePhase.IDLE
        }
    }

    fun isHidden(): Boolean = NotificationReleasePolicy.isIntervening(phase)

    internal fun hidePhase(): NotificationReleasePolicy.HidePhase = phase

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
            ?: android.util.Log.i(tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
            ?: if (e != null) android.util.Log.e(tag, msg, e) else android.util.Log.e(tag, msg)
    }
}
