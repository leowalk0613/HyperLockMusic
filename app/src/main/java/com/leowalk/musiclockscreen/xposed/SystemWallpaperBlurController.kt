package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/**
 * 音乐锁屏壁纸模糊：用系统 [ViewRootImpl.setWallpaperBlur] + MiBackgroundBlur 遮罩。
 * Bitmap 侧只做轻量 softColor，重糊交给合成器。
 */
internal object SystemWallpaperBlurController {

    private const val TAG = "HyperLockMusic_SysBlur"
    private const val MASK_TAG = "hyper_lockmusic_system_blur_mask"

    @Volatile
    private var bgLayerRef: WeakReference<ViewGroup>? = null

    @Volatile
    private var appliedRadius = -1

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun bindBackgroundLayer(layer: ViewGroup) {
        bgLayerRef = WeakReference(layer)
        layer.post { sync(layer.context) }
    }

    /** 音乐锁屏开启且非沉浸专辑时应用系统模糊；沉浸为 Monet 色底，不开模糊遮罩。 */
    fun sync(context: Context? = null) {
        val layer = bgLayerRef?.get()
        val ctx = context ?: layer?.context ?: return
        val want = shouldApply(
            musicLockscreenActive = WallpaperController.isShowing() || MusicLockscreenManager.isShowing,
            immersiveAlbum = ConfigReader.immersiveAlbum(ctx),
        )
        if (want) {
            val radius = mapSliderToWallpaperBlurRadius(ConfigReader.blurRadius(ctx))
            apply(ctx, layer, radius)
        } else {
            clear(layer)
        }
    }

    fun shouldApply(musicLockscreenActive: Boolean, immersiveAlbum: Boolean = false): Boolean {
        return musicLockscreenActive && !immersiveAlbum
    }

    /** 设置页 10–200 → ViewRootImpl.setWallpaperBlur 常用 0–100。 */
    fun mapSliderToWallpaperBlurRadius(sliderDp: Float): Int {
        val t = ((sliderDp - 10f) / 190f).coerceIn(0f, 1f)
        return (t * 100f).toInt().coerceIn(0, 100)
    }

    /** Bitmap softColor 缩小力度（不再 StackBlur）。 */
    fun bakeBlurRadius(sliderDp: Float): Float {
        return (sliderDp * 0.1f).coerceIn(3f, 14f)
    }

    /** Bitmap 暗色只留轻量；重浓度交给 [maskDarkOverlayAlpha]。 */
    fun bakeDarkOverlay(slider: Int): Int {
        return (slider * 0.22f).toInt().coerceIn(0, 60)
    }

    /** MiBlur 遮罩黑层 alpha（合成器侧主暗角）。 */
    fun maskDarkOverlayAlpha(slider: Int): Int {
        return (slider * 0.72f).toInt().coerceIn(0, 180)
    }

    private fun apply(ctx: Context, layer: ViewGroup?, radius: Int) {
        var wallpaperOk = false
        if (layer != null) {
            wallpaperOk = setWallpaperBlurOnView(layer, radius)
            ensureMask(layer)
            applyMiBlurMask(layer, radius, ConfigReader.darkOverlay(ctx))
        }
        appliedRadius = radius
        logI("apply radius=$radius wallpaperBlur=$wallpaperOk mask=${layer != null}")
    }

    private fun clear(layer: ViewGroup?) {
        if (layer != null) {
            setWallpaperBlurOnView(layer, 0)
            clearMiBlurMask(layer)
        }
        appliedRadius = -1
        logI("cleared")
    }

    private fun setWallpaperBlurOnView(view: View, radius: Int): Boolean {
        return try {
            val getVri = View::class.java.getDeclaredMethod("getViewRootImpl")
            getVri.isAccessible = true
            val vri = getVri.invoke(view) ?: return false
            val setBlur = vri.javaClass.getMethod("setWallpaperBlur", Int::class.javaPrimitiveType)
            setBlur.invoke(vri, radius)
            true
        } catch (e: Throwable) {
            logE("setWallpaperBlur failed", e)
            false
        }
    }

    private fun ensureMask(bgLayer: ViewGroup) {
        if (bgLayer.findViewWithTag<View>(MASK_TAG) != null) return
        val mask = FrameLayout(bgLayer.context).apply {
            tag = MASK_TAG
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        bgLayer.addView(mask, 0)
    }

    private fun applyMiBlurMask(bgLayer: ViewGroup, radius: Int, darkOverlay: Int) {
        val mask = bgLayer.findViewWithTag<View>(MASK_TAG) ?: return
        try {
            val miRadius = (radius * 1.2f).toInt().coerceIn(0, 120)
            val viaSystem = trySystemContainerPassBlur(mask, miRadius)
            if (!viaSystem) {
                invokeBool(mask, "setPassWindowBlurEnabled", true)
                invokeInt(mask, "setMiBackgroundBlurMode", 1)
                invokeInt(mask, "setMiBackgroundBlurRadius", miRadius)
            }
            val a = maskDarkOverlayAlpha(darkOverlay)
            mask.setBackgroundColor(Color.argb(a, 0, 0, 0))
            mask.visibility = View.VISIBLE
            mask.alpha = 1f
        } catch (e: Throwable) {
            logE("applyMiBlurMask failed", e)
        }
    }

    private fun clearMiBlurMask(bgLayer: ViewGroup) {
        val mask = bgLayer.findViewWithTag<View>(MASK_TAG) ?: return
        try {
            if (!trySystemClearContainerPassBlur(mask)) {
                invokeInt(mask, "setMiBackgroundBlurRadius", 0)
                invokeInt(mask, "setMiBackgroundBlurMode", 0)
                invokeBool(mask, "setPassWindowBlurEnabled", false)
            }
            mask.setBackgroundColor(Color.TRANSPARENT)
            mask.visibility = View.GONE
        } catch (e: Throwable) {
            logE("clearMiBlurMask failed", e)
        }
    }

    /** SystemUI 进程内优先走 [com.miui.clock.utils.MiuiBlurUtils]。 */
    private fun trySystemContainerPassBlur(view: View, radius: Int): Boolean {
        return try {
            val cls = Class.forName("com.miui.clock.utils.MiuiBlurUtils")
            val m = cls.getMethod(
                "setContainerPassBlur",
                View::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            (m.invoke(null, view, radius, false) as? Boolean) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun trySystemClearContainerPassBlur(view: View): Boolean {
        return try {
            val cls = Class.forName("com.miui.clock.utils.MiuiBlurUtils")
            cls.getMethod("clearContainerPassBlur", View::class.java).invoke(null, view)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun invokeBool(view: View, name: String, value: Boolean) {
        View::class.java.getMethod(name, Boolean::class.javaPrimitiveType)
            .also { it.isAccessible = true }
            .invoke(view, value)
    }

    private fun invokeInt(view: View, name: String, value: Int) {
        View::class.java.getMethod(name, Int::class.javaPrimitiveType)
            .also { it.isAccessible = true }
            .invoke(view, value)
    }

    private fun logI(msg: String) {
        try {
            android.util.Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        try {
            android.util.Log.e(TAG, msg, e)
        } catch (_: Throwable) {
        }
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
