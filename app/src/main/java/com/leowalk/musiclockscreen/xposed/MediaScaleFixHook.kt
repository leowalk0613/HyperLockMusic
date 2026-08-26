package com.leowalk.musiclockscreen.xposed

import android.view.View
import io.github.libxposed.api.XposedModule

/**
 * 锁屏媒体控件 AOD 缩放拦截
 *
 * 原理（参考 HyperLyric 的 NotificationMediaFullAodHook + NotificationMediaFullAodAnimatedHeightHook）：
 * 1. Hook MiuiMediaViewControllerImpl.onFullAodStateChanged(boolean)
 *    — 进入 Full AOD 时将参数 true 替换为 false，让媒体控件保持展开状态
 * 2. Hook MiuiMediaHeaderView.setAnimateHeight(int)
 *    — 阻止 AOD 过渡动画中的高度压缩，让动画过程中也保持展开高度
 *
 * 两个 hook 配合，确保从动画开始到结束，媒体控件始终保持展开大小。
 */
class MediaScaleFixHook {

    private val tag = "HyperLockMusic_MediaScale"
    private var module: XposedModule? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        logI("install start")

        try {
            val viewControllerClassName =
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
            val mediaHeaderClassName =
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"

            // === Hook 1: onFullAodStateChanged ===
            try {
                val viewControllerClass = classLoader.loadClass(viewControllerClassName)
                logI("loaded ViewController class: $viewControllerClassName")

                val onFullAodMethod = viewControllerClass.declaredMethods.find { method ->
                    method.name == "onFullAodStateChanged" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == Boolean::class.javaPrimitiveType
                }?.apply { isAccessible = true }

                if (onFullAodMethod != null) {
                    module.deoptimize(onFullAodMethod)
                    module.hook(onFullAodMethod).intercept { chain ->
                        if (!aodFullMediaEnabled()) {
                            chain.proceed()
                        } else {
                            val enteringFullAod = chain.args.firstOrNull() as? Boolean == true
                            if (enteringFullAod) {
                                logI("onFullAodStateChanged(true) -> false")
                                chain.proceed(arrayOf<Any?>(false))
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    logI("hook onFullAodStateChanged: OK")
                } else {
                    logE("onFullAodStateChanged method not found")
                }
            } catch (e: Throwable) {
                logE("hook onFullAodStateChanged failed: ${e.message}")
            }

            // === Hook 2: setAnimateHeight (阻止高度压缩动画) ===
            try {
                val mediaHeaderClass = classLoader.loadClass(mediaHeaderClassName)
                logI("loaded MediaHeaderView class: $mediaHeaderClassName")

                val setAnimateHeightMethod = mediaHeaderClass.declaredMethods.find { method ->
                    method.name == "setAnimateHeight" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }

                if (setAnimateHeightMethod != null) {
                    module.deoptimize(setAnimateHeightMethod)
                    module.hook(setAnimateHeightMethod).intercept { chain ->
                        val requestedHeight = chain.args.firstOrNull() as? Int
                        if (!aodFullMediaEnabled()) {
                            chain.proceed()
                        } else if (requestedHeight == null || requestedHeight == 0) {
                            // 0 是完成/重置信号，必须放行
                            chain.proceed()
                        } else {
                            val mediaHeader = chain.thisObject as? View
                            val expandedHeight = mediaHeader?.getExpandedMediaHeight()
                            if (expandedHeight != null && expandedHeight > 0) {
                                logI("setAnimateHeight($requestedHeight) -> $expandedHeight")
                                chain.proceed(arrayOf<Any?>(expandedHeight))
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    logI("hook setAnimateHeight: OK")
                } else {
                    logE("setAnimateHeight method not found")
                    // 打印所有方法名方便排查
                    mediaHeaderClass.declaredMethods.forEach { m ->
                        logI("  method: ${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})")
                    }
                }
            } catch (e: Throwable) {
                logE("hook setAnimateHeight failed: ${e.message}")
            }

            logI("MediaScaleFixHook install done")
        } catch (e: Throwable) {
            logE("install failed: ${e.message}", e)
        }
    }

    private fun aodFullMediaEnabled(): Boolean {
        val ctx = HookUtils.systemUiApplicationContext() ?: return true
        return ConfigReader.aodFullMedia(ctx)
    }

    /**
     * 获取展开状态下的媒体卡片高度
     * 参考 HyperLyric: 读取系统资源 qs_media_session_height_expanded
     */
    private fun View.getExpandedMediaHeight(): Int? {
        val resourceId = resources.getIdentifier(
            "qs_media_session_height_expanded",
            "dimen",
            context.packageName
        )
        return resourceId.takeIf { it != 0 }?.let(resources::getDimensionPixelSize)
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
