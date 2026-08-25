package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * 通用工具函数
 */
object HookUtils {

    /**
     * dp 转 px（Int）
     */
    fun dpToPxInt(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * dp 转 px（Float）
     */
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    /**
     * 通过资源 id 名称查找 View
     */
    fun findViewByIdByName(root: ViewGroup, idName: String, defType: String = "id"): View? {
        return try {
            val context = root.context
            val id = context.resources.getIdentifier(idName, defType, context.packageName)
            if (id != 0) root.findViewById(id) else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 通过类名递归查找 View
     */
    fun findViewByClassName(root: ViewGroup, className: String): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.javaClass.simpleName == className ||
                child.javaClass.name.contains(className, true)) {
                return child
            }
            if (child is ViewGroup) {
                val found = findViewByClassName(child, className)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 递归查找类的声明字段（包括父类），自动设置 accessible
     */
    fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            try {
                return c.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    /**
     * 判断当前是否在锁屏
     */
    fun isOnKeyguard(context: Context): Boolean {
        return try {
            val km = context.getSystemService(android.app.KeyguardManager::class.java)
            km?.isKeyguardLocked == true
        } catch (_: Throwable) {
            false
        }
    }

    /** 从 SystemUI MediaData 反射读取 packageName */
    fun packageFromMediaData(mediaData: Any?): String? {
        if (mediaData == null) return null
        return try {
            val field = findField(mediaData.javaClass, "packageName")
            (field?.get(mediaData) as? String)?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    /** 当前活跃媒体会话的包名（取第一个活跃会话） */
    fun currentMediaPackage(context: Context): String? {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? android.media.session.MediaSessionManager ?: return null
            mgr.getActiveSessions(null).firstOrNull()?.packageName
        } catch (_: Throwable) {
            null
        }
    }

    /** 白名单关闭时一律允许；开启时仅白名单内应用允许。 */
    fun isAllowedMusicApp(context: Context, packageName: String? = currentMediaPackage(context)): Boolean {
        return ConfigReader.isAllowedMusicApp(context, packageName)
    }
}
