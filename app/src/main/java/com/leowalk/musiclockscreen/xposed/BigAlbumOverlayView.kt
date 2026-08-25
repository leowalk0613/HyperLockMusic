package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 大专辑封面 overlay
 *
 * 透明全屏背景，中间偏下位置显示带阴影的大专辑封面
 */
class BigAlbumOverlayView(context: Context) : FrameLayout(context) {

    private val tag = "MusicLockScreen_BigAlbum"
    private val albumView: ImageView
    private val container: FrameLayout
    private val albumSizeDp = 280 // 专辑图大小 dp
    private val cornerRadiusDp = 16 // 圆角 dp
    private val elevationDp = 24 // 阴影高度 dp

    init {
        setBackgroundColor(Color.TRANSPARENT)

        val albumSizePx = dpToPx(albumSizeDp)
        val cornerRadiusPx = dpToPx(cornerRadiusDp).toFloat()
        val elevationPx = dpToPx(elevationDp).toFloat()

        // 容器：用来实现阴影
        container = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            elevation = elevationPx
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
        }

        val containerParams = LayoutParams(albumSizePx, albumSizePx).apply {
            gravity = Gravity.CENTER // 屏幕正中间
        }

        // 专辑图
        albumView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        container.addView(albumView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(container, containerParams)
    }

    fun setAlbumArt(drawable: Drawable?) {
        if (drawable != null) {
            albumView.setImageDrawable(drawable)
        }
    }

    fun setAlbumBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            albumView.setImageBitmap(bitmap)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
