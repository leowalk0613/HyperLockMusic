package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule

/**
 * 锁屏媒体控件 AOD 息屏稳定（轻量版，避免每帧 hook 打架导致卡顿）
 *
 * 1. [onFullAodStateChanged] — aod_full_media 时始终保持展开布局（不压缩）
 * 2. [setAnimateHeight] — 忽略非零 animateHeight，避免息屏高度动画
 * 3. [notifyHeightChanged] — 高度变化时 snap，不触发展开动画
 * 4. [setHideAmount] — linkage 窗口内把 hideAmount 钳在 0
 * 5. [getShouldBeVisible] — linkage 窗口内保持通知栈可见
 */
class MediaScaleFixHook {

    private val tag = "HyperLockMusic_MediaScale"
    private var module: XposedModule? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        logI("install start")

        try {
            hookOnFullAodStateChanged(classLoader, module)
            hookSetAnimateHeight(classLoader, module)
            hookNotifyHeightChanged(classLoader, module)
            hookMediaWakeFolmeNeutral(classLoader, module)
            hookMediaFullAodAnimState(classLoader, module)
            hookSetHideAmount(classLoader, module)
            hookNotificationStackShouldBeVisible(classLoader, module)
            logI("MediaScaleFixHook install done")
        } catch (e: Throwable) {
            logE("install failed: ${e.message}", e)
        }
    }

    private fun hookOnFullAodStateChanged(classLoader: ClassLoader, module: XposedModule) {
        try {
            val viewControllerClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
            )
            val onFullAodMethod = viewControllerClass.declaredMethods.firstOrNull { method ->
                method.name == "onFullAodStateChanged" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }

            if (onFullAodMethod == null) {
                logE("onFullAodStateChanged method not found")
                return
            }
            module.deoptimize(onFullAodMethod)
            module.hook(onFullAodMethod).intercept { chain ->
                if (shouldKeepMediaExpanded()) {
                    chain.proceed(arrayOf<Any?>(false))
                } else {
                    chain.proceed()
                }
                null
            }
            logI("hook onFullAodStateChanged: OK")
        } catch (e: Throwable) {
            logE("hook onFullAodStateChanged failed: ${e.message}")
        }
    }

    /**
     * 系统用非零 animateHeight 驱动息屏高度过渡；直接忽略，保持 [getIntrinsicHeight] 展开态。
     */
    private fun hookSetAnimateHeight(classLoader: ClassLoader, module: XposedModule) {
        try {
            val headerClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"
            )
            val method = headerClass.declaredMethods.firstOrNull { m ->
                m.name == "setAnimateHeight" &&
                    m.parameterCount == 1 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("setAnimateHeight not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                val requested = chain.args.firstOrNull() as? Int ?: 0
                if (shouldKeepMediaExpanded() &&
                    MediaAodExpandPolicy.shouldSuppressAnimateHeight(requested)
                ) {
                    null
                } else {
                    chain.proceed()
                }
                null
            }
            logI("hook MiuiMediaHeaderView.setAnimateHeight: OK")
        } catch (e: Throwable) {
            logE("hook setAnimateHeight failed: ${e.message}")
        }
    }

    /** 高度变化时不走 StackStateAnimator 的 resize 展开动画。 */
    private fun hookNotifyHeightChanged(classLoader: ClassLoader, module: XposedModule) {
        try {
            val headerClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"
            )
            val method = headerClass.declaredMethods.firstOrNull { m ->
                m.name == "notifyHeightChanged" &&
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == String::class.java &&
                    m.parameterTypes[1] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
                ?: headerClass.superclass?.declaredMethods?.firstOrNull { m ->
                    m.name == "notifyHeightChanged" &&
                        m.parameterCount == 2 &&
                        m.parameterTypes[0] == String::class.java &&
                        m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }?.apply { isAccessible = true }
            if (method == null) {
                logE("notifyHeightChanged not found on MiuiMediaHeaderView")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (shouldKeepMediaExpanded()) {
                    val reason = chain.args.getOrNull(0) as? String ?: ""
                    val animate = chain.args.getOrNull(1) as? Boolean == true
                    if (MediaAodExpandPolicy.shouldForceSnapHeight(true, animate)) {
                        chain.proceed(arrayOf<Any?>(reason, false))
                    } else {
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
                null
            }
            logI("hook MiuiMediaHeaderView.notifyHeightChanged: OK")
        } catch (e: Throwable) {
            logE("hook notifyHeightChanged failed: ${e.message}")
        }
    }

    /**
     * 息屏/亮屏 linkage 的 Folme scale/alpha 会让媒体 header 出现「展开」感；强制 neutral。
     */
    private fun hookMediaWakeFolmeNeutral(classLoader: ClassLoader, module: XposedModule) {
        val methodNames = listOf(
            "setFolmeScaleXForType",
            "setFolmeScaleYForType",
            "setFolmeTranslationYForType",
            "setFolmeAlphaForType",
        )
        try {
            val injectorClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.stack.ViewStateInjectorImpl"
            )
            val mediaHeaderClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"
            )
            for (name in methodNames) {
                val method = injectorClass.declaredMethods.firstOrNull { m ->
                    m.name == name &&
                        m.parameterCount == 3 &&
                        m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                        m.parameterTypes[2] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
                if (method == null) {
                    logE("$name not found on ViewStateInjectorImpl")
                    continue
                }
                module.deoptimize(method)
                module.hook(method).intercept { chain ->
                    val view = chain.args.getOrNull(0)
                    val type = chain.args.getOrNull(2) as? Int ?: 0
                    if (view != null &&
                        mediaHeaderClass.isInstance(view) &&
                        MediaAodExpandPolicy.isWakeSleepFolmeType(type) &&
                        shouldKeepMediaExpanded()
                    ) {
                        val neutral = MediaAodExpandPolicy.neutralWakeFolmeValue(name)
                        chain.proceed(arrayOf<Any?>(view, neutral, type))
                    } else {
                        chain.proceed()
                    }
                    null
                }
                logI("hook ViewStateInjectorImpl.$name: OK")
            }
        } catch (e: Throwable) {
            logE("hook media wake folme failed: ${e.message}")
        }
    }

    /**
     * Full AOD Folme 每帧刷新媒体背景玻璃/混色；aod_full_media 时跳过，避免和锁屏布局抢帧。
     */
    private fun hookMediaFullAodAnimState(classLoader: ClassLoader, module: XposedModule) {
        try {
            val implClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
            )
            val listenerClass = implClass.declaredClasses.firstOrNull { cls ->
                cls.simpleName.contains("mediaFullAodListener")
            } ?: run {
                logE("mediaFullAodListener inner class not found")
                return
            }
            val method = listenerClass.declaredMethods.firstOrNull { m ->
                m.name == "updateFullAodAnimState" && m.parameterCount == 14
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("updateFullAodAnimState not found on media listener")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (shouldKeepMediaExpanded()) {
                    null
                } else {
                    chain.proceed()
                }
            }
            logI("hook mediaFullAodListener.updateFullAodAnimState: OK")
        } catch (e: Throwable) {
            logE("hook updateFullAodAnimState failed: ${e.message}")
        }
    }

    /**
     * 息屏 linkage 时系统逐帧增大 hideAmount；钳在 0 比逐帧改 setAnimateHeight / setVisibility 轻得多。
     */
    private fun hookSetHideAmount(classLoader: ClassLoader, module: XposedModule) {
        try {
            val controllerClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController"
            )
            val method = controllerClass.declaredMethods.firstOrNull { m ->
                m.name == "setHideAmount" &&
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("setHideAmount not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (shouldPinDuringLinkage()) {
                    val linear = chain.args.getOrNull(0) as? Float ?: 0f
                    val interpolated = chain.args.getOrNull(1) as? Float ?: 0f
                    if (linear > 0f || interpolated > 0f) {
                        chain.proceed(arrayOf<Any?>(0f, 0f))
                    } else {
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
                null
            }
            logI("hook NotificationStackScrollLayoutController.setHideAmount: OK")
        } catch (e: Throwable) {
            logE("hook setHideAmount failed: ${e.message}")
        }
    }

    private fun hookNotificationStackShouldBeVisible(classLoader: ClassLoader, module: XposedModule) {
        try {
            val injectorClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutInjector"
            )
            val method = injectorClass.declaredMethods.firstOrNull { m ->
                m.name == "getShouldBeVisible" && m.parameterCount == 0
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("getShouldBeVisible not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                if (shouldPinDuringLinkage()) {
                    true
                } else {
                    chain.proceed()
                }
            }
            logI("hook NotificationStackScrollLayoutInjector.getShouldBeVisible: OK")
        } catch (e: Throwable) {
            logE("hook getShouldBeVisible failed: ${e.message}")
        }
    }

    private fun shouldKeepMediaExpanded(): Boolean {
        return MediaAodExpandPolicy.shouldKeepExpanded(HookUtils.systemUiApplicationContext())
    }

    private fun shouldPinDuringLinkage(): Boolean {
        val ctx = HookUtils.systemUiApplicationContext() ?: return false
        if (!KeyguardSleepTransition.isMusicLockscreenActive()) return false
        if (!HookUtils.isOnKeyguard(ctx)) return false
        return KeyguardSleepTransition.isInLinkageAnimWindow()
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            module?.log(android.util.Log.ERROR, tag, msg, e)
        } else {
            module?.log(android.util.Log.ERROR, tag, msg)
        }
    }
}
