package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

/**
 * 锁屏勿扰/通知数量状态控制器
 *
 * 「勿扰 | N 个通知」仅应在**普通锁屏**显示；
 * **音乐锁屏**与**通知中心**（含 OS4 锁屏上 STATUS_SHADE）一律隐藏。
 */
object NumStateViewController {

    private const val tag = "HyperLockMusic_NumState"
    private const val FADE_MS = 260L
    private val easeOut by lazy { PathInterpolator(0f, 0f, 0.2f, 1f) }

    private var keyguardRoot: ViewGroup? = null
    private val trackedViews = LinkedHashSet<View>()
    private val originalVisibility = HashMap<View, Int>()
    private var isHidden: Boolean = false

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        syncVisibility()
    }

    fun bindKeyguardRoot(root: ViewGroup?) {
        keyguardRoot = root
        if (root != null) {
            rescanAndRegister()
            syncVisibility()
        }
    }

    /** 按当前锁屏模式决定显示或隐藏。 */
    fun syncVisibility() {
        if (LockscreenNotificationController.shouldShowNumState()) {
            show()
        } else {
            hide()
        }
    }

    /** @deprecated 请用 [syncVisibility] */
    fun reapplyIfNeeded() = syncVisibility()

    fun rescanAndRegister() {
        val root = keyguardRoot ?: return
        val found = LinkedHashSet<View>()
        HookUtils.findAllViewsByIdName(root, "num_state_view").forEach { found.add(it) }
        HookUtils.findAllViewsByClassName(root, "NotificationNumStateView").forEach { found.add(it) }

        var newly = 0
        for (view in found) {
            if (registerView(view)) newly++
        }
        if (newly > 0) {
            logI("registered $newly new num_state view(s), total=${trackedViews.size}")
        }
        if (!LockscreenNotificationController.shouldShowNumState()) {
            found.forEach { applyHideImmediate(it) }
        }
    }

    /** @return true 若为新注册 */
    private fun registerView(view: View): Boolean {
        if (view in trackedViews) return false
        val vis = view.visibility
        originalVisibility[view] = if (vis == View.GONE) View.VISIBLE else vis
        view.addOnLayoutChangeListener(layoutChangeListener)
        trackedViews.add(view)
        return true
    }

    fun hide() {
        if (isHidden) {
            // 已隐藏时只强制收起，避免每次 layout 全树扫描刷日志
            if (trackedViews.isEmpty()) {
                rescanAndRegister()
            } else {
                trackedViews.forEach { applyHideImmediate(it) }
            }
            return
        }
        isHidden = true
        rescanAndRegister()
        trackedViews.forEach { applyHideImmediate(it) }
    }

    fun show() {
        isHidden = false
        rescanAndRegister()
        try {
            for (view in trackedViews.toList()) {
                view.animate().cancel()
                view.visibility = View.VISIBLE
                view.alpha = 1f
                restoreNumStateWrapper(view)
            }
        } catch (e: Throwable) {
            logE("show error", e)
        }
    }

    fun isHidden(): Boolean = isHidden

    private fun applyHideImmediate(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.GONE
        hideNumStateWrapper(view)
    }

    private fun hideNumStateWrapper(view: View) {
        val parent = view.parent as? View ?: return
        val name = parent.javaClass.simpleName
        if (name.contains("NumState", ignoreCase = true) ||
            name.contains("num_state", ignoreCase = true)
        ) {
            parent.animate().cancel()
            parent.alpha = 0f
            parent.visibility = View.GONE
        }
    }

    private fun restoreNumStateWrapper(view: View) {
        val parent = view.parent as? View ?: return
        val name = parent.javaClass.simpleName
        if (name.contains("NumState", ignoreCase = true) ||
            name.contains("num_state", ignoreCase = true)
        ) {
            parent.animate().cancel()
            parent.visibility = View.VISIBLE
            parent.alpha = 1f
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
