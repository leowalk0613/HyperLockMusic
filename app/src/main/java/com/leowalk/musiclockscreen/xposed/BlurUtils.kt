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

    private const val tag = "MusicLockScreen_Blur"

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

    /** 加深并提高饱和度，用于沉浸封面底部取色延伸。 */
    private fun deepenDominantColor(color: Int, satScale: Float = 1.45f, lumScale: Float = 0.52f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * satScale).coerceIn(0.35f, 1f)
        hsv[2] = (hsv[2] * lumScale).coerceIn(0.08f, 0.55f)
        return Color.HSVToColor(hsv)
    }

    private fun blendRgb(c1: Int, c2: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        val inv = 1f - u
        return Color.rgb(
            (Color.red(c1) * inv + Color.red(c2) * u).toInt().coerceIn(0, 255),
            (Color.green(c1) * inv + Color.green(c2) * u).toInt().coerceIn(0, 255),
            (Color.blue(c1) * inv + Color.blue(c2) * u).toInt().coerceIn(0, 255)
        )
    }

    private fun applyDarkOverlay(color: Int, overlayAlpha: Int): Int {
        val a = overlayAlpha.coerceIn(0, 255) / 255f
        return Color.rgb(
            (Color.red(color) * (1f - a)).toInt().coerceIn(0, 255),
            (Color.green(color) * (1f - a)).toInt().coerceIn(0, 255),
            (Color.blue(color) * (1f - a)).toInt().coerceIn(0, 255)
        )
    }

    /** 按屏幕 Y 在模糊底图上采样一行平均色（与壁纸合成坐标对齐）。 */
    private fun sampleBlurRowColor(
        blurred: Bitmap, canvasW: Int, canvasH: Int, yCanvasPx: Int, overlayAlpha: Int
    ): Int {
        val bh = blurred.height
        val bw = blurred.width
        if (bw <= 0 || bh <= 0) return Color.BLACK
        val y = (yCanvasPx.toFloat() / canvasH * bh).toInt().coerceIn(0, bh - 1)
        val y0 = (y - 2).coerceAtLeast(0)
        val y1 = (y + 2).coerceAtMost(bh - 1)
        val step = (bw / 36).coerceAtLeast(1)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (yy in y0..y1) {
            var x = 0
            while (x < bw) {
                val p = blurred.getPixel(x, yy)
                r += Color.red(p)
                g += Color.green(p)
                b += Color.blue(p)
                n++
                x += step
            }
        }
        if (n == 0) return Color.BLACK
        val raw = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        return applyDarkOverlay(raw, overlayAlpha)
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
     * 模糊背景 + 沉浸专辑合成进壁纸：封面上下缘 alpha 羽化，露出同帧模糊底图。
     */
    fun blurWithImmersiveAlbum(
        blurSource: Bitmap,
        sharpAlbum: Bitmap,
        radius: Float,
        darkOverlayAlpha: Int = 140,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        albumAnchorYPercent: Float = 55f,
        topPercent: Float = 0f,
    ): Bitmap {
        val albumBitmap = sharpAlbum.takeIf { !it.isRecycled } ?: blurSource
        val tw = if (targetWidth > 0) targetWidth else 1080
        val th = if (targetHeight > 0) targetHeight else 2400

        // 沉浸模式模糊底与清晰封面同源，羽化露出的是同一套色调
        val blurBaseW = tw.coerceAtMost(1440)
        val blurBaseH = (th.toFloat() * blurBaseW / tw).toInt().coerceAtLeast(1)
        val cover = scaleCenterCrop(albumBitmap, blurBaseW, blurBaseH)
        val blurred = softColorBlur(cover, radius)
        cover.recycle()

        val wallpaper = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(wallpaper)

        val fillPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(
            blurred,
            android.graphics.Rect(0, 0, blurred.width, blurred.height),
            RectF(0f, 0f, tw.toFloat(), th.toFloat()),
            fillPaint
        )

        val overlayPaint = Paint().apply {
            color = Color.argb(darkOverlayAlpha, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, tw.toFloat(), th.toFloat(), overlayPaint)

        val topY = th * topPercent / 100f
        val bottomY = th * albumAnchorYPercent.coerceIn(10f, 95f) / 100f
        val regionH = (bottomY - topY).coerceAtLeast(1f)
        val w = tw.toFloat()
        val bottomFeatherH = (regionH * 0.52f).coerceIn(160f, regionH * 0.68f)
        val extensionH = th * 0.14f
        val blendStart = (bottomY - bottomFeatherH * 1.2f).coerceAtLeast(topY)
        val blendEnd = (bottomY + extensionH).coerceAtMost(th.toFloat())

        val blurTop = sampleBlurRowColor(blurred, tw, th, blendStart.toInt(), darkOverlayAlpha)
        val blurMid = sampleBlurRowColor(blurred, tw, th, bottomY.toInt(), darkOverlayAlpha)
        val blurBottom = sampleBlurRowColor(blurred, tw, th, blendEnd.toInt(), darkOverlayAlpha)
        val deepColor = deepenDominantColor(extractLowerHalfDominantColor(albumBitmap))
        val peakColor = blendRgb(deepColor, blurMid, 0.18f)
        val entryColor = blendRgb(deepColor, blurTop, 0.58f)
        val exitColor = blendRgb(deepColor, blurBottom, 0.68f)

        fun tintAlpha(color: Int, alpha: Int) = Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

        // 延伸渐变带：两端与模糊底同色，中间浓，消除锚点硬切
        val washPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        washPaint.shader = LinearGradient(
            0f, blendStart, 0f, blendEnd,
            intArrayOf(
                tintAlpha(entryColor, 0),
                tintAlpha(entryColor, 38),
                tintAlpha(peakColor, 155),
                tintAlpha(peakColor, 200),
                tintAlpha(exitColor, 95),
                tintAlpha(exitColor, 0)
            ),
            floatArrayOf(0f, 0.22f, 0.48f, 0.62f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, blendStart, w, blendEnd, washPaint)

        val albumRect = RectF(0f, topY, w, bottomY)

        // 封面略向上下 bleed，底部多段 alpha 羽化
        val bleedPx = (regionH * 0.04f).coerceAtLeast(16f)
        val albumDrawH = (regionH + bleedPx).toInt().coerceAtLeast(1)
        val albumScaled = scaleCenterCrop(albumBitmap, tw, albumDrawH)
        val albumDrawTop = topY - bleedPx * 0.5f
        val albumDrawRect = RectF(0f, albumDrawTop, w, albumDrawTop + albumDrawH)

        val albumPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val layer = canvas.saveLayer(albumRect, null)
        canvas.drawBitmap(albumScaled, null, albumDrawRect, albumPaint)

        val fadeStartY = bottomY - bottomFeatherH
        val fadeStartNorm = ((fadeStartY - topY) / regionH).coerceIn(0.20f, 0.45f)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        maskPaint.shader = LinearGradient(
            0f, topY, 0f, bottomY,
            intArrayOf(
                Color.WHITE,
                Color.WHITE,
                Color.argb(235, 255, 255, 255),
                Color.argb(170, 255, 255, 255),
                Color.argb(85, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(
                0f,
                fadeStartNorm,
                fadeStartNorm + 0.10f,
                fadeStartNorm + 0.24f,
                fadeStartNorm + 0.42f,
                1f
            ),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(albumRect, maskPaint)
        maskPaint.xfermode = null
        canvas.restoreToCount(layer)

        blurred.recycle()
        if (albumScaled !== albumBitmap) albumScaled.recycle()
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
