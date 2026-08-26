package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
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
 * 尺寸/圆角由配置决定；垂直位置与歌词同一逻辑：
 * 垂直位置：底边 = 屏高 × [albumAnchorY]%，由 [MediaFollowController] 维护。
 */
class BigAlbumOverlayView(context: Context) : FrameLayout(context) {

    private val albumView: ImageView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var configObserver: android.database.ContentObserver? = null
    private var configObserverRegistered = false

    /** 配置算出的边长（px），供 MediaFollow 在尚未 layout 时使用 */
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
        clipToOutline = true

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

    /** 只更新尺寸与圆角；top 由 MediaFollowController 负责 */
    fun applySizeFromConfig() {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sizePercent = ConfigReader.albumSize(context)
        val cornerDp = ConfigReader.albumCorner(context)
        val size = (sw * sizePercent / 100f).toInt().coerceAtLeast(1)
        val cornerPx = cornerDp * (sw / 360f)
        configuredSizePx = size
        // 低 elevation，避免盖住歌词层（歌词会单独抬高 z）
        elevation = 2f * dm.density

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
        if (lp.width != size || lp.height != size || lp.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)) {
            lp.width = size
            lp.height = size
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.leftMargin = 0
            lp.topMargin = 0
            lp.rightMargin = 0
            lp.bottomMargin = 0
            layoutParams = lp
        }
        MediaFollowController.requestReflow()
    }

    fun setAlbumArt(drawable: Drawable?) {
        if (drawable != null) albumView.setImageDrawable(drawable)
    }

    fun setAlbumBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            albumView.setImageBitmap(bitmap)
        }
    }

    fun clearAlbum() {
        albumView.setImageDrawable(null)
    }

    fun showForMusicLockscreen() {
        if (!ConfigReader.showBigAlbum(context)) {
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
