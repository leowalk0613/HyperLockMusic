package com.leowalk.musiclockscreen.xposed

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout

/**
 * 专辑 / 歌词垂直位置：底边 = 屏高 × 配置百分比，水平居中。
 * 沉浸歌词：与专辑同区块（方形），排版可左/中/右。
 */
object MediaFollowController {

    private const val TAG = "HyperLockMusic_MediaFollow"

    private var bgLayer: View? = null
    private var predrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var listening = false

    private var lastAlbumAnchor = Float.NaN
    private var lastLyricAnchor = Float.NaN
    private var lastAlbumSize = -1
    private var lastLyricHeight = -1

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun bindBackgroundLayer(layer: View?) {
        bgLayer = layer
    }

    fun bindMediaView(view: View?) {
        // 保留接口；定位不再依赖媒体控件
    }

    fun onMusicLockscreenShown() {
        ConfigReader.invalidate()
        invalidateCache()
        startListening()
        layoutAll()
        scheduleRetries()
    }

    fun onMusicLockscreenHidden() {
        stopListening()
        invalidateCache()
        resetTransforms()
    }

    fun onKeyguardShown() {
        if (!MusicLockscreenManager.isShowing) return
        ConfigReader.invalidate()
        invalidateCache()
        startListening()
        layoutAll()
        bgLayer?.post { layoutAll() }
    }

    fun requestReflow() {
        ConfigReader.invalidate()
        invalidateCache()
        layoutAll()
    }

    /** 歌词自调高度后同步缓存，避免下一帧 MediaFollow 再挪一次造成闪 */
    fun syncLyricLaidOut(height: Int) {
        if (height > 0) lastLyricHeight = height
    }

    private fun scheduleRetries() {
        val bg = bgLayer ?: return
        bg.post { layoutAll() }
        bg.postDelayed({ layoutAll() }, 100L)
        bg.postDelayed({ layoutAll() }, 300L)
    }

    private fun invalidateCache() {
        lastAlbumAnchor = Float.NaN
        lastLyricAnchor = Float.NaN
        lastAlbumSize = -1
        lastLyricHeight = -1
    }

    private fun startListening() {
        val target = bgLayer ?: return
        if (listening) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            layoutAll()
            true
        }
        predrawListener = listener
        try {
            target.viewTreeObserver.addOnPreDrawListener(listener)
            listening = true
        } catch (e: Throwable) {
            logE("startListening failed", e)
            listening = false
        }
    }

    private fun stopListening() {
        val target = bgLayer
        val listener = predrawListener
        if (target != null && listener != null) {
            try {
                if (target.viewTreeObserver.isAlive) {
                    target.viewTreeObserver.removeOnPreDrawListener(listener)
                }
            } catch (_: Throwable) {
            }
        }
        predrawListener = null
        listening = false
    }

    private fun layoutAll() {
        if (!MusicLockscreenManager.isShowing) return
        val bg = bgLayer
            ?: (MusicLockscreenManager.lyricView?.parent as? View)
            ?: (MusicLockscreenManager.bigAlbumView?.parent as? View)
            ?: return
        if (bg.width <= 0 || bg.height <= 0) return

        if (isBouncerShowing(bg)) {
            MusicLockscreenManager.bigAlbumView?.takeIf { it.visibility == View.VISIBLE }
                ?.visibility = View.INVISIBLE
            MusicLockscreenManager.lyricView?.takeIf { it.visibility == View.VISIBLE }
                ?.visibility = View.INVISIBLE
            resetTransforms()
            return
        }

        layoutAlbum(bg)
        layoutLyric(bg)
        resetTransforms()
        ensureLyricOnTop()
    }

    private fun layoutAlbum(bg: View) {
        val album = MusicLockscreenManager.bigAlbumView ?: return
        val ctx = bg.context

        if (!ConfigReader.showBigAlbum(ctx) || !ConfigReader.shouldShowSquareAlbum(ctx)) {
            album.visibility = View.GONE
            return
        }

        if (!LockscreenNotificationController.shouldShowKeyguardOverlays()) {
            album.visibility = View.GONE
            return
        }

        val anchor = ConfigReader.albumAnchorY(ctx).coerceIn(10f, 95f)
        val contentSize = album.configuredSizePx.takeIf { it > 0 }
            ?: album.layoutParams?.width?.takeIf { it > 0 }
            ?: (bg.width * ConfigReader.albumSize(ctx) / 100f).toInt().coerceAtLeast(1)
        val layoutW = album.layoutWidthPx.takeIf { it > contentSize } ?: contentSize
        val layoutH = album.layoutHeightPx.takeIf { it > contentSize } ?: contentSize

        if (lastAlbumAnchor != anchor || lastAlbumSize != contentSize) {
            if (placeByScreenHeight(album, layoutW, layoutH, anchor, contentSize)) {
                logI("album bottom=${anchor}% content=$contentSize layout=${layoutW}x$layoutH bgH=${bg.height}")
            }
            lastAlbumAnchor = anchor
            lastAlbumSize = contentSize
        }

        album.alpha = 1f
        if (album.visibility != View.VISIBLE) album.visibility = View.VISIBLE
    }

    private fun layoutLyric(bg: View) {
        val lyric = MusicLockscreenManager.lyricView ?: return
        if (lyric.visibility == View.GONE) return

        val ctx = bg.context
        val immersiveLyric = ConfigReader.immersiveLyric(ctx)
        val anchor = if (immersiveLyric) {
            ConfigReader.albumAnchorY(ctx).coerceIn(10f, 95f)
        } else {
            ConfigReader.lyricBgAnchorY(ctx).coerceIn(10f, 95f)
        }

        val w = when {
            immersiveLyric -> {
                val size = (bg.width * ConfigReader.albumSize(ctx) / 100f).toInt().coerceAtLeast(1)
                size
            }
            lyric.width > 0 -> lyric.width
            lyric.measuredWidth > 0 -> lyric.measuredWidth
            lyric.layoutParams?.width?.let { it > 0 } == true -> lyric.layoutParams!!.width
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }

        val h = when {
            immersiveLyric -> w
            lyric.height > 0 -> lyric.height
            lyric.measuredHeight > 0 -> lyric.measuredHeight
            else -> 0
        }

        if (!immersiveLyric && h <= 0) {
            val guess = (48f * bg.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            placeByScreenHeight(lyric, w, guess, anchor)
            return
        }

        if (lastLyricAnchor != anchor || lastLyricHeight != h) {
            if (immersiveLyric) {
                placeImmersiveLyric(lyric, w, h, anchor)
                logI("immersive lyric bottom=$anchor% block=${w}x$h")
            } else if (placeByScreenHeight(lyric, w, h, anchor)) {
                logI("lyric bottom=${anchor}% h=$h bgH=${bg.height}")
            }
            lastLyricAnchor = anchor
            lastLyricHeight = h
        }
        lyric.alpha = 1f
        if (lyric.visibility == View.INVISIBLE) lyric.visibility = View.VISIBLE
    }

    private fun placeImmersiveLyric(target: View, width: Int, height: Int, bottomAnchorPercent: Float) {
        val parent = target.parent as? View ?: return
        val bgH = parent.height
        if (bgH <= 0 || height <= 0) return

        val bottomY = bgH * (bottomAnchorPercent / 100f)
        val top = (bottomY - height).toInt().coerceAtLeast(0)
        val left = ((parent.width - width) / 2f).toInt().coerceAtLeast(0)

        val lp = target.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        var changed = false
        if (lp.width != width) {
            lp.width = width
            changed = true
        }
        if (lp.height != height) {
            lp.height = height
            changed = true
        }
        if (lp.topMargin != top) {
            lp.topMargin = top
            changed = true
        }
        if (lp.leftMargin != left) {
            lp.leftMargin = left
            changed = true
        }
        if (lp.rightMargin != 0) {
            lp.rightMargin = 0
            changed = true
        }
        if (lp.bottomMargin != 0) {
            lp.bottomMargin = 0
            changed = true
        }
        if (lp is FrameLayout.LayoutParams &&
            lp.gravity != (Gravity.TOP or Gravity.START)
        ) {
            lp.gravity = Gravity.TOP or Gravity.START
            changed = true
        }
        if (changed) target.layoutParams = lp
        resetViewTransform(target)
    }

    /**
     * 底边落在屏高 [bottomAnchorPercent]%，水平居中（只用 topMargin，不用 leftMargin）。
     */
    private fun placeByScreenHeight(
        target: View,
        width: Int,
        height: Int,
        bottomAnchorPercent: Float,
        contentHeight: Int = height
    ): Boolean {
        val parent = target.parent as? View ?: return false
        val bgH = parent.height
        if (bgH <= 0 || height <= 0) return false

        val bottomY = bgH * (bottomAnchorPercent / 100f)
        val lp = target.layoutParams ?: return false
        var changed = false
        val marginLp = lp as? ViewGroup.MarginLayoutParams ?: return false

        if (target is BigAlbumOverlayView) {
            val contentSize = contentHeight.coerceAtLeast(1)
            val top = (bottomY - contentSize - target.contentPadTopPx).toInt().coerceAtLeast(0)
            val left = ((parent.width - contentSize) / 2f - target.contentPadLeftPx)
                .toInt()
                .coerceAtLeast(0)

            if (width > 0 && lp.width != width) {
                lp.width = width
                changed = true
            }
            if (lp.height != height) {
                lp.height = height
                changed = true
            }
            if (marginLp.topMargin != top) {
                marginLp.topMargin = top
                changed = true
            }
            if (marginLp.leftMargin != left) {
                marginLp.leftMargin = left
                changed = true
            }
            if (marginLp.rightMargin != 0) {
                marginLp.rightMargin = 0
                changed = true
            }
            if (marginLp.bottomMargin != 0) {
                marginLp.bottomMargin = 0
                changed = true
            }
            if (lp is FrameLayout.LayoutParams) {
                val g = Gravity.TOP or Gravity.START
                if (lp.gravity != g) {
                    lp.gravity = g
                    changed = true
                }
            }
            if (changed) target.layoutParams = lp
            resetViewTransform(target)
            return changed
        }

        val top = (bottomY - contentHeight).toInt().coerceAtLeast(0)
        if (marginLp.topMargin != top) {
            marginLp.topMargin = top
            changed = true
        }
        if (marginLp.leftMargin != 0) {
            marginLp.leftMargin = 0
            changed = true
        }
        if (marginLp.rightMargin != 0) {
            marginLp.rightMargin = 0
            changed = true
        }
        if (marginLp.bottomMargin != 0) {
            marginLp.bottomMargin = 0
            changed = true
        }

        if (lp is FrameLayout.LayoutParams) {
            val g = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (lp.gravity != g) {
                lp.gravity = g
                changed = true
            }
        }

        if (changed) target.layoutParams = lp
        resetViewTransform(target)
        return changed
    }

    private fun resetViewTransform(target: View) {
        if (target.translationX != 0f) target.translationX = 0f
        if (target.translationY != 0f) target.translationY = 0f
        if (target.scaleX != 1f) target.scaleX = 1f
        if (target.scaleY != 1f) target.scaleY = 1f
    }

    private fun isBouncerShowing(anchor: View): Boolean {
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

    private fun ensureLyricOnTop() {
        val lyric = MusicLockscreenManager.lyricView ?: return
        if (lyric.visibility == View.GONE) return
        val d = lyric.resources.displayMetrics.density
        try {
            MusicLockscreenManager.bigAlbumView?.let { album ->
                album.elevation = 2f * d
                album.translationZ = 0f
            }
            // 歌词保持模块内最高（高于专辑）；过渡遮罩仅在可见时临时压过
            lyric.elevation = 48f * d
            lyric.translationZ = 48f * d
            lyric.bringToFront()
            val mask = MusicLockscreenManager.transitionMaskView
            if (mask != null && mask.visibility == View.VISIBLE && mask.alpha > 0.01f) {
                mask.elevation = 64f * d
                mask.translationZ = 64f * d
                mask.bringToFront()
            }
        } catch (_: Throwable) {
        }
    }

    private fun resetTransforms() {
        listOf(MusicLockscreenManager.bigAlbumView, MusicLockscreenManager.lyricView).forEach { v ->
            v ?: return@forEach
            resetViewTransform(v)
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
