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

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    /** 音乐锁屏激活且仍在锁屏界面时才过滤普通通知（OS4：锁屏=通知中心，不区分 shade） */
    fun shouldFilterNotifications(): Boolean {
        return WallpaperController.isShowing() && isOnKeyguard()
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
        if (!isHidden || !WallpaperController.isShowing() || !isOnKeyguard()) {
            return@OnLayoutChangeListener
        }
        val stack = notificationStackView ?: return@OnLayoutChangeListener
        stack.post {
            if (!WallpaperController.isShowing()) return@post
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
            if (needRehide) doHide()
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
