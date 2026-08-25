package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup

/**
 * SystemUI 通知动画：原地收缩隐藏 / 展开恢复。
 */
object SystemNotificationAnimator {

    private const val tag = "MusicLockScreen_SysAnim"
    private const val COLLAPSE_MS = 260L
    private const val EXPAND_MS = 200L

    private val easeIn by lazy { android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f) }
    private val easeOut by lazy { android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1f) }

    private val collapsing = HashSet<View>()

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun isHidden(row: View): Boolean {
        return row.visibility == View.GONE || collapsing.contains(row)
    }

    fun scheduleRemove(stack: ViewGroup, row: View): Boolean {
        if (!HookUtils.isOnKeyguard(stack.context)) return false
        if (isHidden(row)) return true
        collapseInPlace(row)
        return true
    }

    fun snapVisible(row: View) {
        try {
            if (row.parent == null) return
            collapsing.remove(row)
            row.animate().cancel()
            row.visibility = View.VISIBLE
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

    fun expandInPlace(row: View): Boolean {
        try {
            if (row.parent == null) return false
            if (row.visibility == View.VISIBLE && row.scaleY >= 0.99f && row.alpha >= 0.99f) {
                return true
            }
            setPivot(row)
            row.animate().cancel()
            row.visibility = View.VISIBLE
            row.scaleY = 0f
            row.alpha = 0f
            row.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(EXPAND_MS)
                .setInterpolator(easeOut)
                .start()
            return true
        } catch (e: Throwable) {
            logE("expandInPlace error", e)
            row.scaleY = 1f
            row.alpha = 1f
            row.visibility = View.VISIBLE
            return false
        }
    }

    fun reset() {
        collapsing.toList().forEach { reset(it) }
        collapsing.clear()
    }

    fun reset(row: View) {
        collapsing.remove(row)
        row.animate().cancel()
        row.scaleY = 1f
        row.alpha = 1f
        row.pivotY = 0f
    }

    private fun collapseInPlace(row: View) {
        try {
            if (row.parent == null) return
            val fullyVisible = row.visibility == View.VISIBLE && row.alpha >= 0.99f && row.scaleY >= 0.99f
            if (collapsing.contains(row) && !fullyVisible) return
            collapsing.add(row)
            setPivot(row)
            row.animate().cancel()
            row.alpha = 1f
            row.scaleY = 1f
            row.animate()
                .scaleY(0f)
                .alpha(0f)
                .setDuration(COLLAPSE_MS)
                .setInterpolator(easeIn)
                .withEndAction {
                    collapsing.remove(row)
                    row.scaleY = 1f
                    row.alpha = 1f
                    row.visibility = View.GONE
                }
                .start()
        } catch (e: Throwable) {
            logE("collapseInPlace error", e)
            collapsing.remove(row)
            row.visibility = View.GONE
        }
    }

    private fun setPivot(row: View) {
        val h = row.height.toFloat()
        if (h > 0f) row.pivotY = h / 2f
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
