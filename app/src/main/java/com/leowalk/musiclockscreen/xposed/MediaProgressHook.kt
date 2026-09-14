package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * AOD 媒体进度条实时更新 Hook（SystemUI 进程）
 *
 * HyperOS 17.03（SystemUI 17.03.260226.r）起进度链路变更：
 * - 旧：controller.seekBarObserver + LiveData.observeForever/removeObserver + seekBarChanged
 * - 新：`shouldObserve = hasHolder && isAwake` → [MiuiMediaSeekBarProgressOwner]
 *       register/unregister + onRenderState；unregister 还会 setListening(false) 停 polling
 *
 * AOD 下 isAwake=false → unregister → 进度卡住。
 *
 * 方案：
 * 1. 拦截 ProgressOwner.unregister（holder 仍在时）
 * 2. attach 后若已息屏则强制 register
 * 3. onRenderState / seekBarChanged 绕过 panelAnimating
 * 4. LiveData.removeObserver 兼容新旧 Observer 名（双保险）
 */
object MediaProgressHook {

    private const val TAG = "HyperLockMusic_MediaProgress"

    private const val VIEW_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
    private const val PROGRESS_OWNER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaSeekBarProgressOwner"
    private const val PROGRESS_CLASS =
        "com.android.systemui.media.controls.ui.viewmodel.SeekBarViewModel\$Progress"
    private const val RENDER_STATE_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiSeekBarRenderState"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(classLoader: ClassLoader, module: XposedModule,
                logCb: (Int, String, String, Throwable?) -> Unit) {
        logCallback = logCb
        try {
            val controllerClass = Class.forName(VIEW_CONTROLLER_CLASS, false, classLoader)

            hookProgressOwnerUnregister(classLoader, module)
            hookAttachForceRegister(controllerClass, module)
            hookOnRenderState(controllerClass, classLoader, module)
            hookSeekBarChangedCompat(controllerClass, classLoader, module)
            hookLiveDataRemoveObserver(classLoader, module)

            logI("MediaProgressHook installed successfully")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    /** 新链路：息屏时不让 ProgressOwner 注销 consumer / 停 listening。 */
    private fun hookProgressOwnerUnregister(classLoader: ClassLoader, module: XposedModule) {
        try {
            val ownerClass = Class.forName(PROGRESS_OWNER_CLASS, false, classLoader)
            val unregister = ownerClass.declaredMethods.firstOrNull { m ->
                m.name == "unregister" && m.parameterTypes.size == 1
            }?.apply { isAccessible = true }

            if (unregister == null) {
                logE("MiuiMediaSeekBarProgressOwner.unregister not found")
                return
            }
            module.deoptimize(unregister)
            module.hook(unregister).intercept { chain ->
                if (!shouldKeepAodProgress()) {
                    chain.proceed()
                    return@intercept null
                }
                val consumer = chain.args.getOrNull(0) ?: run {
                    chain.proceed()
                    return@intercept null
                }
                val hasHolder = getFieldValue(consumer, "holder") != null
                if (MediaAodProgressPolicy.shouldInterceptUnregister(true, hasHolder)) {
                    null
                } else {
                    chain.proceed()
                    null
                }
            }
            logI("hook ProgressOwner.unregister: OK")
        } catch (e: ClassNotFoundException) {
            logI("MiuiMediaSeekBarProgressOwner absent (old SystemUI)")
        } catch (e: Throwable) {
            logE("hook ProgressOwner.unregister failed: ${e.message}", e)
        }
    }

    /** AOD 下 attach 时 shouldObserve 不会 register，主动补一次。 */
    private fun hookAttachForceRegister(controllerClass: Class<*>, module: XposedModule) {
        try {
            val attach = controllerClass.declaredMethods.firstOrNull { m ->
                m.name == "attach" && m.parameterTypes.size == 1
            }?.apply { isAccessible = true }
            if (attach == null) {
                logE("attach method not found")
                return
            }
            module.deoptimize(attach)
            module.hook(attach).intercept { chain ->
                chain.proceed()
                if (!MediaAodProgressPolicy.shouldForceRegisterAfterAttach(shouldKeepAodProgress())) {
                    return@intercept null
                }
                try {
                    val controller = chain.thisObject ?: return@intercept null
                    val owner = getFieldValue(controller, "seekBarProgressOwner") ?: return@intercept null
                    val register = owner.javaClass.declaredMethods.firstOrNull { m ->
                        m.name == "register" && m.parameterTypes.size == 1
                    }?.apply { isAccessible = true }
                    register?.invoke(owner, controller)
                } catch (e: Throwable) {
                    logE("force register failed: ${e.message}", e)
                }
                null
            }
            logI("hook attach force-register: OK")
        } catch (e: Throwable) {
            logE("hook attach failed: ${e.message}", e)
        }
    }

    /** 新链路：onRenderState 绕过 panelAnimating。 */
    private fun hookOnRenderState(
        controllerClass: Class<*>,
        classLoader: ClassLoader,
        module: XposedModule,
    ) {
        try {
            val renderStateClass = try {
                Class.forName(RENDER_STATE_CLASS, false, classLoader)
            } catch (_: ClassNotFoundException) {
                logI("MiuiSeekBarRenderState absent (old SystemUI)")
                return
            }
            val method = controllerClass.declaredMethods.firstOrNull { m ->
                m.name == "onRenderState" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == renderStateClass
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("onRenderState method not found")
                return
            }
            module.deoptimize(method)
            module.hook(method).intercept { chain ->
                interceptPanelAnimatingGate(chain)
            }
            logI("hook onRenderState: OK")
        } catch (e: Throwable) {
            logE("hook onRenderState failed: ${e.message}", e)
        }
    }

    /** 旧链路兼容：seekBarChanged。 */
    private fun hookSeekBarChangedCompat(
        controllerClass: Class<*>,
        classLoader: ClassLoader,
        module: XposedModule,
    ) {
        try {
            val progressClass = try {
                Class.forName(PROGRESS_CLASS, false, classLoader)
            } catch (_: ClassNotFoundException) {
                return
            }
            val seekBarChangedMethod = controllerClass.declaredMethods.firstOrNull { m ->
                m.name == "seekBarChanged" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == progressClass
            }?.apply { isAccessible = true }

            if (seekBarChangedMethod == null) {
                logI("seekBarChanged absent (new SystemUI uses onRenderState)")
                return
            }
            module.deoptimize(seekBarChangedMethod)
            module.hook(seekBarChangedMethod).intercept { chain ->
                interceptPanelAnimatingGate(chain)
            }
            logI("hook seekBarChanged: OK")
        } catch (e: Throwable) {
            logE("hook seekBarChanged failed: ${e.message}", e)
        }
    }

    private fun interceptPanelAnimatingGate(
        chain: io.github.libxposed.api.XposedInterface.Chain,
    ): Any? {
        if (!MediaAodProgressPolicy.shouldBypassPanelAnimating(shouldKeepAodProgress())) {
            chain.proceed()
            return null
        }
        try {
            val controller = chain.thisObject
            if (controller == null || getFieldValue(controller, "holder") == null) {
                chain.proceed()
                return null
            }
            val panelAnimatingField = HookUtils.findField(controller.javaClass, "panelAnimating")
            if (panelAnimatingField != null && panelAnimatingField.getBoolean(controller)) {
                panelAnimatingField.setBoolean(controller, false)
                try {
                    chain.proceed()
                } finally {
                    panelAnimatingField.setBoolean(controller, true)
                }
            } else {
                chain.proceed()
            }
        } catch (_: Throwable) {
            chain.proceed()
        }
        return null
    }

    /** 双保险：拦截进度 Observer 被 remove（新旧类名）。 */
    private fun hookLiveDataRemoveObserver(classLoader: ClassLoader, module: XposedModule) {
        try {
            val liveDataClass = Class.forName("androidx.lifecycle.LiveData", false, classLoader)
            val removeObserverMethod = findMethodUp(liveDataClass, "removeObserver")
            if (removeObserverMethod == null) {
                logE("removeObserver method not found")
                return
            }
            module.deoptimize(removeObserverMethod)
            module.hook(removeObserverMethod).intercept { chain ->
                if (!shouldKeepAodProgress()) {
                    chain.proceed()
                    return@intercept null
                }
                val observer = chain.args.getOrNull(0)
                val observerClassName = observer?.javaClass?.name ?: ""
                if (observer != null &&
                    MediaAodProgressPolicy.isProgressObserverClass(observerClassName)
                ) {
                    null
                } else {
                    chain.proceed()
                    null
                }
            }
            logI("hook LiveData.removeObserver: OK")
        } catch (e: Throwable) {
            logE("hook LiveData.removeObserver failed: ${e.message}", e)
        }
    }

    private fun aodFullMediaEnabled(): Boolean {
        val ctx = HookUtils.systemUiApplicationContext() ?: return false
        return ConfigReader.aodFullMedia(ctx)
    }

    /** 开关开启且屏幕非交互（AOD/息屏）时才强留进度；亮屏走系统默认。 */
    private fun shouldKeepAodProgress(): Boolean {
        if (!aodFullMediaEnabled()) return false
        val ctx = HookUtils.systemUiApplicationContext() ?: return false
        return !HookUtils.isScreenInteractive(ctx)
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
