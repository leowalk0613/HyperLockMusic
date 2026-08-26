package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup

/**
 * 锁屏通知控制器（HyperOS 4）
 *
 * OS4 锁屏即通知中心，无「锁屏下拉通知中心」；仅在 [isOnKeyguard] 且音乐壁纸激活时隐藏普通通知。
 * 仅隐藏 [ExpandableNotificationRow] 中的非媒体行；
 * [MiuiMediaHeaderView] 及 SectionHeader/Footer 等永不 GONE。
 */
object LockscreenNotificationController {

    private const val tag = "HyperLockMusic_NotifCtrl"

    private var notificationStackView: ViewGroup? = null
    private var isHidden: Boolean = false
    /** OS4 锁屏上展开通知中心时 SystemUI 会切到 STATUS_SHADE（人仍可处于锁屏）。 */
    private var notificationShadeOpen: Boolean = false

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun setNotificationShadeOpen(open: Boolean) {
        if (notificationShadeOpen == open) return
        notificationShadeOpen = open
        NumStateViewController.syncVisibility()
        if (WallpaperController.isShowing() && isOnKeyguard()) {
            syncKeyguardOverlayVisibility()
        }
    }

    /** 音乐锁屏激活且仍在锁屏界面时才过滤普通通知（OS4：锁屏=通知中心，不区分 shade） */
    fun shouldFilterNotifications(): Boolean {
        return WallpaperController.isShowing() && isOnKeyguard()
    }

    /** 通知栈里是否有可见的普通通知行（通知列表正在展示）。 */
    fun isNotificationListVisible(): Boolean {
        if (!isOnKeyguard()) return false
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

    /**
     * 「勿扰 | N 个通知」仅出现在普通锁屏（非音乐锁屏、非通知中心 shade）。
     * 注意：OS4 普通锁屏上通知行默认可见，不能据此判定为通知中心。
     */
    fun shouldShowNumState(): Boolean {
        if (!isOnKeyguard()) return false
        if (WallpaperController.isShowing()) return false
        if (notificationShadeOpen) return false
        return true
    }

    /**
     * 通知列表里是否仍有普通通知行可见（= 通知中心界面）。
     * 音乐锁屏 overlay（大专辑/歌词）只应出现在隐藏通知后的干净锁屏，不应叠在通知中心上。
     */
    fun isNotificationCenterVisible(): Boolean {
        return shouldFilterNotifications() && isNotificationListVisible()
    }

    /** 干净锁屏（已隐藏普通通知）才显示大专辑/歌词 overlay。 */
    fun shouldShowKeyguardOverlays(): Boolean {
        return shouldFilterNotifications() && !isNotificationCenterVisible()
    }

    fun syncKeyguardOverlayVisibility() {
        if (shouldShowKeyguardOverlays()) {
            MusicLockscreenManager.resumeAlbumOverlay()
        } else if (WallpaperController.isShowing() && isOnKeyguard()) {
            MusicLockscreenManager.pauseAlbumOverlay()
        }
        (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.refreshVisibility()
    }

    fun setNotificationStackView(view: ViewGroup?) {
        notificationStackView?.removeOnLayoutChangeListener(layoutChangeListener)
        notificationStackView = view
        if (view != null) {
            view.addOnLayoutChangeListener(layoutChangeListener)
            if (!WallpaperController.isShowing()) {
                showAllNotifications()
            }
            MediaFollowController.bindMediaView(findMiuiMediaHeaderView())
        }
        logI("notificationStackView set: ${view != null}, childCount=${view?.childCount ?: 0}")
    }

    /** 锁屏媒体控件容器（MiuiMediaHeaderView） */
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

    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val stack = notificationStackView ?: return@OnLayoutChangeListener
        stack.post {
            if (shouldFilterNotifications()) {
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
                } else {
                    syncKeyguardOverlayVisibility()
                }
            }
            NumStateViewController.syncVisibility()
        }
    }

    private fun isOnKeyguard(): Boolean {
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
        if (!isOnKeyguard()) {
            logI("skip hide: not on keyguard")
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
                        ensureVisible(child)
                        keptCount++
                    }
                    NotificationStackChildClassifier.shouldHideNotificationRow(child) -> {
                        SystemNotificationAnimator.scheduleRemove(stack, child)
                        hiddenCount++
                    }
                    NotificationStackChildClassifier.isExpandableNotificationRow(child) -> {
                        ensureVisible(child)
                        keptCount++
                    }
                }
            }
            isHidden = true
            logI("hidden $hiddenCount rows, kept $keptCount (media header + media rows)")
            MediaFollowController.bindMediaView(findMiuiMediaHeaderView())
            syncKeyguardOverlayVisibility()
            NumStateViewController.syncVisibility()
        } catch (e: Throwable) {
            logE("doHide error", e)
        }
    }

    fun showAllNotifications() {
        try {
            val stack = notificationStackView
            if (stack == null) {
                logE("show failed: notificationStackView is null")
                isHidden = false
                return
            }

            SystemNotificationAnimator.reset()

            var restored = 0
            for (i in 0 until stack.childCount) {
                val child = stack.getChildAt(i)
                if (!NotificationStackChildClassifier.isMiuiMediaHeaderView(child) &&
                    !NotificationStackChildClassifier.isExpandableNotificationRow(child)
                ) {
                    continue
                }
                SystemNotificationAnimator.snapVisible(child)
                ensureVisible(child)
                restored++
            }
            isHidden = false
            logI("restored $restored rows + media header")
            syncKeyguardOverlayVisibility()
            NumStateViewController.syncVisibility()
        } catch (e: Throwable) {
            logE("showAllNotifications error", e)
            isHidden = false
        }
    }

    fun isHidden(): Boolean = isHidden

    private fun ensureVisible(view: View) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
            ?: android.util.Log.i(tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
            ?: if (e != null) android.util.Log.e(tag, msg, e) else android.util.Log.e(tag, msg)
    }
}
