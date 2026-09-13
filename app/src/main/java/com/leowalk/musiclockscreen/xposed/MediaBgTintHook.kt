package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * 锁屏媒体控件：只给系统玻璃混色染专辑色，不拆 MiBlur / 不铺纯色底。
 *
 * 拦截 [NotificationUtil.applyElementViewBlend]，把 `[color, mode, …]` 里的 color
 * 换成专辑 RGB（[MediaBgAlbumTintPolicy.retintBlendPairs]），mode 与圆角/模糊模式不动。
 */
class MediaBgTintHook {

    private val tag = "HyperLockMusic_MediaBgTint"
    private var module: XposedModule? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaBgRef: WeakReference<ImageView>? = null
    private var lastTintRgb: Int = Color.TRANSPARENT
    private var lastOpacity: Int = MediaBgAlbumTintPolicy.DEFAULT_OPACITY_PERCENT
    /** 系统最近一次下发的原始 color/mode 对，切歌后用来重套。 */
    private var lastSystemBlendPairs: IntArray? = null
    private var applyGen: Int = 0
    private var reenteringBlend: Boolean = false

    private var applyElementBlendMethod: java.lang.reflect.Method? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        try {
            logI("install start")
            resolveApplyElementBlend(classLoader)
            hookBindMediaData(classLoader, module)
            hookApplyElementViewBlend(classLoader, module)
            logI("MediaBgTintHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun resolveApplyElementBlend(classLoader: ClassLoader) {
        try {
            val utilClass = Class.forName(
                "com.android.systemui.statusbar.notification.utils.NotificationUtil",
                false,
                classLoader,
            )
            applyElementBlendMethod = utilClass.declaredMethods.firstOrNull { m ->
                m.name == "applyElementViewBlend" &&
                    m.parameterCount == 5 &&
                    m.parameterTypes[3] == IntArray::class.java
            }?.apply { isAccessible = true }
        } catch (e: Throwable) {
            logE("resolve applyElementViewBlend failed", e)
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
                    scheduleTintUpdate(mediaBg, albumIv?.drawable)
                }
            } catch (e: Throwable) {
                logE("after bindMediaData tint error", e)
            }
            result
        }
    }

    private fun hookApplyElementViewBlend(classLoader: ClassLoader, module: XposedModule) {
        try {
            val utilClass = Class.forName(
                "com.android.systemui.statusbar.notification.utils.NotificationUtil",
                false,
                classLoader,
            )
            val methods = utilClass.declaredMethods.filter { m ->
                (m.name == "applyElementViewBlend" || m.name == "applyElementViewBlendNoRoundRect") &&
                    m.parameterTypes.any { it == IntArray::class.java }
            }
            if (methods.isEmpty()) {
                logE("applyElementViewBlend* with IntArray not found")
                return
            }
            for (method in methods) {
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    if (reenteringBlend) {
                        return@intercept chain.proceed()
                    }
                    val args = chain.args.toTypedArray()
                    val view = args.firstOrNull { it is View } as? View
                    val colorIdx = args.indexOfFirst { it is IntArray }
                    val colors = if (colorIdx >= 0) args[colorIdx] as IntArray else null
                    if (view != null &&
                        colors != null &&
                        view.getTag(TAG_OWNED) == true &&
                        shouldTintNow(view)
                    ) {
                        lastSystemBlendPairs = colors.copyOf()
                        args[colorIdx] = MediaBgAlbumTintPolicy.retintBlendPairs(
                            colors,
                            lastTintRgb,
                            lastOpacity,
                        )
                        logI(
                            "retint glass pairs=${colors.size / 2} " +
                                "tint=#${Integer.toHexString(lastTintRgb)} opacity=$lastOpacity",
                        )
                        return@intercept chain.proceed(args)
                    }
                    chain.proceed()
                }
            }
            logI("hook applyElementViewBlend: OK count=${methods.size}")
        } catch (e: Throwable) {
            logE("hook applyElementViewBlend failed", e)
        }
    }

    private fun shouldTintNow(view: View): Boolean {
        if (lastTintRgb == Color.TRANSPARENT) return false
        val ctx = view.context ?: return false
        ConfigReader.invalidate()
        return MediaBgAlbumTintPolicy.shouldApply(ConfigReader.mediaBgAlbumTint(ctx))
    }

    private fun scheduleTintUpdate(mediaBg: ImageView, albumDrawable: Drawable?) {
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
        // 等系统写完玻璃混色后再用原 pairs 重入 applyElementViewBlend（hook 会染色）
        for (delay in RETRY_DELAYS_MS) {
            mainHandler.postDelayed({
                if (gen != applyGen) return@postDelayed
                val v = mediaBgRef?.get() ?: return@postDelayed
                if (!shouldTintNow(v)) return@postDelayed
                reapplyGlassTint(v)
            }, delay)
        }
        logI(
            "schedule glass tint=#${Integer.toHexString(lastTintRgb)} " +
                "opacity=$lastOpacity",
        )
    }

    private fun reapplyGlassTint(mediaBg: View) {
        val pairs = lastSystemBlendPairs ?: return
        val method = applyElementBlendMethod ?: return
        if (reenteringBlend) return
        reenteringBlend = true
        try {
            // 传入系统原始 pairs，hook 内再 retint，保留 setRoundRect / blurMode
            method.invoke(null, mediaBg.context, mediaBg, false, pairs, false)
        } catch (e: Throwable) {
            logE("reapplyGlassTint failed", e)
        } finally {
            reenteringBlend = false
        }
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
        private val RETRY_DELAYS_MS = longArrayOf(0L, 80L, 220L, 480L)
    }
}
