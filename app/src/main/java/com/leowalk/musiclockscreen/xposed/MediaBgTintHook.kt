package com.leowalk.musiclockscreen.xposed

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * 锁屏媒体控件：只给系统玻璃混色染专辑色。
 *
 * 真正落点是 [View.addMiBackgroundBlendColor] / [View.setMiBackgroundBlendColors]
 *（HyperChanger 同路径）。玻璃 View 可能是 `mediaBg`，更常见是通知行
 * `NotificationBackgroundView`（mBackgroundNormal）。
 */
class MediaBgTintHook {

    private val tag = "HyperLockMusic_MediaBgTint"
    private var module: XposedModule? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaBgRef: WeakReference<ImageView>? = null
    private val glassTargets = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>(),
    )

    private var lastTintRgb: Int = Color.TRANSPARENT
    private var lastOpacity: Int = MediaBgAlbumTintPolicy.DEFAULT_OPACITY_PERCENT
    private var lastSystemBlendPairs: IntArray? = null
    private var applyGen: Int = 0

    /** 我们自己写混色时跳过 hook，避免递归。 */
    private var bypassBlendHook: Boolean = false

    private var clearBlendMethod: Method? = null
    private var addBlendMethod: Method? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module
        try {
            logI("install start")
            resolveViewBlendApis()
            hookBindMediaData(classLoader, module)
            hookAddMiBackgroundBlendColor(module)
            hookSetMiBackgroundBlendColors(module)
            logI("MediaBgTintHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun resolveViewBlendApis() {
        try {
            clearBlendMethod = View::class.java.getMethod("clearMiBackgroundBlendColor")
                .also { it.isAccessible = true }
        } catch (e: Throwable) {
            logE("clearMiBackgroundBlendColor missing", e)
        }
        try {
            addBlendMethod = View::class.java.getMethod(
                "addMiBackgroundBlendColor",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).also { it.isAccessible = true }
        } catch (e: Throwable) {
            logE("addMiBackgroundBlendColor missing", e)
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
        val playerField = try {
            viewHolderClass.getDeclaredField("player").apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }

        module.hook(bindMethod).intercept { chain ->
            val result = chain.proceed()
            try {
                val holder = holderField.get(chain.thisObject) ?: return@intercept result
                val mediaBg = mediaBgField.get(holder) as? ImageView
                val albumIv = albumImageViewField.get(holder) as? ImageView
                val player = playerField?.get(holder) as? View
                if (mediaBg != null) {
                    mediaBgRef = WeakReference(mediaBg)
                    registerGlassTargets(mediaBg, player)
                    scheduleTintUpdate(mediaBg, albumIv?.drawable)
                }
            } catch (e: Throwable) {
                logE("after bindMediaData tint error", e)
            }
            result
        }
    }

    private fun registerGlassTargets(mediaBg: View, player: View?) {
        markGlass(mediaBg)
        findNotificationBackground(mediaBg)?.let { markGlass(it) }
        if (player != null) {
            markGlass(player)
            findNotificationBackground(player)?.let { markGlass(it) }
            findChildBackgroundViews(player).forEach { markGlass(it) }
        }
        // 向上找 MiuiMediaHeaderView / ExpandableNotificationRow
        var p = mediaBg.parent as? View
        var depth = 0
        while (p != null && depth < 12) {
            val name = p.javaClass.name
            if (name.contains("MiuiMediaHeaderView") ||
                name.contains("ExpandableNotificationRow")
            ) {
                markGlass(p)
                findNotificationBackground(p)?.let { markGlass(it) }
                tryGetBackgroundNormal(p)?.let { markGlass(it) }
            }
            p = p.parent as? View
            depth++
        }
        logI("glass targets=${glassTargets.size}")
    }

    private fun markGlass(view: View) {
        view.setTag(TAG_OWNED, true)
        glassTargets.add(view)
    }

    private fun isGlassTarget(view: View?): Boolean {
        if (view == null) return false
        if (view.getTag(TAG_OWNED) == true) return true
        return glassTargets.contains(view)
    }

    private fun findNotificationBackground(root: View): View? {
        tryGetBackgroundNormal(root)?.let { return it }
        if (root.javaClass.name.contains("NotificationBackgroundView")) return root
        if (root is ViewGroup) {
            val found = ArrayList<View>()
            collectByClassName(root, "NotificationBackgroundView", found)
            return found.firstOrNull()
        }
        return null
    }

    private fun findChildBackgroundViews(root: View): List<View> {
        if (root !is ViewGroup) return emptyList()
        val found = ArrayList<View>()
        collectByClassName(root, "NotificationBackgroundView", found)
        return found
    }

    private fun collectByClassName(group: ViewGroup, needle: String, out: MutableList<View>) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            if (child.javaClass.name.contains(needle)) out.add(child)
            if (child is ViewGroup) collectByClassName(child, needle, out)
        }
    }

    private fun tryGetBackgroundNormal(row: View): View? {
        return try {
            val f = row.javaClass.getDeclaredField("mBackgroundNormal").apply {
                isAccessible = true
            }
            f.get(row) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun hookAddMiBackgroundBlendColor(module: XposedModule) {
        val method = addBlendMethod ?: return
        module.hook(method).intercept { chain ->
            if (bypassBlendHook) return@intercept chain.proceed()
            val view = chain.thisObject as? View
            if (!isGlassTarget(view) || !shouldTintNow(view!!)) {
                return@intercept chain.proceed()
            }
            val color = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()
            val mode = chain.args.getOrNull(1) as? Int ?: return@intercept chain.proceed()
            rememberPair(color, mode)
            val tinted = MediaBgAlbumTintPolicy.retintBlendColor(color, lastTintRgb, lastOpacity)
            logI(
                "addBlend retint mode=$mode " +
                    "#${Integer.toHexString(color)} -> #${Integer.toHexString(tinted)}",
            )
            chain.proceed(arrayOf<Any?>(tinted, mode))
        }
        logI("hook addMiBackgroundBlendColor: OK")
    }

    private fun hookSetMiBackgroundBlendColors(module: XposedModule) {
        try {
            val method = View::class.java.getMethod(
                "setMiBackgroundBlendColors",
                ArrayList::class.java,
            )
            module.hook(method).intercept { chain ->
                if (bypassBlendHook) return@intercept chain.proceed()
                val view = chain.thisObject as? View
                val list = chain.args.getOrNull(0) as? ArrayList<*>
                if (view == null || list == null || !isGlassTarget(view) || !shouldTintNow(view)) {
                    return@intercept chain.proceed()
                }
                val tinted = ArrayList<Point>(list.size)
                for (item in list) {
                    when (item) {
                        is Point -> {
                            rememberPair(item.x, item.y)
                            tinted.add(
                                Point(
                                    MediaBgAlbumTintPolicy.retintBlendColor(
                                        item.x,
                                        lastTintRgb,
                                        lastOpacity,
                                    ),
                                    item.y,
                                ),
                            )
                        }
                        else -> return@intercept chain.proceed()
                    }
                }
                logI("setBlendColors retint size=${tinted.size}")
                chain.proceed(arrayOf<Any?>(tinted))
            }
            logI("hook setMiBackgroundBlendColors: OK")
        } catch (e: Throwable) {
            logE("hook setMiBackgroundBlendColors failed", e)
        }
    }

    private fun rememberPair(color: Int, mode: Int) {
        val prev = lastSystemBlendPairs
        lastSystemBlendPairs = if (prev == null || prev.size < 2) {
            intArrayOf(color, mode)
        } else {
            // 追加新档（系统常连续 add 两档）；同 mode 则更新
            val list = prev.toMutableList()
            var replaced = false
            for (i in 1 until list.size step 2) {
                if (list[i] == mode) {
                    list[i - 1] = color
                    replaced = true
                    break
                }
            }
            if (!replaced) {
                list.add(color)
                list.add(mode)
            }
            list.toIntArray()
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
        for (delay in RETRY_DELAYS_MS) {
            mainHandler.postDelayed({
                if (gen != applyGen) return@postDelayed
                if (lastTintRgb == Color.TRANSPARENT) return@postDelayed
                paintAlbumTintOnGlassTargets()
            }, delay)
        }
        logI(
            "schedule glass tint=#${Integer.toHexString(lastTintRgb)} " +
                "opacity=$lastOpacity targets=${glassTargets.size}",
        )
    }

    /**
     * 主动重写混色：只 clear+add blend color，不动 blurMode / radius / PassWindowBlur。
     */
    private fun paintAlbumTintOnGlassTargets() {
        val template = lastSystemBlendPairs
            ?: loadKeyguardBlendTemplate(mediaBgRef?.get())
            ?: MediaBgAlbumTintPolicy.buildFallbackBlendPairs(lastTintRgb, 100)
        val finalPairs = MediaBgAlbumTintPolicy.retintBlendPairs(
            template,
            lastTintRgb,
            lastOpacity,
        )
        val snapshot = glassTargets.toList()
        if (snapshot.isEmpty()) {
            logE("paint: no glass targets")
            return
        }
        bypassBlendHook = true
        try {
            for (view in snapshot) {
                if (!view.isAttachedToWindow) continue
                try {
                    clearBlendMethod?.invoke(view)
                    var i = 0
                    while (i + 1 < finalPairs.size) {
                        addBlendMethod?.invoke(view, finalPairs[i], finalPairs[i + 1])
                        i += 2
                    }
                } catch (e: Throwable) {
                    logE("paint target failed ${view.javaClass.simpleName}", e)
                }
            }
            logI(
                "painted ${snapshot.size} targets pairs=${finalPairs.size / 2} " +
                    "tint=#${Integer.toHexString(lastTintRgb)}",
            )
        } finally {
            bypassBlendHook = false
        }
    }

    private fun loadKeyguardBlendTemplate(anchor: View?): IntArray? {
        val view = anchor ?: return null
        return try {
            val res = view.resources
            val pkg = "com.android.systemui"
            val c1 = colorByName(res, pkg, "notification_element_blend_keyguard_color_1")
            val m1 = intByName(res, pkg, "notification_element_blend_keyguard_mode_1")
            val c2 = colorByName(res, pkg, "notification_element_blend_keyguard_color_2")
            val m2 = intByName(res, pkg, "notification_element_blend_keyguard_mode_2")
            if (c1 == null || m1 == null) return null
            if (c2 != null && m2 != null) intArrayOf(c1, m1, c2, m2) else intArrayOf(c1, m1)
        } catch (_: Throwable) {
            null
        }
    }

    private fun colorByName(res: Resources, pkg: String, name: String): Int? {
        val id = res.getIdentifier(name, "color", pkg)
        if (id == 0) return null
        return res.getColor(id, null)
    }

    private fun intByName(res: Resources, pkg: String, name: String): Int? {
        val id = res.getIdentifier(name, "integer", pkg)
        if (id == 0) return null
        return res.getInteger(id)
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
        Log.i(tag, msg)
        module?.log(Log.INFO, tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            Log.e(tag, msg, e)
            module?.log(Log.ERROR, tag, msg, e)
        } else {
            Log.e(tag, msg)
            module?.log(Log.ERROR, tag, msg)
        }
    }

    companion object {
        private const val TAG_OWNED = 0x7f1400B1
        private val RETRY_DELAYS_MS = longArrayOf(0L, 60L, 180L, 400L, 900L)
    }
}
