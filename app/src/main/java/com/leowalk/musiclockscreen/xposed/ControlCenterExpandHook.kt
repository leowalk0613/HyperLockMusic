package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy

/**
 * 挂钩 HyperOS 控制中心展开（[ControlCenterImpl] / [ShadeWrapper.OnExpandChangedListener]）。
 *
 * 反编译：CC 展开不改 STATUS_SHADE，音乐锁屏下 StatusBar 仍为 KEYGUARD；
 * 若不暂停过滤与 overlay，NSSL onLayout 会持续 scheduleRemove 导致下拉掉帧。
 */
object ControlCenterExpandHook {

    private const val TAG = "HyperLockMusic_CCExpand"
    private const val CC_IMPL = "com.miui.systemui.controlcenter.ControlCenterImpl"
    private const val LISTENER =
        "com.miui.interfaces.shade.ShadeWrapper\$OnExpandChangedListener"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null
    private var registered = false

    fun install(
        classLoader: ClassLoader,
        module: XposedModule,
        logCb: (Int, String, String, Throwable?) -> Unit,
    ) {
        logCallback = logCb
        try {
            val ccClass = Class.forName(CC_IMPL, false, classLoader)
            val start = ccClass.declaredMethods.firstOrNull {
                it.name == "start" && it.parameterCount == 0
            }?.apply { isAccessible = true }
            if (start == null) {
                logE("ControlCenterImpl.start() not found")
                return
            }
            module.hook(start).intercept { chain ->
                val result = chain.proceed()
                try {
                    registerListener(chain.thisObject, classLoader)
                } catch (e: Throwable) {
                    logE("register CC listener error", e)
                }
                result
            }
            logI("hooked ControlCenterImpl.start")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun registerListener(cc: Any?, classLoader: ClassLoader) {
        if (cc == null || registered) return
        val listenerClass = Class.forName(LISTENER, false, classLoader)
        val addCallback = cc.javaClass.methods.firstOrNull {
            it.name == "addCallback" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0].isAssignableFrom(listenerClass)
        } ?: cc.javaClass.declaredMethods.firstOrNull {
            it.name == "addCallback" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0].isAssignableFrom(listenerClass)
        }?.apply { isAccessible = true }
        if (addCallback == null) {
            logE("addCallback not found on ${cc.javaClass.name}")
            return
        }
        val listener = Proxy.newProxyInstance(
            classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            if (method.name == "onExpandChanged" && args != null && args.isNotEmpty()) {
                val expanded = args[0] as? Boolean ?: return@newProxyInstance null
                onExpandChanged(expanded)
            }
            null
        }
        addCallback.invoke(cc, listener)
        registered = true
        logI("ControlCenter OnExpandChangedListener registered")
    }

    private fun onExpandChanged(expanded: Boolean) {
        try {
            LockscreenNotificationController.setControlCenterOpen(expanded)
            if (expanded) {
                // 与 STATUS_SHADE 暂停路径对齐，但不 release 通知（避免展开动画中 snapVisible）
                MusicLockscreenManager.hideTransitionMaskImmediately()
                MusicLockscreenManager.pauseAlbumOverlay()
                MediaFollowController.onMusicLockscreenHidden()
                KeepScreenController.sync()
                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.setShadeOpen(true)
                MediaKeyguardButtonHook.refreshSlots(onKeyguard = false)
                LockscreenClockController.sync()
                SystemWallpaperBlurController.sync()
                NumStateViewController.syncVisibility()
                logI("control center expanded -> pause music LS work")
            } else {
                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.setShadeOpen(false)
                MediaKeyguardButtonHook.refreshSlots(onKeyguard = true)
                if (WallpaperController.isShowing()) {
                    LockscreenNotificationController.forceHideNormalNotifications()
                    LockscreenNotificationController.syncKeyguardOverlayVisibility()
                    (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onKeyguardShown()
                    MediaFollowController.onKeyguardShown()
                    KeepScreenController.sync()
                    logI("control center collapsed -> resume music LS UI")
                }
                LockscreenClockController.sync()
                NumStateViewController.syncVisibility()
            }
        } catch (e: Throwable) {
            logE("onExpandChanged error", e)
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
            ?: android.util.Log.i(TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
            ?: if (e != null) android.util.Log.e(TAG, msg, e) else android.util.Log.e(TAG, msg)
    }
}
