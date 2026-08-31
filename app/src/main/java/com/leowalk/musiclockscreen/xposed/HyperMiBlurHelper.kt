package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.provider.Settings
import android.util.Log
import android.view.View
import java.lang.reflect.Method

/**
 * HyperOS / MIUI 锁屏时钟同款「透壁纸高斯模糊染色」View API（隐藏方法反射）。
 * 对照 SystemUI `com.miui.clock.utils.MiuiBlurUtils`。
 */
object HyperMiBlurHelper {

    private const val TAG = "HyperLockMusic_MiBlur"

    private val setPassWindowBlurEnabled: Method? by lazy {
        resolveView("setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType!!)
    }
    private val setMiBackgroundBlurMode: Method? by lazy {
        resolveView("setMiBackgroundBlurMode", Int::class.javaPrimitiveType!!)
    }
    private val setMiBackgroundBlurRadius: Method? by lazy {
        resolveView("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType!!)
    }
    private val setMiViewBlurMode: Method? by lazy {
        resolveView("setMiViewBlurMode", Int::class.javaPrimitiveType!!)
    }
    private val clearMiBackgroundBlendColor: Method? by lazy {
        resolveView("clearMiBackgroundBlendColor")
    }
    private val setMiBackgroundBlendColors: Method? by lazy {
        resolveView("setMiBackgroundBlendColors", ArrayList::class.java)
    }
    private val addMiBackgroundBlendColor: Method? by lazy {
        resolveView(
            "addMiBackgroundBlendColor",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
    }
    private val disableMiBackgroundContainBelow: Method? by lazy {
        resolveView("disableMiBackgroundContainBelow", Boolean::class.javaPrimitiveType!!)
    }

    /** 设备是否声明支持 background blur，且用户开关打开。 */
    fun isSupported(context: Context): Boolean {
        if (setMiViewBlurMode == null || setMiBackgroundBlendColors == null) {
            return false
        }
        if (!systemPropBoolean("persist.sys.background_blur_supported", false) &&
            systemPropInt("persist.sys.background_blur_version", 0) <= 0
        ) {
            // 部分机型只暴露 View 方法；方法在就允许尝试
            logI("prop blur unsupported, but View APIs present — allow try")
        }
        return try {
            Settings.Secure.getInt(context.contentResolver, "background_blur_enable", 0) == 1 ||
                // 时钟在部分 ROM 上即使 secure 为 0 仍可用；API 在则放行，失败再降级
                setMiViewBlurMode != null
        } catch (_: Throwable) {
            setMiViewBlurMode != null
        }
    }

    /**
     * 给文字 View 套时钟同款 member blend。
     * @param blendColor 混色主色（通常来自壁纸/专辑取色）
     * @param primaryColor 字形底色（时钟用 primary，沉浸歌词用白）
     * @param colorDark 偏暗混色路径（105）否则 103
     * @param blendAlpha 混色层透明度（越低越不抢白字）
     * @param labAlpha 暗部 lab 层透明度（越低越不发透/发灰）
     * @param overColor 额外提亮色（mode=3），如半透明白可让字更实、更偏白
     */
    fun applyTextBlend(
        view: View,
        blendColor: Int,
        primaryColor: Int = Color.WHITE,
        colorDark: Boolean = true,
        enablePassBlurOnSelf: Boolean = true,
        passBlurRadius: Int = 80,
        blendAlpha: Int = 255,
        labAlpha: Int = 255,
        overColor: Int = 0
    ): Boolean {
        if (!isSupported(view.context)) return false
        return try {
            if (enablePassBlurOnSelf) {
                invoke(setPassWindowBlurEnabled, view, true)
                invoke(setMiBackgroundBlurMode, view, 1)
                invoke(setMiBackgroundBlurRadius, view, passBlurRadius)
                invoke(disableMiBackgroundContainBelow, view, true)
            }
            invoke(clearMiBackgroundBlendColor, view)
            invoke(setMiViewBlurMode, view, 3)

            val blend = Color.argb(
                blendAlpha.coerceIn(0, 255),
                Color.red(blendColor),
                Color.green(blendColor),
                Color.blue(blendColor)
            )
            val lab = Color.argb(labAlpha.coerceIn(0, 255), 0, 0, 0)
            val colors = ArrayList<Point>(5).apply {
                add(Point(blend, 101))
                add(Point(lab, if (colorDark) 105 else 103))
                // origin / primary（BACKGROUND_BLUR_VERSION>=2 路径）
                add(Point(primaryColor, 1000))
                if (overColor != 0) {
                    add(Point(overColor, 3))
                }
            }
            invoke(setMiBackgroundBlendColors, view, colors)
            logI(
                "applyTextBlend ok blend=#${Integer.toHexString(blend)} " +
                    "primary=#${Integer.toHexString(primaryColor)} dark=$colorDark " +
                    "labA=$labAlpha over=#${Integer.toHexString(overColor)}"
            )
            true
        } catch (e: Throwable) {
            logE("applyTextBlend failed", e)
            false
        }
    }

    fun clearTextBlend(view: View) {
        try {
            invoke(clearMiBackgroundBlendColor, view)
            invoke(setMiViewBlurMode, view, 0)
            invoke(setMiBackgroundBlurMode, view, 0)
            invoke(setMiBackgroundBlurRadius, view, 0)
            invoke(setPassWindowBlurEnabled, view, false)
            logI("clearTextBlend ok")
        } catch (e: Throwable) {
            logE("clearTextBlend failed", e)
        }
    }

    private fun resolveView(name: String, vararg params: Class<*>): Method? {
        return try {
            View::class.java.getMethod(name, *params).also { it.isAccessible = true }
        } catch (_: Throwable) {
            logI("View.$name not found")
            null
        }
    }

    private fun invoke(method: Method?, view: View, vararg args: Any?): Any? {
        if (method == null) return null
        return method.invoke(view, *args)
    }

    private fun systemPropBoolean(key: String, def: Boolean): Boolean {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, key, def) as Boolean
        } catch (_: Throwable) {
            def
        }
    }

    private fun systemPropInt(key: String, def: Int): Int {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
                .invoke(null, key, def) as Int
        } catch (_: Throwable) {
            def
        }
    }

    private fun logI(msg: String) {
        Log.i(TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        Log.e(TAG, msg, e)
    }
}
