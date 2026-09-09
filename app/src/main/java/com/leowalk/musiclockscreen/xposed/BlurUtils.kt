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
import kotlin.math.roundToInt

/**
 * 模糊工具类
 *
 * 背景 softColor：缩小 → 放大色块；重糊走系统合成器。
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
     * Lower-half tint: contrast (mean luminance) + accent (chroma-weighted, normalized).
     * extractLowerHalfDominantColor returns accent; use LowerHalfTint.contrast for light/dark.
     */
    data class LowerHalfTint(val contrast: Int, val accent: Int)

    fun extractLowerHalfTintColors(albumBitmap: Bitmap): LowerHalfTint {
        val w = albumBitmap.width
        val h = albumBitmap.height
        if (w <= 0 || h <= 0) {
            return LowerHalfTint(
                Color.rgb(40, 40, 44),
                AlbumTintExtractPolicy.normalizeAccentTint(Color.GRAY),
            )
        }

        val sampleW = 48
        val sampleH = 48
        val small = Bitmap.createScaledBitmap(albumBitmap, sampleW, sampleH, true)
        val startRow = sampleH / 2

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        var accentR = 0.0
        var accentG = 0.0
        var accentB = 0.0
        var accentW = 0.0

        for (y in startRow until sampleH) {
            for (x in 0 until sampleW) {
                val pixel = small.getPixel(x, y)
                val a = Color.alpha(pixel)
                if (a < 128) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                rSum += r
                gSum += g
                bSum += b
                count++
                val wChroma = AlbumTintExtractPolicy.chromaWeight(r, g, b)
                if (wChroma > 0f) {
                    accentR += r * wChroma
                    accentG += g * wChroma
                    accentB += b * wChroma
                    accentW += wChroma
                }
            }
        }
        if (small !== albumBitmap) small.recycle()
        if (count == 0) {
            return LowerHalfTint(
                Color.rgb(40, 40, 44),
                AlbumTintExtractPolicy.normalizeAccentTint(Color.GRAY),
            )
        }
        val contrast = Color.rgb(
            (rSum / count).toInt().coerceIn(0, 255),
            (gSum / count).toInt().coerceIn(0, 255),
            (bSum / count).toInt().coerceIn(0, 255),
        )
        val rawAccent = if (accentW > 1e-3) {
            Color.rgb(
                (accentR / accentW).toInt().coerceIn(0, 255),
                (accentG / accentW).toInt().coerceIn(0, 255),
                (accentB / accentW).toInt().coerceIn(0, 255),
            )
        } else {
            contrast
        }
        return LowerHalfTint(contrast, AlbumTintExtractPolicy.normalizeAccentTint(rawAccent))
    }

    /** Accent from lower half. For light/dark use extractLowerHalfTintColors().contrast. */
    fun extractLowerHalfDominantColor(albumBitmap: Bitmap): Int =
        extractLowerHalfTintColors(albumBitmap).accent
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

        // softColor 用大采样铺柔化底；锁屏 MiBlur 遮罩再叠一层。勿缩到几十像素（会呈小方格）。
        val blurred = softColorBlur(cover, radius)
        if (blurred !== cover) cover.recycle()


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
     * [radius] 保留兼容；沉浸路径不做模糊。上下沿暗色渐变强度取自 [darkOverlayAlpha]。
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
        edgeGradientEnabled: Boolean = true,
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

        if (edgeGradientEnabled) {
            drawImmersiveWallpaperEdgeGradients(
                canvas = canvas,
                width = w,
                height = h,
                albumTop = albumTop,
                albumBottom = albumDrawRect.bottom,
                darkOverlayAlpha = darkOverlayAlpha,
            )
        }

        if (albumFitted !== albumBitmap) albumFitted.recycle()
        return wallpaper
    }

    private fun drawImmersiveWallpaperEdgeGradients(
        canvas: Canvas,
        width: Float,
        height: Float,
        albumTop: Float,
        albumBottom: Float,
        darkOverlayAlpha: Int,
    ) {
        val spans = computeImmersiveEdgeGradientSpans(height, albumTop, albumBottom)
        val peakAlpha = computeImmersiveEdgeGradientPeakAlpha(darkOverlayAlpha)
        val midAlpha = (peakAlpha * 0.35f).toInt().coerceIn(0, 255)

        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, spans.topSpanPx,
                intArrayOf(
                    Color.argb(peakAlpha, 0, 0, 0),
                    Color.argb(midAlpha, 0, 0, 0),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width, spans.topSpanPx, topPaint)

        val bottomStart = height - spans.bottomSpanPx
        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, bottomStart, 0f, height,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(midAlpha, 0, 0, 0),
                    Color.argb(peakAlpha, 0, 0, 0),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, bottomStart, width, height, bottomPaint)
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
     * 壁纸 softColor：金字塔降采样 → 小图 box blur → 金字塔放大。
     * 比「一次缩到极大再拉回」糊得多，又比极小单次缩放更少马赛克感。
     */
    fun softColorBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)

        val r = radius.coerceIn(1f, 150f)
        val (tw, th) = softColorDownsampleSize(w, h, r)

        var work = pyramidDown(bitmap, tw, th)
        val boxR = (r / 18f).toInt().coerceIn(2, 6)
        val passes = if (r >= 40f) 3 else 2
        repeat(passes) {
            val next = boxBlur(work, boxR)
            if (next !== work) work.recycle()
            work = next
        }
        val result = pyramidUp(work, w, h)
        if (result !== work) work.recycle()
        return result
    }

    /**
     * softColor 目标尺寸：最长边约 40–90（明显发糊），保持纵横比。
     */
    internal fun softColorDownsampleSize(srcW: Int, srcH: Int, radius: Float): Pair<Int, Int> {
        val r = radius.coerceIn(1f, 150f)
        val targetMaxSide = (100f - r * 0.7f).coerceIn(40f, 90f).toInt()
        return if (srcW >= srcH) {
            val sw = targetMaxSide
            val sh = max(1, (targetMaxSide.toFloat() * srcH / srcW).roundToInt())
            sw to sh
        } else {
            val sh = targetMaxSide
            val sw = max(1, (targetMaxSide.toFloat() * srcW / srcH).roundToInt())
            sw to sh
        }
    }

    /** 逐级减半再落到目标尺寸，减轻一次缩太狠的色块感。 */
    private fun pyramidDown(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        var work = src
        var owned = false
        while (work.width > dstW * 2 || work.height > dstH * 2) {
            val nw = max(dstW, work.width / 2)
            val nh = max(dstH, work.height / 2)
            val next = Bitmap.createScaledBitmap(work, nw, nh, true)
            if (owned) work.recycle()
            work = next
            owned = true
        }
        if (work.width != dstW || work.height != dstH) {
            val next = Bitmap.createScaledBitmap(work, dstW, dstH, true)
            if (owned) work.recycle()
            work = next
            owned = true
        }
        return if (owned) work else Bitmap.createScaledBitmap(src, dstW, dstH, true)
    }

    /** 逐级放大回全尺寸。 */
    private fun pyramidUp(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        var work = src
        var owned = false
        while (work.width * 2 < dstW || work.height * 2 < dstH) {
            val nw = min(dstW, max(1, work.width * 2))
            val nh = min(dstH, max(1, work.height * 2))
            if (nw == work.width && nh == work.height) break
            val next = Bitmap.createScaledBitmap(work, nw, nh, true)
            if (owned) work.recycle()
            work = next
            owned = true
        }
        if (work.width != dstW || work.height != dstH) {
            val next = Bitmap.createScaledBitmap(work, dstW, dstH, true)
            if (owned) work.recycle()
            return next
        }
        return if (owned) work else src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
    }

    /** 小图可分离 box blur（横+竖各一遍）。 */
    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 8)
        val w = src.width
        val h = src.height
        if (w <= 1 || h <= 1) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        val div = r * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var a = 0
                var red = 0
                var green = 0
                var blue = 0
                for (dx in -r..r) {
                    val p = pixels[row + (x + dx).coerceIn(0, w - 1)]
                    a += p ushr 24
                    red += (p shr 16) and 0xFF
                    green += (p shr 8) and 0xFF
                    blue += p and 0xFF
                }
                tmp[row + x] =
                    ((a / div) shl 24) or ((red / div) shl 16) or ((green / div) shl 8) or (blue / div)
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0
                var red = 0
                var green = 0
                var blue = 0
                for (dy in -r..r) {
                    val p = tmp[(y + dy).coerceIn(0, h - 1) * w + x]
                    a += p ushr 24
                    red += (p shr 16) and 0xFF
                    green += (p shr 8) and 0xFF
                    blue += p and 0xFF
                }
                pixels[y * w + x] =
                    ((a / div) shl 24) or ((red / div) shl 16) or ((green / div) shl 8) or (blue / div)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
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
}
