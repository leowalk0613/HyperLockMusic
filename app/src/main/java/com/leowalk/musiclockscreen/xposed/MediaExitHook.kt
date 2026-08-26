package com.leowalk.musiclockscreen.xposed

import android.content.Context
import io.github.libxposed.api.XposedModule

/**
 * 媒体结束时自动退出音乐锁屏（划掉媒体、杀 app、无活跃会话等）。
 */
object MediaExitHook {

    private const val TAG = "HyperLockMusic_MediaExit"

    private var module: XposedModule? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module

        hookSetTopMediaData(classLoader, module)
        hookBindMediaData(classLoader, module)
        hookCarouselRemovePlayer(classLoader, module)
    }

    private fun hookSetTopMediaData(classLoader: ClassLoader, module: XposedModule) {
        try {
            val ncClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaNotificationControllerImpl",
                false,
                classLoader
            )
            val mediaDataClass = Class.forName(
                "com.android.systemui.media.controls.shared.model.MediaData",
                false,
                classLoader
            )
            val contextField = ncClass.getDeclaredField("context").apply { isAccessible = true }

            val method = ncClass.declaredMethods.firstOrNull {
                it.name.contains("setTopMediaData") && it.parameterTypes.size == 2
            } ?: run {
                logE("setTopMediaData accessor not found")
                return
            }
            method.isAccessible = true

            module.hook(method).intercept { chain ->
                val mediaData = chain.args.getOrNull(1)
                val result = chain.proceed()
                if (mediaData == null) {
                    val ctx = contextField.get(chain.args[0]) as? Context
                    logI("topMediaData cleared")
                    tryExitMusicLockscreen(ctx)
                }
                result
            }
            logI("hooked ${method.name}")
        } catch (e: Throwable) {
            logE("hookSetTopMediaData failed", e)
        }
    }

    private fun hookBindMediaData(classLoader: ClassLoader, module: XposedModule) {
        try {
            val vcClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                false,
                classLoader
            )
            val mediaDataClass = Class.forName(
                "com.android.systemui.media.controls.shared.model.MediaData",
                false,
                classLoader
            )
            val holderField = vcClass.getDeclaredField("holder").apply { isAccessible = true }
            val playerField = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
                false,
                classLoader
            ).getDeclaredField("player").apply { isAccessible = true }

            val bindMethod = vcClass.getDeclaredMethod("bindMediaData", mediaDataClass)
            module.hook(bindMethod).intercept { chain ->
                val mediaData = chain.args.getOrNull(0)
                val result = chain.proceed()
                val ctx = resolveContext(chain.thisObject, holderField, playerField)
                when {
                    mediaData == null -> {
                        logI("bindMediaData(null)")
                        tryExitMusicLockscreen(ctx)
                    }
                    shouldExitForInactiveMedia(mediaData) -> {
                        logI("bindMediaData inactive")
                        tryExitMusicLockscreen(ctx)
                    }
                }
                result
            }
            logI("hooked bindMediaData for media exit")
        } catch (e: Throwable) {
            logE("hookBindMediaData failed", e)
        }
    }

    private fun hookCarouselRemovePlayer(classLoader: ClassLoader, module: XposedModule) {
        try {
            val carouselClass = Class.forName(
                "com.android.systemui.media.controls.ui.controller.MediaCarouselController",
                false,
                classLoader
            )
            val contextField = carouselClass.getDeclaredField("context").apply { isAccessible = true }
            val removePlayerMethod = carouselClass.getDeclaredMethod(
                "removePlayer",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java
            )

            module.hook(removePlayerMethod).intercept { chain ->
                val result = chain.proceed()
                val ctx = contextField.get(chain.thisObject) as? Context
                logI("MediaCarouselController.removePlayer")
                tryExitMusicLockscreen(ctx)
                result
            }
            logI("hooked MediaCarouselController.removePlayer")
        } catch (e: Throwable) {
            logE("hookCarouselRemovePlayer failed", e)
        }
    }

    private fun resolveContext(
        controller: Any,
        holderField: java.lang.reflect.Field,
        playerField: java.lang.reflect.Field
    ): Context? {
        return try {
            val holder = holderField.get(controller) ?: return null
            val player = playerField.get(holder) as? android.view.View
            player?.context
        } catch (_: Throwable) {
            null
        }
    }

    private fun shouldExitForInactiveMedia(mediaData: Any): Boolean {
        return try {
            val activeField = mediaData.javaClass.getDeclaredField("active").apply { isAccessible = true }
            !activeField.getBoolean(mediaData)
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryExitMusicLockscreen(context: Context?) {
        if (context == null) return
        if (!WallpaperController.isShowing()) return
        if (!HookUtils.isOnKeyguard(context)) return
        WallpaperController.restoreOriginalWallpaper(context.applicationContext)
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            module?.log(android.util.Log.ERROR, TAG, msg, e)
        } else {
            module?.log(android.util.Log.ERROR, TAG, msg)
        }
    }
}
