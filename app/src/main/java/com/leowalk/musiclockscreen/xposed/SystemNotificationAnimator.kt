package com.leowalk.musiclockscreen.xposed

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup

/**
 * 通知行隐藏：只动当前 VISIBLE 的普通行；立刻 GONE + 同步 ViewState.gone。
 *
 * 反编译 [ViewState.applyToView]：gone==true 时直接 return。
 * 切勿改写 SystemUI 已在锁屏藏起的行（本就 GONE）——否则解锁后 ViewState.gone 会卡死。
 */
object SystemNotificationAnimator {

    private const val tag = "HyperLockMusic_SysAnim"
    /** 标记本模块藏起的行，release 时只还原这些。 */
    private val TAG_HIDDEN_BY_US = 0x48C00101

    private val hiddenByUs = HashSet<View>()
    private val mainHandler = Handler(Looper.getMainLooper())

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun isHidden(row: View): Boolean {
        return row.getTag(TAG_HIDDEN_BY_US) == true || hiddenByUs.contains(row)
    }

    fun isMarkedByUs(row: View): Boolean = isHidden(row)

    fun scheduleRemove(stack: ViewGroup, row: View): Boolean {
        if (!LockscreenNotificationController.shouldFilterNotifications()) return false
        // 已是 GONE：多半是 SystemUI 锁屏过滤，绝不能再写 ViewState.gone
        if (row.visibility != View.VISIBLE) return false
        hideImmediately(row)
        return true
    }

    fun hideImmediately(row: View) {
        try {
            if (row.parent == null) return
            // 只藏当前可见行，避免污染 SystemUI 已隐藏的锁屏敏感/静音通知
            if (row.visibility != View.VISIBLE) return
            row.animate().cancel()
            row.scaleX = 1f
            row.scaleY = 1f
            row.alpha = 1f
            row.pivotY = 0f
            row.visibility = View.GONE
            markViewStateGone(row, true)
            row.setTag(TAG_HIDDEN_BY_US, true)
            hiddenByUs.add(row)
        } catch (e: Throwable) {
            logE("hideImmediately error", e)
            try {
                if (row.visibility == View.VISIBLE) {
                    row.visibility = View.GONE
                    row.setTag(TAG_HIDDEN_BY_US, true)
                    hiddenByUs.add(row)
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun snapVisible(row: View) {
        try {
            if (row.parent == null) {
                clearOurMark(row)
                return
            }
            clearOurMark(row)
            row.animate().cancel()
            markViewStateGone(row, false)
            row.visibility = View.VISIBLE
            row.scaleX = 1f
            row.scaleY = 1f
            row.alpha = 1f
            row.pivotY = 0f
        } catch (e: Throwable) {
            logE("snapVisible error", e)
            clearOurMark(row)
            try {
                row.visibility = View.VISIBLE
                row.scaleY = 1f
                row.alpha = 1f
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * 交还 SystemUI：只还原本模块标记过的行，并清 ViewState.gone。
     * @return 还原行数
     */
    fun releaseMarkedInStack(stack: ViewGroup): Int {
        var restored = 0
        val snapshot = hiddenByUs.toList()
        for (row in snapshot) {
            try {
                if (row.parent === stack || row.parent != null) {
                    snapVisible(row)
                    restored++
                } else {
                    clearOurMark(row)
                    markViewStateGone(row, false)
                }
            } catch (_: Throwable) {
                clearOurMark(row)
            }
        }
        // 扫一遍：tag 还在但 set 丢了的行（弱引用/重入）
        for (i in 0 until stack.childCount) {
            val child = stack.getChildAt(i)
            if (child.getTag(TAG_HIDDEN_BY_US) == true) {
                snapVisible(child)
                restored++
            }
        }
        hiddenByUs.clear()
        return restored
    }

    /** 解锁后补扫：防止动画中途 reparent 导致漏还原。 */
    fun scheduleReleaseSweep(stack: ViewGroup?, delaysMs: LongArray = longArrayOf(80L, 320L, 800L)) {
        if (stack == null) return
        for (delay in delaysMs) {
            mainHandler.postDelayed({
                try {
                    var n = 0
                    for (i in 0 until stack.childCount) {
                        val child = stack.getChildAt(i)
                        if (child.getTag(TAG_HIDDEN_BY_US) == true) {
                            snapVisible(child)
                            n++
                        }
                    }
                    if (n > 0) {
                        android.util.Log.i(tag, "release sweep restored $n row(s) after ${delay}ms")
                    }
                } catch (e: Throwable) {
                    logE("release sweep error", e)
                }
            }, delay)
        }
    }

    fun reset() {
        hiddenByUs.toList().forEach { row ->
            clearOurMark(row)
            try {
                markViewStateGone(row, false)
                row.animate().cancel()
                row.scaleY = 1f
                row.scaleX = 1f
                row.alpha = 1f
                if (row.parent != null) {
                    row.visibility = View.VISIBLE
                }
            } catch (_: Throwable) {
            }
        }
        hiddenByUs.clear()
    }

    private fun clearOurMark(row: View) {
        hiddenByUs.remove(row)
        try {
            row.setTag(TAG_HIDDEN_BY_US, null)
        } catch (_: Throwable) {
        }
    }

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
