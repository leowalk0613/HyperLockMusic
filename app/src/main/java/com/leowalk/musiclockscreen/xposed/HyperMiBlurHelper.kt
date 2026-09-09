package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.provider.Settings
import android.util.Log
import android.view.View
import java.lang.reflect.Method

/**
 * HyperOS / MIUI 锁屏时钟同款「透壁纸高斯模糊染色」。
 * 优先委托 SystemUI 内 [com.miui.clock.utils.MiuiBlurUtils]，失败再走 View 隐藏方法反射。
 */
object HyperMiBlurHelper {

    private const val TAG = "HyperLockMusic_MiBlur"

    private const val SYSTEM_MIUI_BLUR = "com.miui.clock.utils.MiuiBlurUtils"

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

    /**
     * 柔光玻璃规格（对齐 HyperChanger 锁屏快捷方式 Soft Glass）。
     * @param opacityPercent 混色不透明度 0–100
     * @param backdropBlurRadius [setMiBackgroundBlurRadius] / setContainerPassBlur
     * @param glassBlurRadius MiGlassCompat 小半径（大半径约为 2×）
     * @param luminance MiGlass 亮度参数
     * @param tintColor 混色 RGB（alpha 由 opacityPercent 决定）
     * @param sampleSiblingContent 是否采样同窗下层（画报壁纸 ImageView）
     */
    data class SoftGlassSpec(
        val opacityPercent: Int = MagazinePageSoftGlassPolicy.OPACITY_PERCENT,
        val backdropBlurRadius: Int = MagazinePageSoftGlassPolicy.BACKDROP_BLUR_RADIUS,
        val glassBlurRadius: Int = MagazinePageSoftGlassPolicy.GLASS_BLUR_RADIUS,
        val luminance: Float = MagazinePageSoftGlassPolicy.LUMINANCE,
        val tintColor: Int = MagazinePageSoftGlassPolicy.TINT_COLOR,
        val sampleSiblingContent: Boolean = true,
    )

    /** 设备是否声明支持 background blur，且用户开关打开。 */
    fun isSupported(context: Context): Boolean {
        if (systemMiuiBlurClass() != null) return true
        if (setMiViewBlurMode == null || setMiBackgroundBlendColors == null) {
            return false
        }
        if (!systemPropBoolean("persist.sys.background_blur_supported", false) &&
            systemPropInt("persist.sys.background_blur_version", 0) <= 0
        ) {
            logI("prop blur unsupported, but View APIs present — allow try")
        }
        return try {
            Settings.Secure.getInt(context.contentResolver, "background_blur_enable", 0) == 1 ||
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
     * @param sampleSiblingContent 为 true 时采样同窗口下层兄弟（画报 ImageView 壁纸）
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
        sampleSiblingContent: Boolean = false,
        passBlurRadius: Int = 80,
        blendAlpha: Int = 255,
        labAlpha: Int = 255,
        overColor: Int = 0
    ): Boolean {
        if (!isSupported(view.context)) return false
        if (trySystemApplyTextBlend(
                view, blendColor, primaryColor, colorDark,
                enablePassBlurOnSelf, passBlurRadius, blendAlpha, labAlpha, overColor,
            )
        ) {
            if (sampleSiblingContent) {
                trySetContainBelow(view, containBelow = true)
            }
            return true
        }
        return applyTextBlendReflect(
            view, blendColor, primaryColor, colorDark,
            enablePassBlurOnSelf, sampleSiblingContent, passBlurRadius,
            blendAlpha, labAlpha, overColor,
        )
    }

    fun clearTextBlend(view: View) {
        if (trySystemClearTextBlend(view)) return
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

    /**
     * 容器柔光玻璃（HyperChanger 锁屏快捷方式同路径）：
     * 1) PassWindowBlur + MiBackgroundBlur + addMiBackgroundBlendColor
     * 2) 可选 MiuiBlurUtils.setContainerPassBlur
     * 3) 可选 MiGlassCompat（SystemUI 进程更常见）
     *
     * 调用方需先给 [view] 设置近透明 drawable（[MagazinePageSoftGlassPolicy.seedDrawableColor]），
     * 合成器只注册「有内容」的 View。
     */
    fun applySoftGlassBackdrop(view: View, spec: SoftGlassSpec = SoftGlassSpec()): Boolean {
        if (!isSupported(view.context)) return false
        val blurMode = MagazinePageSoftGlassPolicy.BLUR_MODE
        val blendMode = MagazinePageSoftGlassPolicy.BLEND_MODE
        val opacity = spec.opacityPercent.coerceIn(0, 100)
        val backdropR = spec.backdropBlurRadius.coerceIn(0, 120)
        val blendArgb = Color.argb(
            opacity * 255 / 100,
            Color.red(spec.tintColor),
            Color.green(spec.tintColor),
            Color.blue(spec.tintColor),
        )
        var ok = false
        try {
            invoke(clearMiBackgroundBlendColor, view)
            invoke(setPassWindowBlurEnabled, view, true)
            invoke(setMiViewBlurMode, view, blurMode)
            invoke(setMiBackgroundBlurMode, view, blurMode)
            invoke(setMiBackgroundBlurRadius, view, backdropR)
            invoke(addMiBackgroundBlendColor, view, blendArgb, blendMode)
            trySetContainBelow(view, containBelow = spec.sampleSiblingContent)
            ok = true
            logI(
                "applySoftGlassBackdrop reflect ok opacity=$opacity " +
                    "backdropR=$backdropR blend=#${Integer.toHexString(blendArgb)}"
            )
        } catch (e: Throwable) {
            logE("applySoftGlassBackdrop reflect failed", e)
        }
        // 系统工具双写：部分机型只认 MiuiBlurUtils
        trySystemContainerPassBlur(view, backdropR)
        tryMiGlassCompat(view, spec.glassBlurRadius, spec.luminance)
        return ok
    }

    fun clearSoftGlassBackdrop(view: View) {
        trySystemClearContainerPassBlur(view)
        try {
            invoke(clearMiBackgroundBlendColor, view)
            invoke(setMiViewBlurMode, view, 0)
            invoke(setMiBackgroundBlurMode, view, 0)
            invoke(setMiBackgroundBlurRadius, view, 0)
            invoke(setPassWindowBlurEnabled, view, false)
        } catch (e: Throwable) {
            logE("clearSoftGlassBackdrop failed", e)
        }
    }

    /** 单测 / 诊断：能否加载系统 MiuiBlurUtils。 */
    fun canUseSystemMiuiBlurUtils(): Boolean = systemMiuiBlurClass() != null

    private fun systemMiuiBlurClass(): Class<*>? {
        return try {
            Class.forName(SYSTEM_MIUI_BLUR)
        } catch (_: Throwable) {
            null
        }
    }

    private fun trySystemApplyTextBlend(
        view: View,
        blendColor: Int,
        primaryColor: Int,
        colorDark: Boolean,
        enablePassBlurOnSelf: Boolean,
        passBlurRadius: Int,
        blendAlpha: Int,
        labAlpha: Int,
        overColor: Int,
    ): Boolean {
        val cls = systemMiuiBlurClass() ?: return false
        return try {
            if (enablePassBlurOnSelf) {
                val setContainer = cls.getMethod(
                    "setContainerPassBlur",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                )
                setContainer.invoke(null, view, passBlurRadius, false)
            }
            val setMember = cls.getMethod(
                "setMemberBlendColors",
                View::class.java,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            setMember.invoke(
                null, view, colorDark, blendColor,
                blendAlpha.coerceIn(0, 255), labAlpha.coerceIn(0, 255),
                primaryColor, overColor,
            )
            logI("applyTextBlend via MiuiBlurUtils")
            true
        } catch (e: Throwable) {
            logE("system MiuiBlurUtils apply failed, fallback reflect", e)
            false
        }
    }

    private fun trySystemClearTextBlend(view: View): Boolean {
        val cls = systemMiuiBlurClass() ?: return false
        return try {
            cls.getMethod("clearMiBackgroundBlendColor", View::class.java).invoke(null, view)
            cls.getMethod("clearContainerPassBlur", View::class.java).invoke(null, view)
            logI("clearTextBlend via MiuiBlurUtils")
            true
        } catch (e: Throwable) {
            logE("system MiuiBlurUtils clear failed", e)
            false
        }
    }

    private fun trySystemContainerPassBlur(view: View, radius: Int): Boolean {
        val cls = systemMiuiBlurClass() ?: return false
        return try {
            cls.getMethod(
                "setContainerPassBlur",
                View::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).invoke(null, view, radius, false)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun trySystemClearContainerPassBlur(view: View): Boolean {
        val cls = systemMiuiBlurClass() ?: return false
        return try {
            cls.getMethod("clearContainerPassBlur", View::class.java).invoke(null, view)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * SystemUI 内 [com.miui.systemui.util.MiGlassCompat]；app 进程常缺失，失败则忽略。
     */
    private fun tryMiGlassCompat(view: View, glassBlurRadius: Int, luminance: Float): Boolean {
        return try {
            val loader = view.context.classLoader
            val glassCompat = Class.forName("com.miui.systemui.util.MiGlassCompat", false, loader)
            val small = glassBlurRadius.coerceIn(0, 100)
            val large = (small * 2).coerceAtMost(500)
            glassCompat.getMethod(
                "setMiGlassBlurRadius",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(null, view, small, large)
            glassCompat.getMethod(
                "setMiViewMaterialTypeCompat",
                Int::class.javaPrimitiveType,
                View::class.java,
            ).invoke(null, 1, view)
            val params = floatArrayOf(
                0.67f, 0.16f, 0.09f, 0f, luminance.coerceIn(0f, 0.4f), 1.4f, -0.02f, 0.3f, 0.6f, 1f,
                0.03f, 1f, 1f, 1f, 0.1f, 0.2f, 0.3f, 1f, 1f, 72f, 3.8f, 80f, 800f,
                1.2f, 1f, -0.4f, 0.6f, -0.8f, 1.4f, 0.7f, 0.8f, 1.15f, 4f, 2f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
            glassCompat.getMethod("setMiGlassCompat", View::class.java, FloatArray::class.java)
                .invoke(null, view, params)
            logI("applySoftGlass via MiGlassCompat small=$small lum=$luminance")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyTextBlendReflect(
        view: View,
        blendColor: Int,
        primaryColor: Int,
        colorDark: Boolean,
        enablePassBlurOnSelf: Boolean,
        sampleSiblingContent: Boolean,
        passBlurRadius: Int,
        blendAlpha: Int,
        labAlpha: Int,
        overColor: Int,
    ): Boolean {
        return try {
            if (enablePassBlurOnSelf) {
                invoke(setPassWindowBlurEnabled, view, true)
                invoke(setMiBackgroundBlurMode, view, 1)
                invoke(setMiBackgroundBlurRadius, view, passBlurRadius)
                // disableMiBackgroundContainBelow(true) = 不采下层兄弟；画报需 false
                trySetContainBelow(view, containBelow = sampleSiblingContent)
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
                add(Point(primaryColor, 1000))
                if (overColor != 0) {
                    add(Point(overColor, 3))
                }
            }
            invoke(setMiBackgroundBlendColors, view, colors)
            logI(
                "applyTextBlend reflect ok blend=#${Integer.toHexString(blend)} " +
                    "primary=#${Integer.toHexString(primaryColor)} dark=$colorDark " +
                    "sibling=$sampleSiblingContent labA=$labAlpha over=#${Integer.toHexString(overColor)}"
            )
            true
        } catch (e: Throwable) {
            logE("applyTextBlend failed", e)
            false
        }
    }

    /** containBelow=true → 允许采样同窗下层内容（画报壁纸 ImageView）。 */
    private fun trySetContainBelow(view: View, containBelow: Boolean) {
        try {
            // API 语义：disable…(true) 禁止采下层；故取反
            invoke(disableMiBackgroundContainBelow, view, !containBelow)
        } catch (_: Throwable) {
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
