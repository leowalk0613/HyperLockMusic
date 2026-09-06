package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import com.materialkolor.quantize.QuantizerCelebi

/**
 * 沉浸铺底种子色：Celebi 量化后取**像素占比最高**的簇中心，原样铺底（不做 HCT 改色）。
 */
object MonetPalette {

    data class WallpaperTones(
        val background: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val primaryContainer: Int,
        val secondaryContainer: Int,
    )

    fun extractWallpaper(album: Bitmap): WallpaperTones {
        val seed = extractSeedColor(album)
        return WallpaperTones(seed, seed, seed, seed, seed)
    }

    /** @deprecated 用 [extractWallpaper] */
    fun extractDarkWallpaper(album: Bitmap): WallpaperTones = extractWallpaper(album)

    /** 从专辑取色：出现次数最多的量化簇中心。 */
    fun extractSeedColor(album: Bitmap): Int {
        try {
            if (album.isRecycled || album.width <= 0 || album.height <= 0) {
                return FALLBACK_SEED
            }
        } catch (_: Throwable) {
            return FALLBACK_SEED
        }
        return seedFromCelebi(album)
    }

    fun seedFromCelebi(album: Bitmap): Int {
        if (album.width <= 0 || album.height <= 0 || album.isRecycled) {
            return FALLBACK_SEED
        }
        val sample = Bitmap.createScaledBitmap(album, SAMPLE_SIZE, SAMPLE_SIZE, true)
        return try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            val quantized = QuantizerCelebi.quantize(pixels, MAX_COLORS)
            pickMostPopulous(quantized)
        } catch (_: Throwable) {
            FALLBACK_SEED
        } finally {
            if (sample !== album) sample.recycle()
        }
    }

    /** 占比最高的簇；并列时取先遇到的。 */
    fun pickMostPopulous(quantized: Map<Int, Int>): Int {
        if (quantized.isEmpty()) return FALLBACK_SEED
        return quantized.maxByOrNull { it.value }?.key ?: FALLBACK_SEED
    }

    private const val SAMPLE_SIZE = 128
    private const val MAX_COLORS = 32
    private const val FALLBACK_SEED = 0xFF6750A4.toInt()
}
