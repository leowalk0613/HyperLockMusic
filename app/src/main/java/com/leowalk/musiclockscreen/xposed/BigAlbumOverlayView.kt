package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
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
 * 普通模式：尺寸/圆角由配置决定，底边 = 屏高 × [albumAnchorY]%。
 * 沉浸模式：全宽大图，下缘羽化融入取色背景。
 */
class BigAlbumOverlayView(context: Context) : FrameLayout(context) {

    private val albumView: ImageView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var configObserver: android.database.ContentObserver? = null
    private var configObserverRegistered = false

    private var albumBitmap: Bitmap? = null
    private var dominantColor: Int = Color.BLACK
    private var immersiveMode = false

    private val immersivePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val colorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 配置算出的边长（px），供 MediaFollow 在尚未 layout 时使用 */
    var configuredSizePx: Int = 1
        private set

    /** 沉浸模式下的高度（px） */
    var configuredImmersiveHeightPx: Int = 1
        private set

    private companion object {
        const val CONFIG_URI = "content://com.leowalk.musiclockscreen.config/config"
        private const val IMMERSIVE_TOP_PERCENT = 4f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        visibility = GONE
        clipToOutline = true
        setWillNotDraw(false)

        albumView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(
            albumView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        applySizeFromConfig()
    }

    fun isImmersiveMode(): Boolean = immersiveMode

    /** 只更新尺寸与圆角；top 由 MediaFollowController 负责 */
    fun applySizeFromConfig() {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        immersiveMode = ConfigReader.shouldShowImmersiveAlbum(context) ||
            (ConfigReader.immersiveAlbum(context) && ConfigReader.immersiveLyric(context))

        if (immersiveMode) {
            configuredSizePx = sw
            val anchor = ConfigReader.albumAnchorY(context).coerceIn(10f, 95f)
            val topY = sh * IMMERSIVE_TOP_PERCENT / 100f
            val bottomY = sh * anchor / 100f
            configuredImmersiveHeightPx = (bottomY - topY).toInt().coerceAtLeast(1)
            clipToOutline = false
            outlineProvider = null
            albumView.visibility = GONE

            val lp = (layoutParams as? LayoutParams) ?: LayoutParams(sw, configuredImmersiveHeightPx).also {
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutParams = it
            }
            if (lp.width != sw || lp.height != configuredImmersiveHeightPx) {
                lp.width = sw
                lp.height = configuredImmersiveHeightPx
                lp.gravity = Gravity.TOP or Gravity.START
                lp.leftMargin = 0
                layoutParams = lp
            }
        } else {
            val sizePercent = ConfigReader.albumSize(context)
            val cornerDp = ConfigReader.albumCorner(context)
            val size = (sw * sizePercent / 100f).toInt().coerceAtLeast(1)
            val cornerPx = cornerDp * (sw / 360f)
            configuredSizePx = size
            configuredImmersiveHeightPx = size
            albumView.visibility = VISIBLE

            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
            clipToOutline = true

            val lp = (layoutParams as? LayoutParams) ?: LayoutParams(size, size).also {
                it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutParams = it
            }
            if (lp.width != size || lp.height != size ||
                lp.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            ) {
                lp.width = size
                lp.height = size
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.leftMargin = 0
                lp.topMargin = 0
                lp.rightMargin = 0
                lp.bottomMargin = 0
                layoutParams = lp
            }
        }

        elevation = 2f * dm.density
        MediaFollowController.requestReflow()
    }

    fun setAlbumArt(drawable: Drawable?) {
        if (drawable != null) albumView.setImageDrawable(drawable)
    }

    fun setAlbumBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            albumView.setImageBitmap(bitmap)
            albumBitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
            albumBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            dominantColor = BlurUtils.extractLowerHalfDominantColor(bitmap)
            invalidate()
        }
    }

    fun clearAlbum() {
        albumView.setImageDrawable(null)
        albumBitmap?.takeIf { !it.isRecycled }?.recycle()
        albumBitmap = null
        invalidate()
    }

    fun showForMusicLockscreen() {
        if (!ConfigReader.showBigAlbum(context)) {
            visibility = GONE
            return
        }
        val showImmersive = ConfigReader.shouldShowImmersiveAlbum(context)
        val showSquare = ConfigReader.shouldShowSquareAlbum(context)
        if (!showImmersive && !showSquare) {
            visibility = GONE
            return
        }
        applySizeFromConfig()
        alpha = 1f
        visibility = VISIBLE
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

    override fun dispatchDraw(canvas: Canvas) {
        if (!immersiveMode || albumBitmap == null || albumBitmap!!.isRecycled) {
            super.dispatchDraw(canvas)
            return
        }

        val bmp = albumBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val featherH = h * 0.22f
        val imageH = h - featherH * 0.35f

        val dst = RectF(0f, 0f, w, imageH)
        canvas.drawBitmap(bmp, null, dst, immersivePaint)

        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        fadePaint.shader = LinearGradient(
            0f, imageH - featherH, 0f, h,
            intArrayOf(Color.TRANSPARENT, dominantColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, imageH - featherH, w, h, fadePaint)

        colorFillPaint.color = dominantColor
        canvas.drawRect(0f, imageH, w, h, colorFillPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerConfigObserver()
    }

    override fun onDetachedFromWindow() {
        unregisterConfigObserver()
        super.onDetachedFromWindow()
    }

    private fun registerConfigObserver() {
        if (configObserverRegistered) return
        try {
            val uri = android.net.Uri.parse(CONFIG_URI)
            configObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    ConfigReader.invalidate()
                    if (visibility == VISIBLE || visibility == INVISIBLE) {
                        applySizeFromConfig()
                        invalidate()
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
