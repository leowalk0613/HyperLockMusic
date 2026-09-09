package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import dalvik.system.PathClassLoader
import java.lang.ref.WeakReference

/**
 * HyperOS 锁屏快捷方式同款「系统柔光玻璃」——对齐 HyperChanger
 * [applyLegacyBackdropMaterial] + [applySystemGlassMaterial]，不做 Bitmap/半透明平替。
 *
 * 必须两段都成功才算套上：
 * 1) View PassWindowBlur + MiBackgroundBlur + addMiBackgroundBlendColor(mode=101)
 * 2) MiGlassCompat（从 SystemUI APK 加载）或 View.setMiGlass / setMiGlassBlurRadius
 */
object HyperOsSoftGlass {

    private const val TAG = "HyperLockMusic_SoftGlass"

    private const val MI_GLASS_COMPAT = "com.miui.systemui.util.MiGlassCompat"
    private const val MI_BACKGROUND_STYLE = "miui.systemui.util.MiBackgroundStyle"
    private const val MIUIX_BLUR_UTILS = "miuix.core.util.MiuiBlurUtils"
    private const val CLOCK_BLUR_UTILS = "com.miui.clock.utils.MiuiBlurUtils"

    private const val GLASS_MATERIAL_TYPE = 1
    private const val BLUR_MODE = 1
    private const val BLEND_MODE = 101
    private const val MAX_BACKDROP_BLUR = 120
    private const val MAX_GLASS_BLUR = 100
    private const val MAX_GLASS_LARGE = 500
    private const val GLASS_LUMINANCE_INDEX = 4

    /** HyperChanger SHORTCUT_GLASS_PARAMETERS（亮度位可覆盖）。 */
    private val GLASS_PARAMETERS = floatArrayOf(
        0.67f, 0.16f, 0.09f, 0f, 0.24f, 1.4f, -0.02f, 0.3f, 0.6f, 1f,
        0.03f, 1f, 1f, 1f, 0.1f, 0.2f, 0.3f, 1f, 1f, 72f, 3.8f, 80f, 800f,
        1.2f, 1f, -0.4f, 0.6f, -0.8f, 1.4f, 0.7f, 0.8f, 1.15f, 4f, 2f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    )

    data class Spec(
        val opacityPercent: Int = 10,
        val backdropBlurRadius: Int = 80,
        val glassBlurRadius: Int = 36,
        val luminance: Float = 0.14f,
        val tintColor: Int = 0xFFFFFFFF.toInt(),
        val sampleSiblingContent: Boolean = true,
        val cornerRadiusPx: Float = 0f,
    )

    data class ApplyResult(
        val legacyOk: Boolean,
        val glassOk: Boolean,
        val glassVia: String?,
    ) {
        val success: Boolean get() = legacyOk && glassOk
    }

    @Volatile
    private var systemUiLoaderRef: WeakReference<ClassLoader>? = null

    /**
     * 给 [ImageView] 铺近透明 seed（合成器只注册有 drawable 的 View），再套完整柔光玻璃。
     */
    fun applyToImageView(view: ImageView, spec: Spec): ApplyResult {
        val seed = GradientDrawable().apply {
            setColor(Color.argb(1, 255, 255, 255))
            if (spec.cornerRadiusPx > 0f) cornerRadius = spec.cornerRadiusPx
        }
        view.setImageDrawable(seed)
        view.background = null
        if (spec.cornerRadiusPx > 0f) {
            view.clipToOutline = true
        }
        return apply(view, spec)
    }

    fun apply(view: View, spec: Spec): ApplyResult {
        val legacyOk = applyLegacyBackdropMaterial(view, spec)
        val (glassOk, via) = applySystemGlassMaterial(view, spec)
        logI(
            "apply softGlass legacy=$legacyOk glass=$glassOk via=$via " +
                "opacity=${spec.opacityPercent} backdrop=${spec.backdropBlurRadius} " +
                "glassR=${spec.glassBlurRadius} lum=${spec.luminance}"
        )
        view.invalidate()
        return ApplyResult(legacyOk, glassOk, via)
    }

    fun clear(view: View) {
        runCatching {
            View::class.java.getMethod("clearMiBackgroundBlendColor").invoke(view)
        }
        runCatching {
            View::class.java.getMethod("setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType)
                .invoke(view, false)
        }
        runCatching {
            View::class.java.getMethod("setMiViewBlurMode", Int::class.javaPrimitiveType)
                .invoke(view, 0)
        }
        runCatching {
            View::class.java.getMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType)
                .invoke(view, 0)
        }
        runCatching {
            View::class.java.getMethod("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType)
                .invoke(view, 0)
        }
        tryClearContainerPassBlur(view)
    }

    /**
     * HyperChanger applyLegacyBackdropMaterial —— 一字不差的 View API 顺序。
     */
    private fun applyLegacyBackdropMaterial(view: View, spec: Spec): Boolean {
        return try {
            val viewClass = View::class.java
            viewClass.getMethod("clearMiBackgroundBlendColor").invoke(view)
            viewClass.getMethod("setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType)
                .invoke(view, true)
            viewClass.getMethod("setMiViewBlurMode", Int::class.javaPrimitiveType)
                .invoke(view, BLUR_MODE)
            viewClass.getMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType)
                .invoke(view, BLUR_MODE)
            viewClass.getMethod("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType)
                .invoke(view, spec.backdropBlurRadius.coerceIn(0, MAX_BACKDROP_BLUR))
            val blend = Color.argb(
                spec.opacityPercent.coerceIn(0, 100) * 255 / 100,
                Color.red(spec.tintColor),
                Color.green(spec.tintColor),
                Color.blue(spec.tintColor),
            )
            viewClass.getMethod(
                "addMiBackgroundBlendColor",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(view, blend, BLEND_MODE)
            // 画报壁纸是同窗兄弟：允许采下层
            if (spec.sampleSiblingContent) {
                runCatching {
                    viewClass.getMethod(
                        "disableMiBackgroundContainBelow",
                        Boolean::class.javaPrimitiveType,
                    ).invoke(view, false)
                }
            }
            tryContainerPassBlur(view, spec.backdropBlurRadius)
            true
        } catch (e: Throwable) {
            logE("legacy backdrop failed", e)
            false
        }
    }

    /**
     * HyperChanger applySystemGlassMaterial：优先 SystemUI [MiGlassCompat]。
     */
    private fun applySystemGlassMaterial(view: View, spec: Spec): Pair<Boolean, String?> {
        val small = spec.glassBlurRadius.coerceIn(0, MAX_GLASS_BLUR)
        val large = (small * 2).coerceAtMost(MAX_GLASS_LARGE)
        val params = GLASS_PARAMETERS.copyOf().apply {
            this[GLASS_LUMINANCE_INDEX] = spec.luminance.coerceIn(0f, 0.4f)
        }
        val loaders = buildList {
            systemUiClassLoader(view.context)?.let { add(it) }
            add(view.context.classLoader)
            add(ClassLoader.getSystemClassLoader())
        }

        for (loader in loaders) {
            try {
                val glassCompat = Class.forName(MI_GLASS_COMPAT, false, loader)
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
                ).invoke(null, GLASS_MATERIAL_TYPE, view)
                glassCompat.getMethod("setMiGlassCompat", View::class.java, FloatArray::class.java)
                    .invoke(null, view, params)
                return true to "MiGlassCompat"
            } catch (_: Throwable) {
            }
        }

        // View 原生柔光 API（HyperOS 合成器侧，与 HyperChanger shade hook 同源）
        try {
            View::class.java.getMethod(
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(view, small, large)
            View::class.java.getMethod("setMiGlass", FloatArray::class.java)
                .invoke(view, params)
            return true to "View.setMiGlass"
        } catch (_: Throwable) {
        }

        // miuix / clock blur utils 的 setMiGlassBlurRadius(View,int,int)
        for (name in listOf(MIUIX_BLUR_UTILS, CLOCK_BLUR_UTILS)) {
            for (loader in loaders) {
                try {
                    val cls = Class.forName(name, false, loader)
                    cls.getMethod(
                        "setMiGlassBlurRadius",
                        View::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).invoke(null, view, small, large)
                    // 再补 View.setMiGlass 参数
                    runCatching {
                        View::class.java.getMethod("setMiGlass", FloatArray::class.java)
                            .invoke(view, params)
                    }
                    runCatching {
                        View::class.java.getMethod(
                            "setMiViewMaterialType",
                            Int::class.javaPrimitiveType,
                        ).invoke(view, GLASS_MATERIAL_TYPE)
                    }
                    return true to name
                } catch (_: Throwable) {
                }
            }
        }

        // MiBackgroundStyle DEFAULT_GLASS_TOKEN（HyperChanger 灵动岛路径）
        for (loader in loaders) {
            try {
                val style = Class.forName(MI_BACKGROUND_STYLE, false, loader)
                val instance = style.getField("INSTANCE").get(null)
                val glassToken = style.getMethod("getDEFAULT_GLASS_TOKEN").invoke(instance)
                style.methods.first {
                    it.name == "setMiBackgroundStyle" && it.parameterCount == 3
                }.invoke(null, view, null, glassToken)
                val blurUtils = Class.forName(MIUIX_BLUR_UTILS, false, loader)
                blurUtils.getMethod(
                    "setMiGlassBlurRadius",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).invoke(null, view, small, large)
                return true to "MiBackgroundStyle"
            } catch (_: Throwable) {
            }
        }

        logE("system glass material unavailable — refusing fake fallback")
        return false to null
    }

    private fun tryContainerPassBlur(view: View, radius: Int) {
        val r = radius.coerceIn(0, MAX_BACKDROP_BLUR)
        for (name in listOf(CLOCK_BLUR_UTILS, MIUIX_BLUR_UTILS)) {
            try {
                val cls = Class.forName(name)
                cls.getMethod(
                    "setContainerPassBlur",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).invoke(null, view, r, false)
                return
            } catch (_: Throwable) {
            }
        }
    }

    private fun tryClearContainerPassBlur(view: View) {
        for (name in listOf(CLOCK_BLUR_UTILS, MIUIX_BLUR_UTILS)) {
            try {
                Class.forName(name).getMethod("clearContainerPassBlur", View::class.java)
                    .invoke(null, view)
                return
            } catch (_: Throwable) {
            }
        }
    }

    /** 从 SystemUI APK 拉 ClassLoader，以便 app 进程也能加载 MiGlassCompat。 */
    fun systemUiClassLoader(context: Context): ClassLoader? {
        systemUiLoaderRef?.get()?.let { return it }
        // 1) createPackageContext（部分机型可直接拿 SystemUI ClassLoader）
        try {
            val uiCtx = context.createPackageContext(
                "com.android.systemui",
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
            val cl = uiCtx.classLoader
            if (cl != null) {
                runCatching { Class.forName(MI_GLASS_COMPAT, false, cl) }.onSuccess {
                    systemUiLoaderRef = WeakReference(cl)
                    logI("SystemUI package ClassLoader ok")
                    return cl
                }
            }
        } catch (e: Throwable) {
            logE("createPackageContext SystemUI failed", e)
        }
        // 2) PathClassLoader 指向 SystemUI APK
        return try {
            val ai = context.packageManager.getApplicationInfo("com.android.systemui", 0)
            val path = buildString {
                append(ai.sourceDir)
                ai.splitSourceDirs?.forEach { append(':').append(it) }
            }
            val lib = ai.nativeLibraryDir
            val loader = PathClassLoader(path, lib, context.classLoader)
            Class.forName(MI_GLASS_COMPAT, false, loader)
            systemUiLoaderRef = WeakReference(loader)
            logI("SystemUI PathClassLoader ok path=$path")
            loader
        } catch (e: Throwable) {
            logE("SystemUI PathClassLoader failed", e)
            null
        }
    }

    private fun logI(msg: String) {
        Log.i(TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        Log.e(TAG, msg, e)
    }
}
