package com.leowalk.musiclockscreen.xposed

/**
 * AOD 实时进度条策略（对齐 HyperOS 17.03 SystemUI）。
 *
 * 新链路：`shouldObserve = hasHolder && isAwake` →
 * [MiuiMediaSeekBarProgressOwner.register/unregister]。
 * AOD 时 isAwake=false，会 unregister 并 `setListening(false)`，进度卡住。
 */
internal object MediaAodProgressPolicy {

    /**
     * 息屏仍挂着 holder 时拦截 unregister，避免停掉 polling。
     * detach 会先清 holder 再 unregister，此时应放行。
     */
    fun shouldInterceptUnregister(keepAodProgress: Boolean, hasHolder: Boolean): Boolean {
        return keepAodProgress && hasHolder
    }

    /** attach 时若已息屏，shouldObserve 不会自动 register，需主动补一次。 */
    fun shouldForceRegisterAfterAttach(keepAodProgress: Boolean): Boolean = keepAodProgress

    /** onRenderState / seekBarChanged：绕过 panelAnimating 门闩。 */
    fun shouldBypassPanelAnimating(keepAodProgress: Boolean): Boolean = keepAodProgress

    /**
     * LiveData Observer 类名：旧版 seekBarObserver，新版 progressObserver。
     */
    fun isProgressObserverClass(className: String): Boolean {
        if (className.isEmpty()) return false
        return className.contains("seekBarObserver", ignoreCase = true) ||
            className.contains("SeekBarObserver") ||
            className.contains("\$seekBarObserver\$") ||
            className.contains("progressObserver", ignoreCase = true) ||
            className.contains("ProgressObserver") ||
            className.contains("\$progressObserver\$")
    }
}
