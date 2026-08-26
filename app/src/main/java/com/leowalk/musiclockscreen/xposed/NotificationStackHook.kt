package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 音乐锁屏期间持续过滤通知行（layout 结束后 post，避免死循环）。
 * OS4 锁屏即通知中心，仅在 [HookUtils.isOnKeyguard] 时过滤。
 * 分类逻辑见 [NotificationStackChildClassifier]（对齐反编译）。
 */
class NotificationStackHook {

    private val tag = "MusicLockScreen_StackHook"
    private var module: XposedModule? = null
    private var lastIsShowing = false

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        try {
            val stackClass = Class.forName(
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
                false,
                classLoader
            )

            val onLayoutMethod = findDeclaredMethod(
                stackClass, "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            if (onLayoutMethod != null) {
                module.hook(onLayoutMethod).intercept { chain ->
                    val parent = chain.thisObject as ViewGroup
                    chain.proceed()
                    try {
                        parent.post { filterNotificationChildren(parent) }
                    } catch (e: Throwable) {
                        logE("onLayout post error", e)
                    }
                    null
                }
            }

            logI("NotificationStackHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun filterNotificationChildren(parent: ViewGroup) {
        val showing = WallpaperController.isShowing()

        if (showing != lastIsShowing) {
            lastIsShowing = showing
            if (!showing) {
                SystemNotificationAnimator.reset()
                restoreAllRows(parent)
            }
        }

        if (!LockscreenNotificationController.shouldFilterNotifications()) {
            return
        }

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            when {
                NotificationStackChildClassifier.isMiuiMediaHeaderView(child) -> {
                    ensureVisible(child)
                }
                NotificationStackChildClassifier.shouldHideNotificationRow(child) -> {
                    SystemNotificationAnimator.scheduleRemove(parent, child)
                }
                NotificationStackChildClassifier.isExpandableNotificationRow(child) -> {
                    ensureVisible(child)
                }
            }
        }
    }

    private fun restoreAllRows(parent: ViewGroup) {
        try {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (NotificationStackChildClassifier.isMiuiMediaHeaderView(child) ||
                    NotificationStackChildClassifier.isExpandableNotificationRow(child)
                ) {
                    SystemNotificationAnimator.expandInPlace(child)
                    ensureVisible(child)
                }
            }
        } catch (e: Throwable) {
            logE("restoreAllRows error", e)
        }
    }

    private fun ensureVisible(child: View) {
        if (child.visibility != View.VISIBLE || child.alpha != 1f || child.scaleY != 1f) {
            child.animate().cancel()
            child.visibility = View.VISIBLE
            child.alpha = 1f
            child.scaleX = 1f
            child.scaleY = 1f
        }
    }

    private fun findDeclaredMethod(
        clazz: Class<*>,
        name: String,
        vararg paramTypes: Class<*>?
    ): Method? {
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            try {
                return c.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        return null
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) module?.log(android.util.Log.ERROR, tag, msg, e)
        else module?.log(android.util.Log.ERROR, tag, msg)
    }
}
