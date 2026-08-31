package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 音乐锁屏期间隐藏 HyperOS 锁屏大时钟，并在屏顶约 10% 显示一行时间/日期。
 *
 * 藏大时钟后系统会尝试打开左上角状态栏小时钟（默认锁屏没有）；音乐锁屏期间拦截并压住。
 * 退出时必须再清一次，否则会残留在普通锁屏上。
 */
object LockscreenClockController {

    private const val tag = "HyperLockMusic_Clock"

    private var clockView: View? = null
    private var classLoader: ClassLoader? = null
    private var minimalClock: MusicMinimalClockView? = null
    private var statusBarView: View? = null
    private var statusBarClockView: View? = null

    @Volatile
    private var hiddenByMusic: Boolean = false

    @Volatile
    private var applyingVisibility: Boolean = false

    /** 正在主动调用 setKeyguardStatusBarClock，避免 hook 重入死循环 */
    @Volatile
    private var applyingStatusBarClockApi: Boolean = false

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.classLoader = classLoader
        hookVisibilityHelper(classLoader, module)
        hookClockContainerSetVisibility(classLoader, module)
        hookStatusBarClock(classLoader, module)
        logI("LockscreenClockController hooks installed")
    }

    fun setMinimalClockView(view: MusicMinimalClockView?) {
        minimalClock = view
        syncMinimalOverlay()
    }

    fun getMinimalClockView(): MusicMinimalClockView? = minimalClock

    fun setClockView(view: View?) {
        clockView = view
        logI("clockView set: ${view != null}, class=${view?.javaClass?.simpleName}")
        sync()
    }

    fun sync() {
        if (WallpaperController.isShowing() && isMinimalClockEnabled()) {
            hideForMusicLockscreen()
        } else {
            restoreAfterMusicLockscreen()
        }
        syncMinimalOverlay()
    }

    /** 仅刷新简洁时钟显隐（密码页 / 通知中心等布局轮询用，避免反复打系统时钟 hook 日志） */
    fun syncMinimalClockVisibility() {
        syncMinimalOverlay()
    }

    fun onWallpaperUpdated() {
        minimalClock?.onWallpaperUpdated()
        syncMinimalOverlay()
    }

    fun onAlbumTint(color: Int?) {
        minimalClock?.setAlbumTint(color)
    }

    private fun isMinimalClockEnabled(): Boolean {
        val ctx = clockView?.context
            ?: minimalClock?.context
            ?: statusBarView?.context
            ?: return true
        return ConfigReader.minimalClock(ctx)
    }

    private fun syncMinimalOverlay() {
        val mini = minimalClock ?: return
        try {
            val bouncer = HookUtils.isBouncerShowing(mini) ||
                HookUtils.isBouncerShowing(clockView) ||
                HookUtils.isBouncerShowing(statusBarView)
            if (WallpaperController.isShowing() &&
                isMinimalClockEnabled() &&
                LockscreenNotificationController.shouldShowKeyguardOverlays() &&
                !bouncer
            ) {
                ConfigReader.invalidate()
                mini.applyStyleFromConfig()
                mini.showForMusicLockscreen()
            } else {
                mini.hideForMusicLockscreenOff()
            }
        } catch (e: Throwable) {
            logE("syncMinimalOverlay error", e)
        }
    }

    fun hideForMusicLockscreen() {
        try {
            resolveClockView()?.let { clockView = it }
            val clock = clockView
            if (clock != null) {
                hiddenByMusic = true
                applyGone(clock)
                logI("clock hidden for music lockscreen")
            } else {
                logE("hideForMusicLockscreen: clockView is null")
            }
            suppressStatusBarClock()
        } catch (e: Throwable) {
            logE("hideForMusicLockscreen error", e)
        }
    }

    fun restoreAfterMusicLockscreen() {
        minimalClock?.hideForMusicLockscreenOff()
        try {
            // 退出时务必清掉左上角小时钟残留（音乐期间系统可能已把它打开）
            suppressStatusBarClock()
        } catch (e: Throwable) {
            logE("clear status bar clock on restore error", e)
        }
        if (!hiddenByMusic && clockView?.visibility != View.GONE) {
            hiddenByMusic = false
            return
        }
        try {
            hiddenByMusic = false
            val clock = clockView ?: resolveClockView()?.also { clockView = it }
            if (clock == null) {
                logI("restoreAfterMusicLockscreen: no clock view")
                return
            }
            val ctx = clock.context
            if (HookUtils.isOnKeyguard(ctx) &&
                !LockscreenNotificationController.isNotificationShadeOpen()
            ) {
                applyVisible(clock)
                // 大时钟回来后再清一次状态栏小时钟，对齐默认锁屏
                suppressStatusBarClock()
                logI("clock restored (keyguard)")
            } else {
                logI("clock restore deferred (not keyguard / shade open)")
            }
        } catch (e: Throwable) {
            logE("restoreAfterMusicLockscreen error", e)
        }
    }

    private fun applyGone(clock: View) {
        applyingVisibility = true
        try {
            clock.clearAnimation()
            clock.visibility = View.GONE
            clock.alpha = 1f
        } finally {
            applyingVisibility = false
        }
    }

    private fun applyVisible(clock: View) {
        applyingVisibility = true
        try {
            clock.clearAnimation()
            clock.visibility = View.VISIBLE
            clock.alpha = 1f
        } finally {
            applyingVisibility = false
        }
    }

    private fun hookVisibilityHelper(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clazz = Class.forName(
                "com.android.keyguard.injector.KeyguardVisibilityHelperInjector",
                false,
                classLoader
            )
            val method = findSetKeyguardClockVisibility(clazz) ?: run {
                logE("setKeyguardClockVisibility not found")
                return
            }
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                try {
                    if (WallpaperController.isShowing() && isMinimalClockEnabled()) {
                        resolveClockView()?.let { clockView = it }
                        clockView?.let {
                            hiddenByMusic = true
                            applyGone(it)
                        }
                        suppressStatusBarClock()
                    }
                } catch (e: Throwable) {
                    logE("setKeyguardClockVisibility after error", e)
                }
                result
            }
            logI("hooked KeyguardVisibilityHelperInjector.setKeyguardClockVisibility")
        } catch (e: Throwable) {
            logE("hookVisibilityHelper failed", e)
        }
    }

    private fun hookClockContainerSetVisibility(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clazz = Class.forName(
                "com.android.keyguard.clock.KeyguardClockContainer",
                false,
                classLoader
            )
            val method = clazz.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val view = chain.thisObject as? View
                if (view != null) {
                    clockView = view
                }
                val musicMinimal = WallpaperController.isShowing() && isMinimalClockEnabled()
                if (!applyingVisibility && musicMinimal) {
                    hiddenByMusic = true
                    val args = chain.args
                    if (args.isNotEmpty()) {
                        args[0] = View.GONE
                    }
                }
                val result = chain.proceed()
                if (musicMinimal) {
                    suppressStatusBarClock()
                }
                result
            }
            logI("hooked KeyguardClockContainer.setVisibility")
        } catch (e: Throwable) {
            logE("hookClockContainerSetVisibility failed", e)
        }
    }

    private fun hookStatusBarClock(classLoader: ClassLoader, module: XposedModule) {
        try {
            val clazz = Class.forName(
                "com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView",
                false,
                classLoader
            )
            val method = clazz.getDeclaredMethod(
                "setKeyguardStatusBarClock",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                if (applyingStatusBarClockApi) {
                    return@intercept chain.proceed()
                }
                statusBarView = chain.thisObject as? View
                captureStatusBarClock(chain.thisObject)
                val wantShow = chain.args.firstOrNull() as? Boolean ?: false
                if (WallpaperController.isShowing() && isMinimalClockEnabled()) {
                    // 系统想打开左上角时间：直接吞掉，勿 proceed(true) 触发 fadeIn
                    if (wantShow) {
                        forceHideStatusBarClockView()
                        return@intercept null
                    }
                    val result = chain.proceed()
                    forceHideStatusBarClockView()
                    return@intercept result
                }
                chain.proceed()
            }
            logI("hooked MiuiKeyguardStatusBarView.setKeyguardStatusBarClock")

            // MiuiClock.setPolicyVisibility：防止其它路径把状态栏时钟设回 VISIBLE
            hookMiuiClockPolicyVisibility(classLoader, module)
        } catch (e: Throwable) {
            logE("hookStatusBarClock failed", e)
        }
    }

    private fun hookMiuiClockPolicyVisibility(classLoader: ClassLoader, module: XposedModule) {
        val candidates = listOf(
            "com.android.systemui.statusbar.views.MiuiClock",
            "com.android.systemui.statusbar.policy.MiuiClock",
            "com.android.systemui.statusbar.phone.MiuiClock",
            "com.android.keyguard.MiuiClock"
        )
        for (name in candidates) {
            try {
                val clazz = Class.forName(name, false, classLoader)
                val method = clazz.declaredMethods.firstOrNull {
                    it.name == "setPolicyVisibility" && it.parameterTypes.size == 1
                } ?: continue
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val self = chain.thisObject as? View
                    if (WallpaperController.isShowing() &&
                        isMinimalClockEnabled() &&
                        self != null &&
                        statusBarClockView != null &&
                        self === statusBarClockView
                    ) {
                        val args = chain.args
                        if (args.isNotEmpty()) args[0] = View.GONE
                    }
                    chain.proceed()
                }
                logI("hooked $name.setPolicyVisibility")
                return
            } catch (_: Throwable) {
            }
        }
        logI("MiuiClock.setPolicyVisibility not hooked (class not found)")
    }

    private fun captureStatusBarClock(statusBar: Any?) {
        if (statusBar == null) return
        try {
            val f = statusBar.javaClass.getDeclaredField("mKeyguardClock")
            f.isAccessible = true
            statusBarClockView = f.get(statusBar) as? View
            statusBarView = statusBar as? View
        } catch (_: Throwable) {
        }
    }

    /** 压住左上角状态栏小时钟（音乐期间与退出清理共用）。 */
    private fun suppressStatusBarClock() {
        try {
            ensureStatusBarRefs()
            val bar = statusBarView
            if (bar != null) {
                applyingStatusBarClockApi = true
                try {
                    val m = bar.javaClass.getDeclaredMethod(
                        "setKeyguardStatusBarClock",
                        Boolean::class.javaPrimitiveType
                    )
                    m.isAccessible = true
                    // 若当前已是 false 会 early-return，故后面仍 forceHide
                    m.invoke(bar, false)
                } catch (_: Throwable) {
                } finally {
                    applyingStatusBarClockApi = false
                }
            }
            forceHideStatusBarClockView()
        } catch (e: Throwable) {
            logE("suppressStatusBarClock error", e)
        }
    }

    private fun ensureStatusBarRefs() {
        statusBarView?.let { captureStatusBarClock(it) }
        if (statusBarView == null) {
            resolveStatusBarView()?.let {
                statusBarView = it
                captureStatusBarClock(it)
            }
        }
    }

    private fun forceHideStatusBarClockView() {
        ensureStatusBarRefs()
        cancelStatusBarClockAnim()
        val bar = statusBarView
        if (bar != null) {
            try {
                val showField = bar.javaClass.getDeclaredField("mShowClock")
                showField.isAccessible = true
                showField.setBoolean(bar, false)
            } catch (_: Throwable) {
            }
        }
        val clock = statusBarClockView ?: return
        try {
            clock.animate().cancel()
            clock.clearAnimation()
            val m = clock.javaClass.methods.firstOrNull {
                it.name == "setPolicyVisibility" && it.parameterTypes.size == 1
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(clock, View.GONE)
            }
            clock.visibility = View.GONE
            clock.alpha = 0f
        } catch (_: Throwable) {
            try {
                clock.visibility = View.GONE
                clock.alpha = 0f
            } catch (_: Throwable) {
            }
        }
    }

    private fun cancelStatusBarClockAnim() {
        val bar = statusBarView ?: return
        try {
            val folmeField = bar.javaClass.getDeclaredField("mClockFolme")
            folmeField.isAccessible = true
            val folme = folmeField.get(bar) ?: return
            val state = folme.javaClass.methods.firstOrNull {
                it.name == "state" && it.parameterTypes.isEmpty()
            }?.invoke(folme) ?: return
            state.javaClass.methods.firstOrNull {
                it.name == "cancel" && it.parameterTypes.isEmpty()
            }?.invoke(state)
        } catch (_: Throwable) {
        }
    }

    private fun resolveStatusBarView(): View? {
        statusBarView?.let { return it }
        val root = (clockView?.rootView as? ViewGroup)
            ?: (minimalClock?.rootView as? ViewGroup)
            ?: return null
        return findViewByClassName(root, "MiuiKeyguardStatusBarView")
    }

    private fun findViewByClassName(root: ViewGroup, simpleName: String): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.javaClass.simpleName == simpleName) return child
            if (child is ViewGroup) {
                findViewByClassName(child, simpleName)?.let { return it }
            }
        }
        return null
    }

    private fun findSetKeyguardClockVisibility(clazz: Class<*>): Method? {
        for (m in clazz.declaredMethods) {
            if (m.name != "setKeyguardClockVisibility") continue
            val pt = m.parameterTypes
            if (pt.size == 4 &&
                pt[0] == Int::class.javaPrimitiveType &&
                pt[1] == Int::class.javaPrimitiveType &&
                pt[2] == Boolean::class.javaPrimitiveType &&
                pt[3] == Boolean::class.javaPrimitiveType
            ) {
                m.isAccessible = true
                return m
            }
        }
        return clazz.declaredMethods.firstOrNull { it.name == "setKeyguardClockVisibility" }
            ?.also { it.isAccessible = true }
    }

    private fun resolveClockView(): View? {
        clockView?.let { return it }
        val cl = classLoader ?: return null
        return try {
            val interfacesMgr = Class.forName(
                "com.miui.systemui.interfacesmanager.InterfacesImplManager",
                false,
                cl
            )
            val injectorClass = Class.forName(
                "com.android.keyguard.injector.KeyguardClockInjector",
                false,
                cl
            )
            val getImpl = interfacesMgr.getDeclaredMethod("getImpl", Class::class.java)
            val injector = getImpl.invoke(null, injectorClass) ?: return null
            val field = injectorClass.getDeclaredField("keyguardClockView")
            field.isAccessible = true
            field.get(injector) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
