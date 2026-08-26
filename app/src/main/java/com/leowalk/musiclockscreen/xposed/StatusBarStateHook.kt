package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 状态栏状态 Hook（HyperOS 4）
 *
 * OS4 无锁屏下拉通知中心，锁屏即通知中心：
 * - STATUS_KEYGUARD：锁屏界面（含通知列表）→ 音乐锁屏时隐藏普通通知
 * - STATUS_SHADE：已离开锁屏（解锁）→ 恢复通知、暂停音乐锁屏 UI
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
                        when (newState) {
                            STATUS_SHADE -> {
                                // 已离开锁屏（解锁）；OS4 不会在锁屏上单独展开 shade
                                MusicLockscreenManager.hideTransitionMaskImmediately()
                                MusicLockscreenManager.pauseAlbumOverlay()
                                MediaFollowController.onMusicLockscreenHidden()
                                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onLeftKeyguard()
                                MusicLockscreenManager.lyricView?.setShadeOpen(true)
                                MediaKeyguardButtonHook.refreshSlots(onKeyguard = false)
                                val ctx = MusicLockscreenManager.lyricView?.context
                                if (ctx != null && !HookUtils.isOnKeyguard(ctx)) {
                                    LockscreenNotificationController.showAllNotifications()
                                    NumStateViewController.show()
                                    logI("left keyguard -> restore notifications, pause music lockscreen UI")
                                } else {
                                    logI("STATUS_SHADE but keyguard still locked -> skip notification restore")
                                }
                            }
                            STATUS_KEYGUARD -> {
                                MusicLockscreenManager.lyricView?.setShadeOpen(false)
                                MediaKeyguardButtonHook.refreshSlots(onKeyguard = true)
                                if (WallpaperController.isShowing()) {
                                    LockscreenNotificationController.forceHideNormalNotifications()
                                    NumStateViewController.hide()
                                    MusicLockscreenManager.resumeAlbumOverlay()
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
