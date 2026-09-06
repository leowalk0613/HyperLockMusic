package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference

/**
 * 音乐锁屏壁纸模糊：Bitmap softColor 铺底 + 均匀暗色。
 * 不用 ViewRootImpl.setWallpaperBlur / 全屏 MiBackgroundBlur——易出十字暗纹并可能糊到桌面。
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

    /**
     * 离开锁屏时清掉可能残留的系统壁纸 blur / 遮罩；锁屏内不再启用全屏 MiBlur。
     */
    fun sync(context: Context? = null) {
        val layer = bgLayerRef?.get()
        val ctx = context ?: layer?.context
        clear(layer)
        if (ctx != null && shouldApply(
                musicLockscreenActive = WallpaperController.isShowing() || MusicLockscreenManager.isShowing,
                immersiveAlbum = ConfigReader.immersiveAlbum(ctx),
                onKeyguard = HookUtils.isOnKeyguard(ctx),
            )
        ) {
            appliedRadius = mapSliderToWallpaperBlurRadius(ConfigReader.blurRadius(ctx))
            logI("softColor-only mode radius=$appliedRadius (no MiBlur mask)")
        }
    }

    fun shouldApply(
        musicLockscreenActive: Boolean,
        immersiveAlbum: Boolean = false,
        onKeyguard: Boolean = true,
    ): Boolean {
        return musicLockscreenActive && !immersiveAlbum && onKeyguard
    }

    /** 设置页 10–200 → 内部半径映射 0–100（驱动 softColor 力度）。 */
    fun mapSliderToWallpaperBlurRadius(sliderDp: Float): Int {
        val t = ((sliderDp - 10f) / 190f).coerceIn(0f, 1f)
        return (t * 100f).toInt().coerceIn(0, 100)
    }

    /** Bitmap softColor 缩小力度。 */
    fun bakeBlurRadius(sliderDp: Float): Float {
        return (sliderDp * 0.12f).coerceIn(4f, 18f)
    }

    /** Bitmap 暗色：MiBlur 遮罩已关，暗角主要画在 Bitmap 上。 */
    fun bakeDarkOverlay(slider: Int): Int {
        return (slider * 0.55f).toInt().coerceIn(0, 160)
    }

    /** @deprecated 遮罩已停用，保留供兼容测试。 */
    fun maskDarkOverlayAlpha(slider: Int): Int {
        return (slider * 0.72f).toInt().coerceIn(0, 180)
    }

    private fun clear(layer: ViewGroup?) {
        if (layer != null) {
            setWallpaperBlurOnView(layer, 0)
            clearMiBlurMask(layer)
        }
        appliedRadius = -1
        logI("cleared system blur/mask")
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
        try {
            bgLayer.removeView(mask)
        } catch (_: Throwable) {
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
