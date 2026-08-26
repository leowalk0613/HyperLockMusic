package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * AOD 媒体进度条实时更新 Hook（SystemUI 进程）
 *
 * 问题：AOD 下媒体控件进度条和时间不更新。
 *
 * 根因：
 * MiuiMediaViewControllerImpl.attach() 里启动协程收集 `materialTypeState`，
 * 当它满足/不满足条件时调用：
 *   _progress.observeForever(seekBarObserver)  // 注册
 *   _progress.removeObserver(seekBarObserver)  // 注销
 *
 * AOD 模式下 materialTypeState 变成不满足条件，Observer 被移除，进度卡住。
 *
 * 方案：
 * 1. Hook LiveData.removeObserver —— 如果移除的是 seekBarObserver 就拦截，不让移除
 * 2. Hook seekBarChanged() —— 绕过 panelAnimating 检查（双保险）
 */
object MediaProgressHook {

    private const val TAG = "HyperLockMusic_MediaProgress"

    private const val VIEW_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
    private const val PROGRESS_CLASS =
        "com.android.systemui.media.controls.ui.viewmodel.SeekBarViewModel\$Progress"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(classLoader: ClassLoader, module: XposedModule,
                logCb: (Int, String, String, Throwable?) -> Unit) {
        logCallback = logCb
        try {
            val controllerClass = Class.forName(VIEW_CONTROLLER_CLASS, false, classLoader)
            val progressClass = Class.forName(PROGRESS_CLASS, false, classLoader)

            // 拿到 LiveData 类
            val liveDataClass = Class.forName(
                "androidx.lifecycle.LiveData", false, classLoader
            )
            logI("LiveData class: ${liveDataClass.name}")

            // === Hook 1: LiveData.removeObserver —— 拦截移除 seekBarObserver ===
            val removeObserverMethod = findMethodUp(liveDataClass, "removeObserver")
            if (removeObserverMethod != null) {
                module.deoptimize(removeObserverMethod)
                module.hook(removeObserverMethod).intercept { chain ->
                    if (!aodFullMediaEnabled()) {
                        chain.proceed()
                        return@intercept null
                    }
                    val observer = chain.args[0]
                    val observerClassName = observer?.javaClass?.name ?: ""
                    // 匹配 seekBarObserver 的类名
                    if (observer != null && isSeekBarObserver(observerClassName)) {
                        logI("removeObserver intercepted: $observerClassName")
                        null // 拦截，不执行移除
                    } else {
                        chain.proceed()
                        null
                    }
                }
                logI("hook LiveData.removeObserver: OK")
            } else {
                logE("removeObserver method not found")
            }

            // === Hook 2: seekBarChanged —— 绕过 panelAnimating 检查 ===
            try {
                val seekBarChangedMethod = controllerClass.declaredMethods.firstOrNull { m ->
                    m.name == "seekBarChanged" &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == progressClass
                }?.apply { isAccessible = true }

                if (seekBarChangedMethod != null) {
                    module.deoptimize(seekBarChangedMethod)
                    module.hook(seekBarChangedMethod).intercept { chain ->
                        if (!aodFullMediaEnabled()) {
                            chain.proceed()
                            return@intercept null
                        }
                        try {
                            val controller = chain.thisObject
                            val holder = getFieldValue(controller, "holder")
                            if (holder == null) {
                                chain.proceed()
                            } else {
                                val panelAnimatingField = HookUtils.findField(
                                    controller.javaClass, "panelAnimating"
                                )
                                val oldValue = panelAnimatingField?.getBoolean(controller) ?: false
                                if (oldValue) {
                                    panelAnimatingField?.setBoolean(controller, false)
                                }
                                try {
                                    chain.proceed()
                                } finally {
                                    if (oldValue) {
                                        panelAnimatingField?.setBoolean(controller, true)
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                            chain.proceed()
                        }
                        null
                    }
                    logI("hook seekBarChanged: OK")
                } else {
                    logE("seekBarChanged method not found")
                }
            } catch (e: Throwable) {
                logE("hook seekBarChanged failed: ${e.message}", e)
            }

            logI("MediaProgressHook installed successfully")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun aodFullMediaEnabled(): Boolean {
        val ctx = HookUtils.systemUiApplicationContext() ?: return true
        return ConfigReader.aodFullMedia(ctx)
    }

    private fun isSeekBarObserver(className: String): Boolean {
        return className.contains("seekBarObserver", ignoreCase = true) ||
            className.contains("SeekBarObserver") ||
            className.contains("\$seekBarObserver\$")
    }

    private fun findMethodUp(clazz: Class<*>, name: String): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val m = c.declaredMethods.firstOrNull {
                    it.name == name && it.parameterTypes.size == 1
                }
                if (m != null) return m
            } catch (_: Throwable) {
            }
            c = c.superclass
        }
        return null
    }

    private fun getFieldValue(obj: Any, fieldName: String): Any? {
        return try {
            val f: Field? = HookUtils.findField(obj.javaClass, fieldName)
            f?.isAccessible = true
            f?.get(obj)
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}