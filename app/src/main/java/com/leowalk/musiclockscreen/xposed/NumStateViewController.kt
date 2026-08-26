package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.animation.PathInterpolator

/**
 * 锁屏勿扰/通知数量状态控制器
 *
 * 音乐锁屏时隐藏 "勿扰 | N个通知" 状态显示（num_state_view）
 * 进入/退出使用柔和渐隐渐显过渡，避免生硬。
 */
object NumStateViewController {

    private const val tag = "HyperLockMusic_NumState"

    // 过渡动画时长
    private const val FADE_MS = 260L
    private val easeOut by lazy { PathInterpolator(0f, 0f, 0.2f, 1f) }

    private var numStateView: View? = null
    private var originalVisibility: Int = View.VISIBLE
    private var isHidden: Boolean = false

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        reapplyIfNeeded()
    }

    fun setNumStateView(view: View?) {
        // 移除旧的监听
        numStateView?.removeOnLayoutChangeListener(layoutChangeListener)
        numStateView = view
        if (view != null) {
            originalVisibility = view.visibility
            view.addOnLayoutChangeListener(layoutChangeListener)
            logI("num_state_view found (${view.javaClass.simpleName}), originalVisibility=$originalVisibility")
            // 如果当前已经是隐藏状态，立即应用
            if (isHidden) {
                view.animate().cancel()
                view.alpha = 1f
                view.visibility = View.GONE
                logI("already in hidden state, applied immediately")
            }
        } else {
            logI("num_state_view set to null")
        }
    }

    /**
     * 隐藏勿扰/通知数量状态（柔和渐隐）
     */
    fun hide() {
        if (isHidden) return
        try {
            val view = numStateView ?: run {
                // View 还没找到也记录状态，等找到后自动应用
                isHidden = true
                logI("hide requested but view is null, will apply when found")
                return
            }
            originalVisibility = view.visibility
            view.animate().cancel()

            // 确保可见且 alpha=1 后再渐隐，避免中途状态残留
            view.visibility = View.VISIBLE
            view.alpha = 1f

            view.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .setInterpolator(easeOut)
                .withEndAction {
                    // 动画期间可能被系统重置隐藏状态，结束时以逻辑状态为准
                    if (isHidden) {
                        view.visibility = View.GONE
                        view.alpha = 1f
                    }
                }
                .start()

            isHidden = true
            logI("num_state_view fade-out started")
        } catch (e: Throwable) {
            logE("hide error", e)
        }
    }

    /**
     * 恢复勿扰/通知数量状态（柔和渐显）
     */
    fun show() {
        if (!isHidden) return
        isHidden = false
        try {
            val view = numStateView ?: run {
                logI("show requested but view is null")
                return
            }
            view.animate().cancel()

            // 从隐藏状态渐显
            view.visibility = originalVisibility
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setDuration(FADE_MS)
                .setInterpolator(easeOut)
                .start()

            logI("num_state_view fade-in started (visibility=$originalVisibility)")
        } catch (e: Throwable) {
            logE("show error", e)
        }
    }

    /**
     * 布局变化时若状态仍为隐藏，立即重新隐藏（不走动画，保证实时性）
     */
    fun reapplyIfNeeded() {
        if (!isHidden) return
        try {
            val view = numStateView ?: return
            if (view.visibility != View.GONE) {
                logI("visibility reset by system, re-hiding num_state_view")
                view.animate().cancel()
                view.alpha = 1f
                view.visibility = View.GONE
            }
        } catch (_: Throwable) {
        }
    }

    fun isHidden(): Boolean = isHidden

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}