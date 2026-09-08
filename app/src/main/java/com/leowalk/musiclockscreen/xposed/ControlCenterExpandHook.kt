package com.leowalk.musiclockscreen.xposed

import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Proxy

/**
 * 挂钩 HyperOS 控制中心展开。
 * 展开回调里先置 flag 停 NSSL 过滤，重活 post 出去，避免卡住展开动画。
 */
object ControlCenterExpandHook {

    private const val TAG = "HyperLockMusic_CCExpand"
    private const val CC_IMPL = "com.miui.systemui.controlcenter.ControlCenterImpl"
    private const val LISTENER =
        "com.miui.interfaces.shade.ShadeWrapper\$OnExpandChangedListener"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null
    private var registered = false
    private val mainHandler = Handler(Looper.getMainLooper())

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
            // 最先置位：让同帧 NSSL onLayout hook 直接 return
            LockscreenNotificationController.setControlCenterOpen(expanded)
            if (expanded) {
                MusicLockscreenManager.lyricView?.setShadeOpen(true)
                // 重活离开动画帧，避免控制中心掉帧
                mainHandler.post {
                    try {
                        if (!LockscreenNotificationController.isControlCenterOpen()) return@post
                        MusicLockscreenManager.hideTransitionMaskImmediately()
                        MusicLockscreenManager.pauseAlbumOverlay()
                        MediaFollowController.onMusicLockscreenHidden()
                        MediaKeyguardButtonHook.refreshSlots(onKeyguard = false)
                        NumStateViewController.syncVisibility()
                        // 刻意不做 SystemWallpaperBlurController.sync / Clock.sync：反射+布局太重
                        logI("control center expanded -> paused music LS (deferred)")
                    } catch (e: Throwable) {
                        logE("CC expand deferred error", e)
                    }
                }
            } else {
                MusicLockscreenManager.lyricView?.setShadeOpen(false)
                mainHandler.post {
                    try {
                        if (LockscreenNotificationController.isControlCenterOpen()) return@post
                        MediaKeyguardButtonHook.refreshSlots(onKeyguard = true)
                        if (WallpaperController.isShowing()) {
                            // 已是 HIDDEN 则勿再全栈扫描 hide
                            if (!LockscreenNotificationController.isHidden()) {
                                LockscreenNotificationController.forceHideNormalNotifications()
                            } else {
                                LockscreenNotificationController.syncKeyguardOverlayVisibility()
                            }
                            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onKeyguardShown()
                            MediaFollowController.onKeyguardShown()
                            logI("control center collapsed -> resume music LS (deferred)")
                        }
                        NumStateViewController.syncVisibility()
                    } catch (e: Throwable) {
                        logE("CC collapse deferred error", e)
                    }
                }
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
