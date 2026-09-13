package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * 锁屏媒体控件背景取专辑主色（可调透明度）。
 *
 * 落点：SystemUI [MiuiMediaViewHolder.mediaBg]
 * 策略（对齐 LyricFocus solidColorBitmap）：
 * 1. 清掉系统 MiBlur 混色，避免 SoftGlass「假成功」盖住纯色
 * 2. 同时写 background（圆角 GradientDrawable）+ image（纯色 Bitmap）
 * 3. 拦截 [NotificationUtil.applyElementViewBlend]，系统回写后立刻重套
 */
class MediaBgTintHook {

    private val tag = "HyperLockMusic_MediaBgTint"
    private var module: XposedModule? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaBgRef: WeakReference<ImageView>? = null
    private var lastTintRgb: Int = Color.TRANSPARENT
    private var lastOpacity: Int = MediaBgAlbumTintPolicy.DEFAULT_OPACITY_PERCENT
    private var applyGen: Int = 0

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        try {
            logI("install start")
            hookBindMediaData(classLoader, module)
            hookUpdateForegroundColors(classLoader, module)
            hookApplyElementViewBlend(classLoader, module)
            logI("MediaBgTintHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun hookBindMediaData(classLoader: ClassLoader, module: XposedModule) {
        val vcClass = Class.forName(
            "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
            false,
            classLoader,
        )
        val mediaDataClass = Class.forName(
            "com.android.systemui.media.controls.shared.model.MediaData",
            false,
            classLoader,
        )
        val bindMethod = vcClass.getDeclaredMethod("bindMediaData", mediaDataClass)
        val holderField = vcClass.getDeclaredField("holder").apply { isAccessible = true }
        val viewHolderClass = Class.forName(
            "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
            false,
            classLoader,
        )
        val mediaBgField = viewHolderClass.getDeclaredField("mediaBg").apply { isAccessible = true }
        val albumImageViewField =
            viewHolderClass.getDeclaredField("albumImageView").apply { isAccessible = true }

        module.hook(bindMethod).intercept { chain ->
            val result = chain.proceed()
            try {
                val holder = holderField.get(chain.thisObject) ?: return@intercept result
                val mediaBg = mediaBgField.get(holder) as? ImageView
                val albumIv = albumImageViewField.get(holder) as? ImageView
                if (mediaBg != null) {
                    mediaBgRef = WeakReference(mediaBg)
                    mediaBg.setTag(TAG_OWNED, true)
                    scheduleApply(mediaBg, albumIv?.drawable)
                }
            } catch (e: Throwable) {
                logE("after bindMediaData tint error", e)
            }
            result
        }
    }

    private fun hookUpdateForegroundColors(classLoader: ClassLoader, module: XposedModule) {
        try {
            val vcClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                false,
                classLoader,
            )
            val method = vcClass.declaredMethods.firstOrNull { m ->
                m.name == "updateForegroundColors" && m.parameterCount == 0
            }?.apply { isAccessible = true }
            if (method == null) {
                logE("updateForegroundColors not found")
                return
            }
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                reapplyIfNeeded("updateForegroundColors")
                result
            }
            logI("hook updateForegroundColors: OK")
        } catch (e: Throwable) {
            logE("hook updateForegroundColors failed", e)
        }
    }

    /**
     * 系统 AOD / 主题会不断 [applyElementViewBlend] 覆盖 mediaBg；
     * 我们的染色开启时，在系统写完后立刻用专辑主色重套。
     */
    private fun hookApplyElementViewBlend(classLoader: ClassLoader, module: XposedModule) {
        try {
            val utilClass = Class.forName(
                "com.android.systemui.statusbar.notification.utils.NotificationUtil",
                false,
                classLoader,
            )
            val methods = utilClass.declaredMethods.filter { m ->
                m.name == "applyElementViewBlend" && m.parameterCount >= 4
            }
            if (methods.isEmpty()) {
                logE("applyElementViewBlend not found")
                return
            }
            for (method in methods) {
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val view = chain.args.getOrNull(1) as? View
                        if (view != null && view.getTag(TAG_OWNED) == true) {
                            reapplyIfNeeded("applyElementViewBlend")
                        }
                    } catch (e: Throwable) {
                        logE("after applyElementViewBlend tint error", e)
                    }
                    result
                }
            }
            logI("hook applyElementViewBlend: OK count=${methods.size}")
        } catch (e: Throwable) {
            logE("hook applyElementViewBlend failed", e)
        }
    }

    private fun reapplyIfNeeded(reason: String) {
        val mediaBg = mediaBgRef?.get() ?: return
        if (lastTintRgb == Color.TRANSPARENT) return
        val ctx = mediaBg.context ?: return
        ConfigReader.invalidate()
        if (!MediaBgAlbumTintPolicy.shouldApply(ConfigReader.mediaBgAlbumTint(ctx))) return
        applyResolved(mediaBg, lastTintRgb, lastOpacity)
        logI("reapply ($reason) tint=#${Integer.toHexString(lastTintRgb)} opacity=$lastOpacity")
    }

    private fun scheduleApply(mediaBg: ImageView, albumDrawable: Drawable?) {
        val ctx = mediaBg.context ?: return
        ConfigReader.invalidate()
        if (!MediaBgAlbumTintPolicy.shouldApply(ConfigReader.mediaBgAlbumTint(ctx))) {
            logI("skip: media_bg_album_tint off")
            return
        }
        val opacity = ConfigReader.mediaBgAlbumOpacity(ctx)
        val bitmap = drawableToBitmap(albumDrawable)
        if (bitmap == null) {
            logE("skip: album drawable empty")
            return
        }
        val rgb = try {
            BlurUtils.extractLowerHalfDominantColor(bitmap)
        } catch (e: Throwable) {
            logE("extract color failed", e)
            return
        }
        lastTintRgb = MediaBgAlbumTintPolicy.tintRgb(rgb)
        lastOpacity = MediaBgAlbumTintPolicy.coerceOpacity(opacity)
        val gen = ++applyGen
        // 系统 bind 后还会写 blur，多段重试确保盖住
        for (delay in RETRY_DELAYS_MS) {
            mainHandler.postDelayed({
                if (gen != applyGen) return@postDelayed
                val v = mediaBgRef?.get() ?: return@postDelayed
                val c = v.context ?: return@postDelayed
                if (!MediaBgAlbumTintPolicy.shouldApply(ConfigReader.mediaBgAlbumTint(c))) return@postDelayed
                applyResolved(v, lastTintRgb, lastOpacity)
            }, delay)
        }
        logI(
            "schedule tint=#${Integer.toHexString(lastTintRgb)} " +
                "opacity=$lastOpacity retries=${RETRY_DELAYS_MS.size}",
        )
    }

    private fun applyResolved(mediaBg: ImageView, tintRgb: Int, opacityPercent: Int) {
        val color = MediaBgAlbumTintPolicy.colorWithOpacity(tintRgb, opacityPercent)
        // 必须清掉系统 MiBlur，否则纯色不可见
        try {
            HyperMiBlurHelper.clearSoftGlassBackdrop(mediaBg)
        } catch (_: Throwable) {
        }
        val density = mediaBg.resources?.displayMetrics?.density ?: 3f
        val corner = CORNER_DP * density
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(color)
        }
        mediaBg.background = gd
        // LyricFocus：ImageView 再铺一层纯色 bitmap，防止系统只画 image 层
        mediaBg.scaleType = ImageView.ScaleType.FIT_XY
        mediaBg.setImageBitmap(solidColorBitmap(color))
        mediaBg.imageAlpha = 255
        mediaBg.visibility = View.VISIBLE
        mediaBg.alpha = 1f
    }

    private fun solidColorBitmap(color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap
            return if (bmp != null && !bmp.isRecycled) bmp else null
        }
        if (drawable is ColorDrawable) {
            val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(drawable.color)
            return bmp
        }
        return try {
            val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(256)
            val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(256)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) module?.log(android.util.Log.ERROR, tag, msg, e)
        else module?.log(android.util.Log.ERROR, tag, msg)
    }

    companion object {
        private const val TAG_OWNED = 0x7f1400B1
        private const val CORNER_DP = 28f
        private val RETRY_DELAYS_MS = longArrayOf(0L, 48L, 160L, 400L)
    }
}
