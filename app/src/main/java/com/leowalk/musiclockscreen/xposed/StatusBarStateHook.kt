package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 状态栏状态 Hook（HyperOS 4）
 *
 * OS4 锁屏即通知中心；锁屏上展开通知列表时 SystemUI 也会进入 STATUS_SHADE：
 * - STATUS_KEYGUARD：普通锁屏 → 勿扰可显示（非音乐锁屏时）
 * - STATUS_SHADE：通知中心 / 已解锁 → 隐藏勿扰与音乐锁屏 overlay
 */
object StatusBarStateHook {

    private const val TAG = "HyperLockMusic_StatusBarState"
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
                        when (newState) {
                            STATUS_SHADE -> {
                                // 已离开锁屏，或 OS4 锁屏上展开通知中心
                                LockscreenNotificationController.setNotificationShadeOpen(true)
                                MusicLockscreenManager.hideTransitionMaskImmediately()
                                MusicLockscreenManager.pauseAlbumOverlay()
                                MediaFollowController.onMusicLockscreenHidden()
                                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onLeftKeyguard()
                                MusicLockscreenManager.lyricView?.setShadeOpen(true)
                                MediaKeyguardButtonHook.refreshSlots(onKeyguard = false)
                                val ctx = MusicLockscreenManager.lyricView?.context
                                if (ctx != null && !HookUtils.isOnKeyguard(ctx)) {
                                    LockscreenNotificationController.showAllNotifications()
                                    logI("left keyguard -> restore notifications, pause music lockscreen UI")
                                } else {
                                    logI("notification shade open on keyguard")
                                }
                                NumStateViewController.syncVisibility()
                            }
                            STATUS_KEYGUARD -> {
                                LockscreenNotificationController.setNotificationShadeOpen(false)
                                MusicLockscreenManager.lyricView?.setShadeOpen(false)
                                MediaKeyguardButtonHook.refreshSlots(onKeyguard = true)
                                if (WallpaperController.isShowing()) {
                                    LockscreenNotificationController.forceHideNormalNotifications()
                                    LockscreenNotificationController.syncKeyguardOverlayVisibility()
                                    (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onKeyguardShown()
                                    MediaFollowController.onKeyguardShown()
                                    // 亮屏/回到锁屏时同步壁纸与歌词背景（曲目未变也会补雾状背景）
                                    val ctx = MusicLockscreenManager.lyricView?.context
                                    if (ctx != null && HookUtils.isScreenInteractive(ctx)) {
                                        WallpaperController.refreshMusicWallpaper(ctx)
                                    } else if (ctx != null) {
                                        WallpaperController.ensureLyricFogReady()
                                    }
                                    logI("keyguard shown -> resume music lockscreen UI")
                                }
                                NumStateViewController.syncVisibility()
                            }
                        }
                        logI("setState -> $newState")
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
