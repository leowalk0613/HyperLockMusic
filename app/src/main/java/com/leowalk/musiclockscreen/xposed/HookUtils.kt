package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

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

    /** 锁屏壁纸目标尺寸（含导航栏区域，避免底部露黑） */
    fun lockScreenWallpaperSize(context: Context): Pair<Int, Int> {
        val dm = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    /** 遮罩/壁纸 BitmapDrawable 铺满全屏 */
    fun fillDrawable(context: Context, bitmap: android.graphics.Bitmap): BitmapDrawable {
        return BitmapDrawable(context.resources, bitmap).apply {
            gravity = Gravity.FILL
        }
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
        return findAllViewsByClassName(root, className).firstOrNull()
    }

    /** 收集所有匹配类名的 View（HyperOS 通知区可能有多处 NotificationNumStateView）。 */
    fun findAllViewsByClassName(root: ViewGroup, className: String): List<View> {
        val out = ArrayList<View>()
        collectViewsByClassName(root, className, out)
        return out
    }

    private fun collectViewsByClassName(root: ViewGroup, className: String, out: MutableList<View>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.javaClass.simpleName == className ||
                child.javaClass.name.contains(className, ignoreCase = true)
            ) {
                out.add(child)
            }
            if (child is ViewGroup) {
                collectViewsByClassName(child, className, out)
            }
        }
    }

    /** 收集所有匹配资源 id 的 View。 */
    fun findAllViewsByIdName(root: ViewGroup, idName: String, defType: String = "id"): List<View> {
        return try {
            val context = root.context
            val id = context.resources.getIdentifier(idName, defType, context.packageName)
            if (id == 0) emptyList() else findAllViewsById(root, id)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun findAllViewsById(root: ViewGroup, id: Int): List<View> {
        val out = ArrayList<View>()
        collectViewsById(root, id, out)
        return out
    }

    private fun collectViewsById(root: ViewGroup, id: Int, out: MutableList<View>) {
        if (root.id == id) {
            out.add(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.id == id) {
                out.add(child)
            }
            if (child is ViewGroup) {
                collectViewsById(child, id, out)
            }
        }
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

    /** 屏幕是否处于点亮/可交互状态（息屏、AOD 过渡时为 false）。 */
    fun isScreenInteractive(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isInteractive == true
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * 允许写锁屏壁纸 / 刷新歌词雾状背景。
     * 必须在锁屏；屏幕点亮时一律允许；
     * 音乐锁屏已开启时，AOD/息屏也允许切歌刷新（否则亮屏前壁纸与专辑会卡住）。
     */
    fun canApplyLockWallpaper(context: Context): Boolean {
        if (!isOnKeyguard(context)) return false
        if (isScreenInteractive(context)) return true
        return try {
            WallpaperController.isShowing() || MusicLockscreenManager.isShowing
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
            com.leowalk.musiclockscreen.MediaSessionAccess
                .getActiveControllers(context)
                .firstOrNull()?.packageName
        } catch (_: Throwable) {
            null
        }
    }

    /** 白名单关闭时一律允许；开启时仅白名单内应用允许。 */
    fun isAllowedMusicApp(context: Context, packageName: String? = currentMediaPackage(context)): Boolean {
        return ConfigReader.isAllowedMusicApp(context, packageName)
    }

    /** 锁屏密码 / 图案输入界面是否正在显示（与专辑、歌词隐藏条件一致）。 */
    fun isBouncerShowing(anchor: View?): Boolean {
        if (anchor == null) return false
        return try {
            val root = anchor.rootView ?: return false
            val pkg = anchor.context.packageName
            val res = root.resources
            val names = arrayOf(
                "keyguard_bouncer_container",
                "keyguard_security_container",
                "miui_keyguard_bouncer_container",
                "security_container"
            )
            for (name in names) {
                val id = res.getIdentifier(name, "id", pkg)
                if (id == 0) continue
                val v = root.findViewById<View>(id) ?: continue
                if (v.visibility == View.VISIBLE && v.isShown && v.height > 0) return true
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    /** SystemUI 进程 Application 上下文（隐藏 API，反射获取）。 */
    fun systemUiApplicationContext(): Context? {
        return try {
            val at = Class.forName("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? Context
        } catch (_: Throwable) {
            null
        }
    }
}
