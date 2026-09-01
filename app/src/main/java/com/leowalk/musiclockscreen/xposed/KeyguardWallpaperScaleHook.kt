package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule

/**
 * 音乐锁屏息屏：按开关拦截 KeyguardPanelViewController 的壁纸缩放，只让 wallpaperBlack 压暗动画运行。
 * 适用于整个音乐锁屏（大专辑 / 沉浸 / 仅歌词）。
 */
object KeyguardWallpaperScaleHook {

    private const val TAG = "HyperLockMusic_WpScale"
    private const val PANEL_CLASS = "com.android.keyguard.panel.KeyguardPanelViewController"
    private const val WAKE_OBSERVER_CLASS =
        "com.android.keyguard.panel.KeyguardPanelViewController\$wakeObserver\$1"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(classLoader: ClassLoader, module: XposedModule,
                logCb: (Int, String, String, Throwable?) -> Unit) {
        logCallback = logCb
        try {
            val panelClass = classLoader.loadClass(PANEL_CLASS)

            hookScaleMethod(module, panelClass, "setWallpaperScale", 3)
            hookScaleMethod(module, panelClass, "setWallpaperZoom", 1)
            hookScaleMethod(module, panelClass, "doDeductedImageScaleAnim", 4)

            hookLinkageViewAnimDefault(module, panelClass)
            hookWakefulnessObserver(module, classLoader)

            logI("KeyguardWallpaperScaleHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    fun bindScreenEvents(context: android.content.Context) {
        KeyguardWallpaperScalePolicy.bindScreenEvents(context)
    }

    private fun hookWakefulnessObserver(module: XposedModule, classLoader: ClassLoader) {
        try {
            val observerClass = classLoader.loadClass(WAKE_OBSERVER_CLASS)
            hookSleepCallback(module, observerClass, "onStartedGoingToSleep") {
                val ctx = HookUtils.systemUiApplicationContext()
                if (KeyguardSleepTransition.isMusicLockscreenActive()) {
                    KeyguardSleepTransition.onStartedGoingToSleep()
                } else if (ctx != null && ConfigReader.aodFullMedia(ctx)) {
                    KeyguardSleepTransition.onStartedGoingToSleep()
                }
                if (ctx != null && KeyguardWallpaperScalePolicy.shouldHandleSleepTransition(ctx)) {
                    KeyguardWallpaperScalePolicy.onGoingToSleep()
                }
            }
            hookSleepCallback(module, observerClass, "onStartedWakingUp") {
                KeyguardSleepTransition.onStartedWakingUp()
                KeyguardWallpaperScalePolicy.onWakingUp()
            }
            logI("hook $WAKE_OBSERVER_CLASS sleep callbacks: OK")
        } catch (e: Throwable) {
            logE("wakeObserver hook failed", e)
        }
    }

    private fun hookSleepCallback(
        module: XposedModule,
        observerClass: Class<*>,
        name: String,
        beforeProceed: () -> Unit
    ) {
        val method = observerClass.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
        if (method == null) {
            logE("$name not found on $WAKE_OBSERVER_CLASS")
            return
        }
        module.deoptimize(method)
        module.hook(method).intercept { chain ->
            try {
                beforeProceed()
            } catch (_: Throwable) {
            }
            chain.proceed()
            null
        }
    }

    /** linkageViewAnim$default 在 onStartedGoingToSleep 末尾触发，再保险取消 Folme scale。 */
    private fun hookLinkageViewAnimDefault(module: XposedModule, panelClass: Class<*>) {
        val method = panelClass.declaredMethods.firstOrNull { m ->
            m.name == "linkageViewAnim\$default" && m.parameterCount == 4
        }?.apply { isAccessible = true }
        if (method == null) {
            logE("linkageViewAnim\$default(4) not found on $PANEL_CLASS")
            return
        }
        module.deoptimize(method)
        module.hook(method).intercept { chain ->
            val ctx = HookUtils.systemUiApplicationContext()
            if (ctx != null && KeyguardWallpaperScalePolicy.shouldHandleSleepTransition(ctx)) {
                KeyguardWallpaperScalePolicy.onGoingToSleep()
            }
            chain.proceed()
            null
        }
        logI("hook $PANEL_CLASS.linkageViewAnim\$default: OK")
    }

    private fun hookScaleMethod(module: XposedModule, panelClass: Class<*>, name: String, paramCount: Int) {
        val method = panelClass.declaredMethods.firstOrNull { m ->
            m.name == name && m.parameterCount == paramCount
        }?.apply { isAccessible = true }
        if (method == null) {
            logE("$name($paramCount) not found on $PANEL_CLASS")
            return
        }
        module.deoptimize(method)
        module.hook(method).intercept { chain ->
            val ctx = HookUtils.systemUiApplicationContext()
            if (KeyguardWallpaperScalePolicy.shouldSuppress(ctx)) {
                null
            } else {
                chain.proceed()
                null
            }
        }
        logI("hook $PANEL_CLASS.$name: OK")
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
