package com.leowalk.musiclockscreen.xposed

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 锁屏时钟极简模式控制器
 *
 * 方案：
 * 1. 把时钟 View 从父容器中 remove 掉（最彻底的隐藏方式）
 * 2. 把时间加到日期 TextView 前面，让它们同一行显示
 */
object LockscreenClockController {

    private const val tag = "HyperLockMusic_Clock"
    private const val TIME_PREFIX_MARKER = "  "

    private var clockView: View? = null
    private var dateView: TextView? = null
    private var isMinimalMode: Boolean = false

    // 保存原始父容器和位置，用于恢复
    private var originalParent: ViewGroup? = null
    private var originalIndex: Int = -1
    private var originalLayoutParams: ViewGroup.LayoutParams? = null
    private var originalDateText: CharSequence? = null

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var isUpdatingText: Boolean = false

    private val dateTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (isUpdatingText) return
            if (!isMinimalMode) return
            val date = dateView ?: return
            val text = s?.toString() ?: return

            val parts = text.split(TIME_PREFIX_MARKER)
            if (parts.size >= 2 && parts[0].matches(Regex("\\d{2}:\\d{2}"))) return

            isUpdatingText = true
            val timeStr = timeFormat.format(Date())
            date.text = "$timeStr$TIME_PREFIX_MARKER$text"
            logI("date text updated, added time prefix: ${date.text}")
            isUpdatingText = false
        }
    }

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun setClockView(view: View?) {
        clockView = view
        logI("clockView set: ${view != null}, class=${view?.javaClass?.simpleName}")
        logI("  parent: ${view?.parent?.javaClass?.simpleName}")
    }

    fun setDateView(view: TextView?) {
        dateView?.removeTextChangedListener(dateTextWatcher)
        dateView = view
        view?.addTextChangedListener(dateTextWatcher)
        logI("dateView set: ${view != null}, text=\"${view?.text}\"")
    }

    fun applyMinimalMode() {
        if (isMinimalMode) return
        try {
            val clock = clockView
            val date = dateView
            if (clock == null) {
                logE("applyMinimalMode failed: clockView is null")
                return
            }
            if (date == null) {
                logE("applyMinimalMode failed: dateView is null")
                return
            }

            originalDateText = date.text

            // 把时钟从父容器中 remove 掉
            val parent = clock.parent as? ViewGroup
            if (parent != null) {
                originalParent = parent
                originalLayoutParams = clock.layoutParams
                // 找到 clock 在父容器中的位置
                for (i in 0 until parent.childCount) {
                    if (parent.getChildAt(i) == clock) {
                        originalIndex = i
                        break
                    }
                }
                parent.removeView(clock)
                logI("clock removed from parent, index=$originalIndex")
            } else {
                logE("clock has no parent, cannot remove")
            }

            // 在日期前面加上时间
            isUpdatingText = true
            val timeStr = timeFormat.format(Date())
            val newText = "$timeStr$TIME_PREFIX_MARKER${date.text}"
            date.text = newText
            isUpdatingText = false

            isMinimalMode = true
            logI("minimal mode applied: \"$newText\"")
        } catch (e: Throwable) {
            logE("applyMinimalMode error", e)
        }
    }

    fun restoreNormalMode() {
        if (!isMinimalMode) return
        try {
            val clock = clockView
            val date = dateView

            isMinimalMode = false

            // 把时钟加回父容器
            if (clock != null && originalParent != null) {
                if (originalIndex >= 0 && originalIndex < originalParent!!.childCount + 1) {
                    originalParent!!.addView(clock, originalIndex, originalLayoutParams)
                } else {
                    originalParent!!.addView(clock, originalLayoutParams)
                }
                logI("clock restored to parent, index=$originalIndex")
            }

            if (date != null && originalDateText != null) {
                isUpdatingText = true
                date.text = originalDateText
                isUpdatingText = false
            }

            logI("normal mode restored")
        } catch (e: Throwable) {
            logE("restoreNormalMode error", e)
        }
    }

    fun isMinimal(): Boolean = isMinimalMode

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
