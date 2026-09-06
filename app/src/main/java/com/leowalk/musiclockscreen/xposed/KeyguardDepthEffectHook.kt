package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule

/**
 * 拦截官方景深刷新路径，音乐锁屏激活时持续压制 `deductedImageView` / 视频景深。
 */
object KeyguardDepthEffectHook {

    private const val TAG = "HyperLockMusic_DepthHook"
    private const val PANEL_CLASS = "com.android.keyguard.panel.KeyguardPanelViewController"
    private const val DEPTH_INTERACTOR_CLASS = "com.android.keyguard.depth.KeyguardDepthInteractor"
    private const val WALLPAPER_MANAGER_CLASS =
        "com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager"
    private const val SETTINGS_OBSERVER_CLASS = "com.miui.keyguard.KeyguardCommonSettingObserver"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(classLoader: ClassLoader, module: XposedModule,
                logCb: (Int, String, String, Throwable?) -> Unit) {
        logCallback = logCb
        KeyguardDepthEffectPolicy.logCallback = logCb
        try {
            val panelClass = classLoader.loadClass(PANEL_CLASS)
            hookAfterReapply(module, panelClass, "updateShowDepthState", 0)
            hookAfterReapply(module, panelClass, "updateKeyguardElementsVisibility", 0)

            hookDepthInteractorAlpha(module, classLoader)
            hookIsDepthVideoEnable(module, classLoader)
            hookGetDepthEffectEnable(module, classLoader)

            logI("KeyguardDepthEffectHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun hookAfterReapply(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        paramCount: Int
    ) {
        val method = clazz.declaredMethods.firstOrNull {
            it.name == name && it.parameterCount == paramCount
        }?.apply { isAccessible = true }
        if (method == null) {
            logE("$name($paramCount) not found on ${clazz.name}")
            return
        }
        module.deoptimize(method)
        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (KeyguardDepthEffectPolicy.shouldSuppress()) {
                KeyguardDepthEffectPolicy.reapplyIfSuppressed()
            }
            result
        }
        logI("hook ${clazz.simpleName}.$name: OK")
    }

    private fun hookDepthInteractorAlpha(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(DEPTH_INTERACTOR_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "setDepthTransitionAlpha" && it.parameterCount == 3
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("setDepthTransitionAlpha(3) not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (KeyguardDepthEffectPolicy.shouldSuppress()) {
                    val args = chain.args
                    if (args.isNotEmpty() && args[0] is Float) {
                        args[0] = 0f
                    }
                }
                chain.proceed()
                null
            }
            logI("hook setDepthTransitionAlpha: OK")
        } catch (e: Throwable) {
            logE("hook setDepthTransitionAlpha failed", e)
        }
    }

    private fun hookIsDepthVideoEnable(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(WALLPAPER_MANAGER_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "isDepthVideoEnable" && it.parameterCount == 0
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("isDepthVideoEnable not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (KeyguardDepthEffectPolicy.shouldSuppress()) {
                    false
                } else {
                    chain.proceed()
                }
            }
            logI("hook isDepthVideoEnable: OK")
        } catch (e: Throwable) {
            logE("hook isDepthVideoEnable failed", e)
        }
    }

    private fun hookGetDepthEffectEnable(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(SETTINGS_OBSERVER_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "getDepthEffectEnable" && it.parameterCount == 0
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("getDepthEffectEnable not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (KeyguardDepthEffectPolicy.shouldSuppress()) {
                    false
                } else {
                    chain.proceed()
                }
            }
            logI("hook getDepthEffectEnable: OK")
        } catch (e: Throwable) {
            logE("hook getDepthEffectEnable failed", e)
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
