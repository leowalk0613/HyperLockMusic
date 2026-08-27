package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi

/**
 * 沉浸铺底：系统封面 Celebi 量化后选种子色，**原样铺底**（不做 HCT 改色）。
 * 加权 = 占比 × 彩度，避免大面积灰底盖过真正的主色；全灰封面仍会落到灰。
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

    /**
     * 从专辑取色。返回量化簇中心原色，不改 hue/tone。
     * 用 population×chroma 加权，减少「灰底当主色」的情况。
     */
    fun extractSeedColor(album: Bitmap): Int {
        if (album.width <= 0 || album.height <= 0 || album.isRecycled) {
            return FALLBACK_SEED
        }
        val sample = Bitmap.createScaledBitmap(album, SAMPLE_SIZE, SAMPLE_SIZE, true)
        return try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
            // 色数少一点，簇中心更干净，少混成脏灰
            val quantized = QuantizerCelebi.quantize(pixels, MAX_COLORS)
            if (quantized.isEmpty()) return FALLBACK_SEED

            quantized.maxByOrNull { (color, count) ->
                val chroma = Hct.fromInt(color).chroma
                val boost = 1.0 + (chroma / 18.0).coerceIn(0.0, 5.0)
                count.toDouble() * boost * boost
            }?.key ?: FALLBACK_SEED
        } catch (_: Throwable) {
            FALLBACK_SEED
        } finally {
            if (sample !== album) sample.recycle()
        }
    }

    private const val SAMPLE_SIZE = 128
    private const val MAX_COLORS = 32
    private const val FALLBACK_SEED = 0xFF6750A4.toInt()
}
