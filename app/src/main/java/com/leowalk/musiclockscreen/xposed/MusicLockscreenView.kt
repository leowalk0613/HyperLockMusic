package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.min

/**
 * 音乐锁屏自定义 View
 *
 * 包含：模糊背景 + 大专辑图 + 歌名 + 歌手
 */
class MusicLockscreenView(context: Context) : FrameLayout(context) {

    private val tag = "MusicLockScreen_View"

    // 背景模糊层
    private val bgImageView: ImageView
    // 大专辑图
    private val albumImageView: ImageView
    // 歌名
    private val titleText: TextView
    // 歌手
    private val artistText: TextView

    init {
        // 关闭自身和所有子 View 的裁剪
        setClipToPadding(false)
        setClipChildren(false)

        // 背景：放大 1.5 倍的模糊专辑图，确保边缘也能覆盖
        bgImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            scaleX = 1.5f
            scaleY = 1.5f
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 黑色遮罩
        val mask = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0.4f
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 内容容器（专辑图 + 文字）
        val contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // 专辑图
        albumImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 48)
        }

        // 歌名
        titleText = TextView(context).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(64, 0, 64, 8)
            includeFontPadding = false
        }

        // 歌手
        artistText = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(64, 0, 64, 0)
            includeFontPadding = false
        }

        // 添加到内容容器
        contentContainer.addView(albumImageView)
        contentContainer.addView(titleText, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 80 // 歌手文字高度 + 间距
        })
        contentContainer.addView(artistText, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 48
        })

        // 添加到根布局
        addView(bgImageView)
        addView(mask)
        addView(contentContainer)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = measuredWidth
        val height = measuredHeight

        // 专辑图大小：屏幕宽度的 70%，但不超过高度的 50%
        val albumSize = min((width * 0.7f).toInt(), (height * 0.5f).toInt())

        val albumLp = albumImageView.layoutParams as LayoutParams
        albumLp.width = albumSize
        albumLp.height = albumSize
        albumLp.gravity = Gravity.CENTER
        albumImageView.layoutParams = albumLp

        // 重新测量内容容器
        val contentContainer = getChildAt(2) as FrameLayout
        contentContainer.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(albumSize + 200, MeasureSpec.EXACTLY)
        )
    }

    /**
     * 更新专辑图
     */
    fun setAlbumArt(drawable: Drawable?) {
        if (drawable == null) return

        albumImageView.setImageDrawable(drawable)

        // 背景用同一张图做模糊
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            bgImageView.setImageBitmap(bitmap)
            // 高斯模糊
            try {
                val blurEffect = RenderEffect.createBlurEffect(
                    80f, 80f, Shader.TileMode.CLAMP
                )
                bgImageView.setRenderEffect(blurEffect)
            } catch (e: Throwable) {
                // 不支持的话就用暗背景
                bgImageView.setRenderEffect(null)
                bgImageView.setColorFilter(Color.parseColor("#80000000"))
            }
        }
    }

    /**
     * 更新歌曲信息
     */
    fun setMediaInfo(title: String?, artist: String?) {
        titleText.text = title ?: ""
        artistText.text = artist ?: ""
    }
}
