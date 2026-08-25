package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 状态栏状态 Hook：通知中心展开时隐藏歌词 overlay。
 */
object StatusBarStateHook {

    private const val TAG = "MusicLockScreen_StatusBarState"
    private const val STATUS_SHADE = 0
    private const val STATUS_KEYGUARD = 1

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(classLoader: ClassLoader, module: XposedModule,
                logCb: (Int, String, String, Throwable?) -> Unit) {
        logCallback = logCb
        try {
            val controllerClass = findControllerClass(classLoader) ?: run {
                logE("StatusBarStateControllerImpl not found")
                return
            }

            val setState = findSetStateMethod(controllerClass)
            if (setState == null) {
                logE("setState not found on ${controllerClass.name}")
                return
            }

            module.hook(setState).intercept { chain ->
                val result = chain.proceed()
                try {
                    val newState = chain.args.firstOrNull() as? Int
                    if (newState != null) {
                        val shadeOpen = newState != STATUS_KEYGUARD
                        MusicLockscreenManager.lyricView?.setShadeOpen(shadeOpen)
                        when (newState) {
                            STATUS_SHADE -> {
                                // 离开锁屏（解锁/通知中心展开）时立即隐藏过渡遮罩，
                                // 防止壁纸切换尚未完成时遮罩残留在非锁屏界面
                                MusicLockscreenManager.hideTransitionMaskImmediately()
                                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onLeftKeyguard()
                                LockscreenNotificationController.showAllNotifications()
                                MediaKeyguardButtonHook.refreshSlots(onKeyguard = false)
                                logI("unlocked -> pause music lockscreen UI")
                            }
                            STATUS_KEYGUARD -> {
                                MediaKeyguardButtonHook.refreshSlots(onKeyguard = true)
                                if (WallpaperController.isShowing()) {
                                    LockscreenNotificationController.forceHideNormalNotifications()
                                    (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onKeyguardShown()
                                    // 仅亮屏回到锁屏时刷新壁纸；息屏/AOD 切换不触发 setBitmap
                                    val ctx = MusicLockscreenManager.lyricView?.context
                                    if (ctx != null && HookUtils.isScreenInteractive(ctx)) {
                                        WallpaperController.refreshMusicWallpaper(ctx)
                                    }
                                    logI("keyguard shown -> resume music lockscreen UI")
                                }
                            }
                        }
                        logI("setState -> $newState shadeOpen=$shadeOpen")
                    }
                } catch (e: Throwable) {
                    logE("setState intercept error", e)
                }
                // HyperOS setState(int, boolean) 返回 boolean，不能 return null
                result
            }
            logI("hooked ${controllerClass.name}.${setState.name}")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun findControllerClass(classLoader: ClassLoader): Class<*>? {
        val candidates = listOf(
            "com.android.systemui.statusbar.StatusBarStateControllerImpl",
            "com.android.systemui.statusbar.policy.StatusBarStateControllerImpl",
            "com.android.systemui.statusbar.policy.StatusBarStateController"
        )
        for (name in candidates) {
            try {
                return Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun findSetStateMethod(clazz: Class<*>): Method? {
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.name != "setState") continue
                val pt = m.parameterTypes
                when {
                    pt.size == 2 &&
                        pt[0] == Int::class.javaPrimitiveType &&
                        pt[1] == Boolean::class.javaPrimitiveType -> {
                        return m.apply { isAccessible = true }
                    }
                    pt.size == 1 && pt[0] == Int::class.javaPrimitiveType -> {
                        return m.apply { isAccessible = true }
                    }
                }
            }
            c = c.superclass
        }
        return null
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
