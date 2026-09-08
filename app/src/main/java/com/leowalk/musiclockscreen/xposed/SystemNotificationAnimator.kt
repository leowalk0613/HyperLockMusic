package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup

/**
 * 通知行隐藏：立刻 GONE，并尽量同步 ExpandableView.ViewState.gone。
 *
 * 反编译 [ViewState.applyToView]：gone==true 时直接 return，不会把 visibility 打回 VISIBLE；
 * 若仅做 scaleY/alpha 动画而仍 VISIBLE，会与每帧 applyToView 打架并触发更多 onLayout。
 */
object SystemNotificationAnimator {

    private const val tag = "HyperLockMusic_SysAnim"

    private val hiddenByUs = HashSet<View>()

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun isHidden(row: View): Boolean {
        return row.visibility == View.GONE || hiddenByUs.contains(row)
    }

    fun scheduleRemove(stack: ViewGroup, row: View): Boolean {
        if (!LockscreenNotificationController.shouldFilterNotifications()) return false
        if (row.visibility == View.GONE) {
            markViewStateGone(row, true)
            hiddenByUs.add(row)
            return true
        }
        hideImmediately(row)
        return true
    }

    fun hideImmediately(row: View) {
        try {
            if (row.parent == null) return
            row.animate().cancel()
            row.scaleX = 1f
            row.scaleY = 1f
            row.alpha = 1f
            row.pivotY = 0f
            row.visibility = View.GONE
            markViewStateGone(row, true)
            hiddenByUs.add(row)
        } catch (e: Throwable) {
            logE("hideImmediately error", e)
            try {
                row.visibility = View.GONE
                hiddenByUs.add(row)
            } catch (_: Throwable) {
            }
        }
    }

    fun snapVisible(row: View) {
        try {
            if (row.parent == null) return
            hiddenByUs.remove(row)
            row.animate().cancel()
            markViewStateGone(row, false)
            row.visibility = View.VISIBLE
            row.scaleX = 1f
            row.scaleY = 1f
            row.alpha = 1f
            row.pivotY = 0f
        } catch (e: Throwable) {
            logE("snapVisible error", e)
            row.visibility = View.VISIBLE
            row.scaleY = 1f
            row.alpha = 1f
        }
    }

    fun reset() {
        hiddenByUs.toList().forEach { reset(it) }
        hiddenByUs.clear()
    }

    fun reset(row: View) {
        hiddenByUs.remove(row)
        row.animate().cancel()
        row.scaleY = 1f
        row.scaleX = 1f
        row.alpha = 1f
        row.pivotY = 0f
        markViewStateGone(row, false)
        if (row.parent != null && row.visibility != View.VISIBLE) {
            row.visibility = View.VISIBLE
        }
    }

    /**
     * 同步 SystemUI ViewState.gone，避免下一帧 applyToView 把 GONE 行设回 VISIBLE。
     */
    private fun markViewStateGone(row: View, gone: Boolean) {
        try {
            val viewState = resolveViewState(row) ?: return
            val goneField = HookUtils.findField(viewState.javaClass, "gone") ?: return
            goneField.setBoolean(viewState, gone)
        } catch (_: Throwable) {
        }
    }

    private fun resolveViewState(row: View): Any? {
        try {
            val getter = row.javaClass.methods.firstOrNull {
                it.name == "getViewState" && it.parameterCount == 0
            }
            if (getter != null) {
                getter.isAccessible = true
                val vs = getter.invoke(row)
                if (vs != null) return vs
            }
        } catch (_: Throwable) {
        }
        return try {
            HookUtils.findField(row.javaClass, "mViewState")?.get(row)
        } catch (_: Throwable) {
            null
        }
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
