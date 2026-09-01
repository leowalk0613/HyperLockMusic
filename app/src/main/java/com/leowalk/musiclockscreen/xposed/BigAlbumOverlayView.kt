package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 大专辑封面 overlay（仅音乐锁屏可见）。
 *
 * 方形大封面：尺寸/圆角由配置决定，底边 = 屏高 × [albumAnchorY]%。
 * 沉浸专辑由 [WallpaperController] 合成进壁纸，此处不再绘制。
 *
 * 性能：封面 + 阴影在切歌 / 改尺寸时离屏合成一次，之后每帧只 blit 缓存位图。
 */
class BigAlbumOverlayView(context: Context) : FrameLayout(context) {

    private val albumView: ImageView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var configObserver: android.database.ContentObserver? = null
    private var configObserverRegistered = false

    /** overlay 独占副本，避免 WallpaperController 回收后仍引用原 bitmap */
    private var ownedAlbumBitmap: Bitmap? = null

    /** 阴影 + 圆角封面离屏缓存；曲目 / 尺寸不变时不重建 */
    private var compositeBitmap: Bitmap? = null
    private var compositeCacheKey: Long = 0L

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var cornerRadiusPx = 0f
    private var shadowPadLeftPx = 0
    private var shadowPadTopPx = 0
    private var shadowPadRightPx = 0
    private var shadowPadBottomPx = 0
    private var shadowOffsetXPx = 0f
    private var shadowOffsetYPx = 0f
    private var shadowBlurPx = 0f

    /** 非 Bitmap 封面时回退旧路径（每帧 saveLayer） */
    private var legacyDrawableFallback = false

    /** 含阴影留白的外层尺寸，供布局使用 */
    var layoutWidthPx: Int = 1
        private set

    var layoutHeightPx: Int = 1
        private set

    /** 专辑内容区相对外层左上角的留白（阴影绘制在 content 区外侧） */
    val contentPadLeftPx: Int get() = shadowPadLeftPx
    val contentPadTopPx: Int get() = shadowPadTopPx

    /** 配置算出的专辑边长（px），供 MediaFollow 定位内容区 */
    var configuredSizePx: Int = 1
        private set

    private companion object {
        const val CONFIG_URI = "content://com.leowalk.musiclockscreen.config/config"
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        visibility = GONE
        clipToOutline = false
        setWillNotDraw(false)

        albumView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = GONE
        }
        addView(
            albumView,
            LayoutParams(1, 1).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )
        applySizeFromConfig()
    }

    /** 只更新尺寸与圆角；top 由 MediaFollowController 负责 */
    fun applySizeFromConfig() {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sizePercent = ConfigReader.albumSize(context)
        val cornerDp = ConfigReader.albumCorner(context)
        val size = (sw * sizePercent / 100f).toInt().coerceAtLeast(1)
        val cornerPx = cornerDp * (sw / 360f)
        cornerRadiusPx = cornerPx
        configuredSizePx = size
        updateSquareShadowInsets(sw)
        layoutWidthPx = size + shadowPadLeftPx + shadowPadRightPx
        layoutHeightPx = size + shadowPadTopPx + shadowPadBottomPx

        val cornerProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
        clipToOutline = false
        outlineProvider = null
        albumView.outlineProvider = cornerProvider
        albumView.clipToOutline = true

        val albumLp = (albumView.layoutParams as? LayoutParams)
            ?: LayoutParams(size, size).also { albumView.layoutParams = it }
        var layoutChanged = false
        if (albumLp.width != size || albumLp.height != size ||
            albumLp.leftMargin != shadowPadLeftPx || albumLp.topMargin != shadowPadTopPx
        ) {
            albumLp.width = size
            albumLp.height = size
            albumLp.gravity = Gravity.TOP or Gravity.START
            albumLp.leftMargin = shadowPadLeftPx
            albumLp.topMargin = shadowPadTopPx
            albumLp.rightMargin = 0
            albumLp.bottomMargin = 0
            albumView.layoutParams = albumLp
            layoutChanged = true
        }

        val lp = (layoutParams as? LayoutParams) ?: LayoutParams(layoutWidthPx, layoutHeightPx).also {
            it.gravity = Gravity.TOP or Gravity.START
            layoutParams = it
        }
        if (lp.width != layoutWidthPx || lp.height != layoutHeightPx ||
            lp.gravity != (Gravity.TOP or Gravity.START)
        ) {
            lp.width = layoutWidthPx
            lp.height = layoutHeightPx
            lp.gravity = Gravity.TOP or Gravity.START
            lp.leftMargin = 0
            lp.topMargin = 0
            lp.rightMargin = 0
            lp.bottomMargin = 0
            layoutParams = lp
            layoutChanged = true
        }

        elevation = 2f * dm.density
        rebuildCompositeCacheIfNeeded()
        if (layoutChanged) {
            MediaFollowController.requestReflow()
        }
    }

    fun setAlbumArt(drawable: Drawable?) {
        if (drawable == null) {
            clearAlbum()
            return
        }
        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap
            if (bmp != null && !bmp.isRecycled) {
                legacyDrawableFallback = false
                setAlbumBitmap(bmp)
                return
            }
        }
        legacyDrawableFallback = true
        releaseCompositeCache()
        albumView.visibility = VISIBLE
        albumView.setImageDrawable(drawable)
        invalidate()
    }

    fun setAlbumBitmap(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        val copy = try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            return
        }
        legacyDrawableFallback = false
        albumView.visibility = GONE
        albumView.setImageDrawable(null)
        ownedAlbumBitmap?.takeIf { it !== copy && !it.isRecycled }?.recycle()
        ownedAlbumBitmap = copy
        rebuildCompositeCacheIfNeeded()
    }

    fun clearAlbum() {
        legacyDrawableFallback = false
        albumView.setImageDrawable(null)
        albumView.visibility = GONE
        ownedAlbumBitmap?.takeIf { !it.isRecycled }?.recycle()
        ownedAlbumBitmap = null
        releaseCompositeCache()
    }

    fun showForMusicLockscreen() {
        if (!ConfigReader.showBigAlbum(context)) {
            visibility = GONE
            return
        }
        val hold = MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled
        if (!ConfigReader.shouldShowSquareAlbum(context) && !hold) {
            visibility = GONE
            return
        }
        applySizeFromConfig()
        alpha = 1f
        visibility = VISIBLE
        rebuildCompositeCacheIfNeeded()
        MediaFollowController.requestReflow()
    }

    fun hideForMusicLockscreenOff() {
        animate().cancel()
        translationX = 0f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        visibility = GONE
        clearAlbum()
    }

    private fun currentCompositeSpec(album: Bitmap): BigAlbumOverlayComposite.Spec {
        return BigAlbumOverlayComposite.Spec(
            layoutW = layoutWidthPx,
            layoutH = layoutHeightPx,
            contentSizePx = configuredSizePx,
            cornerRadiusPx = cornerRadiusPx,
            shadowBlurPx = shadowBlurPx,
            shadowOffsetX = shadowOffsetXPx,
            shadowOffsetY = shadowOffsetYPx,
            padLeft = shadowPadLeftPx,
            padTop = shadowPadTopPx,
            albumIdentity = System.identityHashCode(album),
            albumWidth = album.width,
            albumHeight = album.height,
        )
    }

    private fun rebuildCompositeCacheIfNeeded() {
        if (legacyDrawableFallback) return
        val album = ownedAlbumBitmap
        if (album == null || album.isRecycled) {
            releaseCompositeCache()
            return
        }
        val spec = currentCompositeSpec(album)
        if (!BigAlbumOverlayComposite.shouldRebuild(compositeCacheKey, spec)) {
            return
        }
        releaseCompositeCache()
        val w = layoutWidthPx
        val h = layoutHeightPx
        if (w <= 0 || h <= 0 || configuredSizePx <= 0) return
        val out = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return
        }
        val canvas = Canvas(out)
        drawCompositeOntoCanvas(canvas, album, configuredSizePx.toFloat())
        compositeBitmap = out
        compositeCacheKey = BigAlbumOverlayComposite.cacheKey(spec)
        invalidate()
    }

    private fun releaseCompositeCache() {
        compositeBitmap?.takeIf { !it.isRecycled }?.recycle()
        compositeBitmap = null
        compositeCacheKey = 0L
    }

    private fun updateSquareShadowInsets(screenWidth: Int) {
        val scale = screenWidth / 1080f
        shadowBlurPx = 40f * scale
        shadowOffsetXPx = 8f * scale
        shadowOffsetYPx = 20f * scale
        shadowPadLeftPx = shadowBlurPx.toInt().coerceAtLeast(1)
        shadowPadTopPx = shadowBlurPx.toInt().coerceAtLeast(1)
        shadowPadRightPx = (shadowOffsetXPx + shadowBlurPx).toInt().coerceAtLeast(1)
        shadowPadBottomPx = (shadowOffsetYPx + shadowBlurPx).toInt().coerceAtLeast(1)
    }

    private fun drawSquareDropShadow(canvas: Canvas, contentSize: Float, cornerPx: Float) {
        val left = shadowPadLeftPx.toFloat()
        val top = shadowPadTopPx.toFloat()
        shadowPaint.color = Color.argb(100, 0, 0, 0)
        shadowPaint.maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            RectF(
                left + shadowOffsetXPx,
                top + shadowOffsetYPx,
                left + contentSize + shadowOffsetXPx,
                top + contentSize + shadowOffsetYPx
            ),
            cornerPx,
            cornerPx,
            shadowPaint
        )
        shadowPaint.maskFilter = null
    }

    private fun drawCompositeOntoCanvas(canvas: Canvas, album: Bitmap, contentSize: Float) {
        drawSquareDropShadow(canvas, contentSize, cornerRadiusPx)
        val left = shadowPadLeftPx.toFloat()
        val top = shadowPadTopPx.toFloat()
        val rect = RectF(left, top, left + contentSize, top + contentSize)
        val scaled = scaleCenterCrop(album, contentSize.toInt(), contentSize.toInt())
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val albumPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val layer = canvas.saveLayer(rect, null)
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, clipPaint)
        albumPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, null, rect, albumPaint)
        albumPaint.xfermode = null
        canvas.restoreToCount(layer)
        if (scaled !== album) {
            scaled.recycle()
        }
    }

    private fun scaleCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val sw = src.width.coerceAtLeast(1)
        val sh = src.height.coerceAtLeast(1)
        val scale = maxOf(targetW.toFloat() / sw, targetH.toFloat() / sh)
        val scaledW = (sw * scale).toInt().coerceAtLeast(1)
        val scaledH = (sh * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val y = ((scaledH - targetH) / 2).coerceAtLeast(0)
        return if (x == 0 && y == 0 && scaledW == targetW && scaledH == targetH) {
            scaled
        } else {
            val cropped = Bitmap.createBitmap(scaled, x, y, targetW, targetH)
            if (cropped !== scaled) scaled.recycle()
            cropped
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val cache = compositeBitmap
        if (!legacyDrawableFallback && cache != null && !cache.isRecycled) {
            canvas.drawBitmap(cache, 0f, 0f, blitPaint)
            return
        }
        val drawable = albumView.drawable
        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap
            if (bmp.isRecycled) {
                clearAlbum()
                super.dispatchDraw(canvas)
                return
            }
        }
        val contentSize = configuredSizePx.toFloat()
        if (albumView.visibility == VISIBLE && contentSize > 0f) {
            albumView.visibility = VISIBLE
            val layer = canvas.saveLayer(
                0f, 0f, width.toFloat(), height.toFloat(), null
            )
            drawSquareDropShadow(canvas, contentSize, cornerRadiusPx)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(layer)
        } else {
            super.dispatchDraw(canvas)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerConfigObserver()
    }

    override fun onDetachedFromWindow() {
        unregisterConfigObserver()
        releaseCompositeCache()
        super.onDetachedFromWindow()
    }

    private fun registerConfigObserver() {
        if (configObserverRegistered) return
        try {
            val uri = android.net.Uri.parse(CONFIG_URI)
            configObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    val bakeBefore = ConfigReader.shouldBakeImmersiveAlbumInWallpaper(context)
                    val centerBefore =
                        if (bakeBefore) ConfigReader.immersiveAlbumCenterY(context) else Float.NaN
                    ConfigReader.invalidate()
                    val bakeAfter = ConfigReader.shouldBakeImmersiveAlbumInWallpaper(context)
                    val centerAfter =
                        if (bakeAfter) ConfigReader.immersiveAlbumCenterY(context) else Float.NaN
                    if (MusicLockscreenManager.isShowing) {
                        val layoutChanged = immersiveAlbumLayoutChanged(
                            bakeBefore, bakeAfter, centerBefore, centerAfter
                        )
                        if (layoutChanged) {
                            if (bakeAfter) {
                                val squareVisible = MusicLockscreenManager.isSquareAlbumOverlayVisible()
                                MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled =
                                    squareVisible
                                if (squareVisible) {
                                    MusicLockscreenManager.showAlbumOverlay()
                                }
                            } else {
                                MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled = false
                                MusicLockscreenManager.showAlbumOverlay()
                            }
                            WallpaperController.refreshWallpaperForAlbumVisibility(context)
                        } else {
                            MusicLockscreenManager.showAlbumOverlay()
                        }
                    } else if (visibility == VISIBLE || visibility == INVISIBLE) {
                        applySizeFromConfig()
                    } else {
                        MediaFollowController.requestReflow()
                    }
                }
            }
            context.contentResolver.registerContentObserver(uri, true, configObserver!!)
            configObserverRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun unregisterConfigObserver() {
        if (!configObserverRegistered) return
        try {
            configObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        } catch (_: Throwable) {
        }
        configObserver = null
        configObserverRegistered = false
    }
}
