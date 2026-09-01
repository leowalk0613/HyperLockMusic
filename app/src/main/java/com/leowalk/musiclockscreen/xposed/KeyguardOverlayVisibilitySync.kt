package com.leowalk.musiclockscreen.xposed

/**
 * 避免通知栈每次 onLayout 都触发 overlay 显隐 / reflow（AOD 上会造成媒体控件卡顿）。
 */
internal object KeyguardOverlayVisibilitySync {

    private var lastShouldShow: Boolean? = null

    /** @return true 当 [shouldShowKeyguardOverlays] 相对上次发生变化，应执行显隐同步。 */
    fun shouldApply(shouldShowKeyguardOverlays: Boolean): Boolean {
        if (lastShouldShow == shouldShowKeyguardOverlays) return false
        lastShouldShow = shouldShowKeyguardOverlays
        return true
    }

    fun reset() {
        lastShouldShow = null
    }
}
