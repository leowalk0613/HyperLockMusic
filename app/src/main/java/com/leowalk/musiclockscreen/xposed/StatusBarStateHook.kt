package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 状态栏状态 Hook（HyperOS 4）
 *
 * - STATUS_KEYGUARD(1)：干净锁屏 → 音乐锁屏时 hide 普通通知
 * - STATUS_SHADE(0) / STATUS_SHADE_LOCKED(2)：通知中心或解锁 → 立刻 release
 */
object StatusBarStateHook {

    private const val TAG = "HyperLockMusic_StatusBarState"

    private var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun install(
        classLoader: ClassLoader,
        module: XposedModule,
        logCb: (Int, String, String, Throwable?) -> Unit,
    ) {
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
                        LockscreenNotificationController.setStatusBarState(newState)
                        when (newState) {
                            NotificationReleasePolicy.STATUS_SHADE,
                            NotificationReleasePolicy.STATUS_SHADE_LOCKED,
                            -> onShadeOrLocked(newState)

                            NotificationReleasePolicy.STATUS_KEYGUARD -> onKeyguard()
                        }
                        logI("setState -> $newState")
                    }
                } catch (e: Throwable) {
                    logE("setState intercept error", e)
                }
                result
            }
            logI("hooked ${controllerClass.name}.${setState.name}")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun onShadeOrLocked(state: Int) {
        LockscreenNotificationController.setNotificationShadeOpen(true)
        MusicLockscreenManager.hideTransitionMaskImmediately()
        MusicLockscreenManager.pauseAlbumOverlay()
        MediaFollowController.onMusicLockscreenHidden()
        KeepScreenController.sync()
        (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onLeftKeyguard()
        MusicLockscreenManager.lyricView?.setShadeOpen(true)
        MediaKeyguardButtonHook.refreshSlots(onKeyguard = false)
        LockscreenClockController.sync()
        SystemWallpaperBlurController.sync()
        if (NotificationReleasePolicy.shouldReleaseOnStatusBarState(
                newState = state,
                phase = LockscreenNotificationController.hidePhase(),
            )
        ) {
            LockscreenNotificationController.releaseToSystemUi()
            logI("state=$state -> release notification stack")
        } else {
            logI("state=$state (nothing to release, phase=${LockscreenNotificationController.hidePhase()})")
        }
        NumStateViewController.syncVisibility()
    }

    private fun onKeyguard() {
        LockscreenNotificationController.setNotificationShadeOpen(false)
        MusicLockscreenManager.lyricView?.setShadeOpen(false)
        MediaKeyguardButtonHook.refreshSlots(onKeyguard = true)
        if (WallpaperController.isShowing()) {
            LockscreenNotificationController.forceHideNormalNotifications()
            LockscreenNotificationController.syncKeyguardOverlayVisibility()
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onKeyguardShown()
            MediaFollowController.onKeyguardShown()
            KeepScreenController.sync()
            val ctx = MusicLockscreenManager.lyricView?.context
            if (ctx != null) {
                WallpaperController.refreshMusicWallpaper(ctx)
                SystemWallpaperBlurController.sync(ctx)
            }
            logI("keyguard shown -> resume music lockscreen UI")
        }
        LockscreenClockController.sync()
        NumStateViewController.syncVisibility()
    }

    private fun findControllerClass(classLoader: ClassLoader): Class<*>? {
        val candidates = listOf(
            "com.android.systemui.statusbar.StatusBarStateControllerImpl",
            "com.android.systemui.statusbar.policy.StatusBarStateControllerImpl",
            "com.android.systemui.statusbar.policy.StatusBarStateController",
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
