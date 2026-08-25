package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * 模糊工具类
 *
 * 背景采用「缩小 → 轻量模糊 → 放大」得到柔和色块，避免大半径 StackBlur 的横竖条纹。
 */
object BlurUtils {

    private const val tag = "MusicLockScreen_Blur"

    /**
     * 简单的 dp 转 px（假设屏幕宽度 1080px = 360dp，密度 3x）
     */
    private fun dpToPx(screenWidthPx: Int, dp: Float): Float {
        val density = screenWidthPx / 360f
        return dp * density
    }

    /**
     * iOS 风格模糊 + 大专辑封面合成
     *
     * @param albumBitmap 原始专辑图
     * @param radius 模糊半径
     * @param darkOverlayAlpha 暗色遮罩透明度
     * @param showBigAlbum 是否显示大专辑封面
     * @param targetWidth 目标壁纸宽度
     * @param targetHeight 目标壁纸高度
     */
    fun blurWithBigAlbum(
        albumBitmap: Bitmap,
        radius: Float,
        darkOverlayAlpha: Int = 140,
        showBigAlbum: Boolean = true,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        albumSizePercent: Float = 55f,
        albumOffsetYDp: Float = -80f,
        albumCornerDp: Float = 24f
    ): Bitmap {
        val tw = if (targetWidth > 0) targetWidth else 1080
        val th = if (targetHeight > 0) targetHeight else 2400

        // 模糊底图：先 center-crop 到屏幕比例，保证全屏有足够像素
        val blurBaseW = tw.coerceAtMost(1440)
        val blurBaseH = (th.toFloat() * blurBaseW / tw).toInt().coerceAtLeast(1)
        val cover = scaleCenterCrop(albumBitmap, blurBaseW, blurBaseH)

        val blurred = softColorBlur(cover, radius)
        cover.recycle()

        val wallpaper = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wallpaper)

        val blW = blurred.width
        val blH = blurred.height
        val scale = maxOf(tw.toFloat() / blW, th.toFloat() / blH)
        val drawW = blW * scale
        val drawH = blH * scale
        val left = (tw - drawW) / 2
        val top = (th - drawH) / 2
        val srcRect = android.graphics.Rect(0, 0, blW, blH)
        val dstRect = android.graphics.RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(blurred, srcRect, dstRect, null)

        // 5. 叠加暗色遮罩
        val paint = Paint().apply {
            color = Color.argb(darkOverlayAlpha, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, tw.toFloat(), th.toFloat(), paint)

        if (showBigAlbum) {
            val albumSize = (tw * albumSizePercent / 100f).toInt()
            val albumLeft = (tw - albumSize) / 2f
            val albumTop = (th - albumSize) / 2f + dpToPx(tw, albumOffsetYDp)
            val cornerPx = dpToPx(tw, albumCornerDp)
            val albumRect = RectF(albumLeft, albumTop, albumLeft + albumSize, albumTop + albumSize)

            // 阴影
            val shadowPaint = Paint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
                color = Color.argb(100, 0, 0, 0)
            }
            val shadowRect = RectF(
                albumLeft + 8f,
                albumTop + 20f,
                albumLeft + albumSize + 8f,
                albumTop + albumSize + 20f
            )
            canvas.drawRoundRect(shadowRect, cornerPx, cornerPx, shadowPaint)

            val albumScaled = scaleCenterCrop(albumBitmap, albumSize, albumSize)
            val albumPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            val saveCount = canvas.saveLayer(albumRect, null)
            canvas.drawRoundRect(albumRect, cornerPx, cornerPx, Paint(Paint.ANTI_ALIAS_FLAG))
            albumPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(albumScaled, null, albumRect, albumPaint)
            albumPaint.xfermode = null
            canvas.restoreToCount(saveCount)

            if (albumScaled !== albumBitmap) {
                albumScaled.recycle()
            }
        }

        blurred.recycle()

        return wallpaper
    }

    /**
     * 柔和色块模糊：先大幅缩小再放大，低分辨率下做小半径多遍模糊。
     * 避免在全尺寸图上做大半径 separable blur 产生的横/竖条纹。
     * 壁纸背景与歌词雾状背景共用此算法。
     */
    fun softColorBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap.copy(bitmap.config, true)

        val r = radius.coerceIn(4f, 150f)
        val targetMaxSide = (240f - r * 1.5f).coerceIn(36f, 128f).toInt()
        val downScale = targetMaxSide.toFloat() / max(w, h)
        val sw = max(1, (w * downScale).toInt())
        val sh = max(1, (h * downScale).toInt())

        var work = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        val shrink = when {
            r >= 70f -> 0.3f
            r >= 40f -> 0.45f
            else -> 0.6f
        }
        val tinyW = max(1, (sw * shrink).toInt())
        val tinyH = max(1, (sh * shrink).toInt())
        val tiny = Bitmap.createScaledBitmap(work, tinyW, tinyH, true)
        work.recycle()
        work = Bitmap.createScaledBitmap(tiny, sw, sh, true)
        tiny.recycle()

        val passes = if (r >= 50f) 3 else 2
        val passRadius = max(2, min(8, (r / 22f).toInt()))
        repeat(passes) {
            val next = stackBlur(work, passRadius)
            work.recycle()
            work = next
        }

        val result = Bitmap.createScaledBitmap(work, w, h, true)
        work.recycle()
        return result
    }

    /** 居中裁剪缩放，用于模糊底图与大专辑 */
    private fun scaleCenterCrop(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (dstW <= 0 || dstH <= 0) return src
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = maxOf(dstW / srcW, dstH / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - dstW) / 2).coerceAtLeast(0)
        val y = ((scaledH - dstH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, x, y, dstW.coerceAtMost(scaledW), dstH.coerceAtMost(scaledH))
        if (scaled != cropped) scaled.recycle()
        return cropped
    }

    /**
     * iOS 风格模糊：先放大 → StackBlur → 暗色遮罩
     */
    @Deprecated("Use blurWithBigAlbum instead")
    fun blurIosStyle(bitmap: Bitmap, radius: Float, darkOverlayAlpha: Int = 80): Bitmap {
        // 1. 先放大到屏幕尺寸（避免边缘发黑，模拟 iOS 的大半径模糊）
        val screenWidth = 1080 // 目标宽度，实际运行时会自适应
        val scaleFactor = 2f // 放大倍数
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scaleFactor).toInt(),
            (bitmap.height * scaleFactor).toInt(),
            true
        )

        // 2. StackBlur 模糊
        val blurred = stackBlur(scaled, radius.toInt())

        // 3. 叠加暗色遮罩
        val result = Bitmap.createBitmap(blurred.width, blurred.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(blurred, 0f, 0f, null)

        val paint = Paint().apply {
            color = Color.argb(darkOverlayAlpha, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, blurred.width.toFloat(), blurred.height.toFloat(), paint)

        scaled.recycle()
        blurred.recycle()

        return result
    }

    /**
     * StackBlur 算法 - 高效的盒式模糊近似高斯模糊
     * 基于 Mario Klingemann 的 StackBlur 算法
     */
    fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return bitmap.copy(bitmap.config, true)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val resultPixels = IntArray(w * h)
        stackBlurHorizontal(pixels, resultPixels, w, h, radius)
        stackBlurVertical(resultPixels, pixels, w, h, radius)

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun stackBlurHorizontal(input: IntArray, output: IntArray, w: Int, h: Int, r: Int) {
        val widthMinus1 = w - 1
        val div = r + r + 1

        for (y in 0 until h) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var aSum = 0

            val rowStart = y * w

            // 初始化窗口
            for (i in -r..r) {
                val pixel = input[rowStart + (i.coerceIn(0, widthMinus1))]
                rSum += (pixel shr 16) and 0xFF
                gSum += (pixel shr 8) and 0xFF
                bSum += pixel and 0xFF
                aSum += (pixel shr 24) and 0xFF
            }

            for (x in 0 until w) {
                output[rowStart + x] = ((aSum / div) shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)

                // 滑动窗口
                val pixelOut = input[rowStart + ((x - r).coerceIn(0, widthMinus1))]
                val pixelIn = input[rowStart + ((x + r + 1).coerceIn(0, widthMinus1))]

                rSum += ((pixelIn shr 16) and 0xFF) - ((pixelOut shr 16) and 0xFF)
                gSum += ((pixelIn shr 8) and 0xFF) - ((pixelOut shr 8) and 0xFF)
                bSum += (pixelIn and 0xFF) - (pixelOut and 0xFF)
                aSum += ((pixelIn shr 24) and 0xFF) - ((pixelOut shr 24) and 0xFF)
            }
        }
    }

    private fun stackBlurVertical(input: IntArray, output: IntArray, w: Int, h: Int, r: Int) {
        val heightMinus1 = h - 1
        val div = r + r + 1

        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var aSum = 0

            // 初始化窗口
            for (i in -r..r) {
                val pixel = input[(i.coerceIn(0, heightMinus1)) * w + x]
                rSum += (pixel shr 16) and 0xFF
                gSum += (pixel shr 8) and 0xFF
                bSum += pixel and 0xFF
                aSum += (pixel shr 24) and 0xFF
            }

            for (y in 0 until h) {
                output[y * w + x] = ((aSum / div) shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)

                // 滑动窗口
                val pixelOut = input[((y - r).coerceIn(0, heightMinus1)) * w + x]
                val pixelIn = input[((y + r + 1).coerceIn(0, heightMinus1)) * w + x]

                rSum += ((pixelIn shr 16) and 0xFF) - ((pixelOut shr 16) and 0xFF)
                gSum += ((pixelIn shr 8) and 0xFF) - ((pixelOut shr 8) and 0xFF)
                bSum += (pixelIn and 0xFF) - (pixelOut and 0xFF)
                aSum += ((pixelIn shr 24) and 0xFF) - ((pixelOut shr 24) and 0xFF)
            }
        }
    }
}
