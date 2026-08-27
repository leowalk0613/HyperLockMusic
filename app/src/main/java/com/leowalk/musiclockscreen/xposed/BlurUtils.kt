package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * 模糊工具类
 *
 * 背景采用「缩小 → 轻量模糊 → 放大」得到柔和色块，避免大半径 StackBlur 的横竖条纹。
 */
object BlurUtils {

    private const val tag = "HyperLockMusic_Blur"

    private fun dpToPx(screenWidthPx: Int, dp: Float): Float {
        val density = screenWidthPx / 360f
        return dp * density
    }

    /**
     * 壁纸中专辑矩形（屏幕坐标，不含阴影），与 [blurWithBigAlbum] 绘制位置一致。
     */
    fun computeAlbumRect(
        screenWidth: Int,
        screenHeight: Int,
        albumSizePercent: Float,
        albumOffsetYDp: Float
    ): RectF {
        val albumSize = (screenWidth * albumSizePercent / 100f).toInt()
        val albumLeft = (screenWidth - albumSize) / 2f
        val albumTop = (screenHeight - albumSize) / 2f + dpToPx(screenWidth, albumOffsetYDp)
        return RectF(albumLeft, albumTop, albumLeft + albumSize, albumTop + albumSize)
    }

    /**
     * 取专辑图下半区域的主色调（缩小采样后求平均，跳过过亮/过暗像素）。
     */
    fun extractLowerHalfDominantColor(albumBitmap: Bitmap): Int {
        val w = albumBitmap.width
        val h = albumBitmap.height
        if (w <= 0 || h <= 0) return Color.BLACK

        val sampleW = 48
        val sampleH = 48
        val small = Bitmap.createScaledBitmap(albumBitmap, sampleW, sampleH, true)
        val startRow = sampleH / 2

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (y in startRow until sampleH) {
            for (x in 0 until sampleW) {
                val pixel = small.getPixel(x, y)
                val a = Color.alpha(pixel)
                if (a < 128) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                if (lum < 18 || lum > 235) continue
                rSum += r
                gSum += g
                bSum += b
                count++
            }
        }
        if (count == 0) {
            for (y in startRow until sampleH) {
                for (x in 0 until sampleW) {
                    val pixel = small.getPixel(x, y)
                    rSum += Color.red(pixel)
                    gSum += Color.green(pixel)
                    bSum += Color.blue(pixel)
                    count++
                }
            }
        }
        if (small !== albumBitmap) small.recycle()
        if (count == 0) return Color.BLACK
        return Color.rgb(
            (rSum / count).toInt().coerceIn(0, 255),
            (gSum / count).toInt().coerceIn(0, 255),
            (bSum / count).toInt().coerceIn(0, 255)
        )
    }

    /**
     * iOS 风格模糊 + 大专辑封面合成。
     *
     * @param blurSource 生成模糊背景（及暗色遮罩）的图，始终用系统封面
     * @param sharpAlbum 前景大专辑图；为 null 时与 [blurSource] 相同。网络高清只替换这一层
     */
    fun blurWithBigAlbum(
        blurSource: Bitmap,
        radius: Float,
        darkOverlayAlpha: Int = 140,
        showBigAlbum: Boolean = true,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        albumSizePercent: Float = 55f,
        albumOffsetYDp: Float = -80f,
        albumCornerDp: Float = 24f,
        sharpAlbum: Bitmap? = null
    ): Bitmap {
        val albumBitmap = sharpAlbum?.takeIf { !it.isRecycled } ?: blurSource
        val tw = if (targetWidth > 0) targetWidth else 1080
        val th = if (targetHeight > 0) targetHeight else 2400

        // 模糊底图：始终来自 blurSource（系统封面）
        val blurBaseW = tw.coerceAtMost(1440)
        val blurBaseH = (th.toFloat() * blurBaseW / tw).toInt().coerceAtLeast(1)
        val cover = scaleCenterCrop(blurSource, blurBaseW, blurBaseH)

        val blurred = softColorBlur(cover, radius)
        cover.recycle()

        val wallpaper = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wallpaper)

        val blW = blurred.width
        val blH = blurred.height
        val fillPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val dstRect = RectF(0f, 0f, tw.toFloat(), th.toFloat())
        val srcRect = android.graphics.Rect(0, 0, blW, blH)
        canvas.drawBitmap(blurred, srcRect, dstRect, fillPaint)

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
     * 沉浸专辑壁纸（与大专辑模糊算法分离）：
     * Monet 取色铺底 + 区域内 contain 完整封面，上下 alpha 溶入色底。
     * [radius] / [darkOverlayAlpha] 保留兼容；沉浸路径不做模糊、不压暗。
     */
    @Suppress("UNUSED_PARAMETER")
    fun blurWithImmersiveAlbum(
        blurSource: Bitmap,
        sharpAlbum: Bitmap,
        radius: Float,
        darkOverlayAlpha: Int = 140,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        albumAnchorYPercent: Float = 75f,
        topPercent: Float = 0f,
        /** 专辑竖直中心占屏高百分比，默认中间偏上 */
        albumCenterYPercent: Float = 38f,
    ): Bitmap {
        // 取色只用系统封面；前景可用高清 sharpAlbum
        val colorSource = blurSource.takeIf { !it.isRecycled } ?: sharpAlbum
        val albumBitmap = sharpAlbum.takeIf { !it.isRecycled } ?: colorSource
        val tw = if (targetWidth > 0) targetWidth else 1080
        val th = if (targetHeight > 0) targetHeight else 2400
        val w = tw.toFloat()
        val h = th.toFloat()

        val fillColor = MonetPalette.extractSeedColor(colorSource)

        val wallpaper = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wallpaper)
        canvas.drawColor(fillColor)

        val topY = th * topPercent / 100f
        val bottomY = th * albumAnchorYPercent.coerceIn(10f, 95f) / 100f
        val regionH = (bottomY - topY).coerceAtLeast(1f)

        val srcW = albumBitmap.width.toFloat().coerceAtLeast(1f)
        val srcH = albumBitmap.height.toFloat().coerceAtLeast(1f)
        val fitScale = min(w / srcW, regionH / srcH)
        val drawW = srcW * fitScale
        val drawH = srcH * fitScale
        val albumLeft = (w - drawW) * 0.5f
        val preferredCenterY = h * albumCenterYPercent.coerceIn(18f, 70f) / 100f
        val minTop = (topY + h * 0.04f).coerceAtLeast(h * 0.04f)
        val maxTop = (bottomY - drawH).coerceAtLeast(minTop)
        val albumTop = (preferredCenterY - drawH * 0.5f).coerceIn(minTop, maxTop)
        val albumDrawRect = RectF(albumLeft, albumTop, albumLeft + drawW, albumTop + drawH)

        val featherH = (drawH * 0.28f).coerceIn(72f, drawH * 0.42f)
        // 上沿渐变约为原先一半
        val topFeatherH = (drawH * 0.18f).coerceIn(55f, drawH * 0.26f)
        val fadePast = (drawH * 0.12f).coerceIn(36f, 96f)
        val layerTop = (albumTop - fadePast * 0.2f).coerceAtLeast(0f)
        val layerBottom = (albumDrawRect.bottom + fadePast).coerceAtMost(h)
        val layerRect = RectF(0f, layerTop, w, layerBottom)
        val fadeStartY = (albumDrawRect.bottom - featherH).coerceAtLeast(albumTop + drawH * 0.42f)
        val topFadeEndY = (albumTop + topFeatherH).coerceAtMost(fadeStartY - drawH * 0.12f)

        val albumPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val albumFitted = Bitmap.createScaledBitmap(
            albumBitmap,
            drawW.toInt().coerceAtLeast(1),
            drawH.toInt().coerceAtLeast(1),
            true
        )

        val layer = canvas.saveLayer(layerRect, null)
        canvas.drawBitmap(albumFitted, null, albumDrawRect, albumPaint)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        maskPaint.shader = buildMonetFeatherMask(
            topY = layerTop,
            bottomY = layerBottom,
            albumTop = albumTop,
            topFadeEndY = topFadeEndY,
            fadeStartY = fadeStartY,
        )
        canvas.drawRect(layerRect, maskPaint)
        maskPaint.xfermode = null
        canvas.restoreToCount(layer)

        if (albumFitted !== albumBitmap) albumFitted.recycle()
        return wallpaper
    }

    /** 沉浸封面上下沿溶入 Monet 色底的 alpha 蒙版。 */
    private fun buildMonetFeatherMask(
        topY: Float,
        bottomY: Float,
        albumTop: Float,
        topFadeEndY: Float,
        fadeStartY: Float,
    ): Shader {
        val height = (bottomY - topY).coerceAtLeast(1f)
        val stops = 33
        val colors = IntArray(stops)
        val positions = FloatArray(stops)
        for (i in 0 until stops) {
            val t = i / (stops - 1f)
            positions[i] = t
            val y = topY + t * height
            val alpha = when {
                y < albumTop -> 0f
                y < topFadeEndY && topFadeEndY > albumTop + 0.5f -> {
                    val u = ((y - albumTop) / (topFadeEndY - albumTop)).coerceIn(0f, 1f)
                    // 上沿更长、更柔：smoothstep 后再缓一点
                    val s = u * u * (3f - 2f * u)
                    s * s * (0.35f + 0.65f * s)
                }
                y <= fadeStartY -> 1f
                else -> {
                    val u = ((y - fadeStartY) / (bottomY - fadeStartY)).coerceIn(0f, 1f)
                    val s = u * u * (3f - 2f * u)
                    (1f - s) * (1f - s)
                }
            }
            colors[i] = Color.argb((alpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        return LinearGradient(0f, topY, 0f, bottomY, colors, positions, Shader.TileMode.CLAMP)
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
