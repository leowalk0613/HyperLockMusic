package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * 锁屏媒体控件背景取专辑主色（可调透明度）。
 *
 * 落点：SystemUI [MiuiMediaViewHolder.mediaBg]
 * - 开启通知背景模糊时：走 [HyperMiBlurHelper.applySoftGlassBackdrop]（混色 + 透明度）
 * - 否则：GradientDrawable / ColorDrawable 纯色（对齐 LyricFocus solidColorBitmap）
 *
 * 在 [bindMediaData] 后与 [updateForegroundColors] 后重新套色，避免系统 AOD/主题回写覆盖。
 */
class MediaBgTintHook {

    private val tag = "HyperLockMusic_MediaBgTint"
    private var module: XposedModule? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaBgRef: WeakReference<ImageView>? = null
    private var lastTintRgb: Int = Color.TRANSPARENT
    private var lastOpacity: Int = MediaBgAlbumTintPolicy.DEFAULT_OPACITY_PERCENT

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        try {
            logI("install start")
            hookBindMediaData(classLoader, module)
            hookUpdateForegroundColors(classLoader, module)
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
                try {
                    val mediaBg = mediaBgRef?.get()
                    if (mediaBg != null && lastTintRgb != Color.TRANSPARENT) {
                        applyResolved(mediaBg, lastTintRgb, lastOpacity)
                    }
                } catch (e: Throwable) {
                    logE("after updateForegroundColors tint error", e)
                }
                result
            }
            logI("hook updateForegroundColors: OK")
        } catch (e: Throwable) {
            logE("hook updateForegroundColors failed", e)
        }
    }

    private fun scheduleApply(mediaBg: ImageView, albumDrawable: Drawable?) {
        val ctx = mediaBg.context ?: return
        if (!MediaBgAlbumTintPolicy.shouldApply(ConfigReader.mediaBgAlbumTint(ctx))) {
            return
        }
        val opacity = ConfigReader.mediaBgAlbumOpacity(ctx)
        val bitmap = drawableToBitmap(albumDrawable) ?: return
        val rgb = try {
            BlurUtils.extractLowerHalfDominantColor(bitmap)
        } catch (e: Throwable) {
            logE("extract color failed", e)
            return
        }
        lastTintRgb = MediaBgAlbumTintPolicy.tintRgb(rgb)
        lastOpacity = MediaBgAlbumTintPolicy.coerceOpacity(opacity)
        applyResolved(mediaBg, lastTintRgb, lastOpacity)
        // 系统 bind 后可能再写 blur blend，延迟再套一次
        mainHandler.postDelayed({
            val v = mediaBgRef?.get() ?: return@postDelayed
            if (lastTintRgb == Color.TRANSPARENT) return@postDelayed
            val c = v.context ?: return@postDelayed
            if (!MediaBgAlbumTintPolicy.shouldApply(ConfigReader.mediaBgAlbumTint(c))) return@postDelayed
            applyResolved(v, lastTintRgb, lastOpacity)
        }, 120L)
    }

    private fun applyResolved(mediaBg: ImageView, tintRgb: Int, opacityPercent: Int) {
        val color = MediaBgAlbumTintPolicy.colorWithOpacity(tintRgb, opacityPercent)
        mediaBg.setImageDrawable(null)
        // SoftGlass：先铺近透明底再套混色（合成器只注册「有内容」的 View）
        mediaBg.background = ColorDrawable(MagazinePageSoftGlassPolicy.seedDrawableColor())
        val softOk = HyperMiBlurHelper.applySoftGlassBackdrop(
            mediaBg,
            HyperMiBlurHelper.SoftGlassSpec(
                opacityPercent = opacityPercent,
                tintColor = tintRgb,
                sampleSiblingContent = false,
            ),
        )
        if (softOk) {
            logI("applied softGlass tint=#${Integer.toHexString(tintRgb)} opacity=$opacityPercent")
            return
        }
        // 回退：纯色圆角（LyricFocus 式 solid color）
        val density = mediaBg.resources?.displayMetrics?.density ?: 3f
        val corner = 28f * density
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(color)
        }
        mediaBg.background = gd
        logI("applied solid tint=#${Integer.toHexString(color)}")
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap
            return if (bmp != null && !bmp.isRecycled) bmp else null
        }
        return try {
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w.coerceAtMost(256), h.coerceAtMost(256), Bitmap.Config.ARGB_8888)
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
}
