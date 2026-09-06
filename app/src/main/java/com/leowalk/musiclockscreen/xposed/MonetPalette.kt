package com.leowalk.musiclockscreen.xposed

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi

/**
 * 沉浸铺底种子色：优先系统 [WallpaperColors.fromBitmap]，失败再 Celebi 量化。
 * 种子原样使用（不做 HCT 改色）。
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
     * 从专辑取色。优先系统 WallpaperColors；否则 Celebi 簇中心（占比×彩度加权）。
     */
    fun extractSeedColor(album: Bitmap): Int {
        try {
            if (album.isRecycled || album.width <= 0 || album.height <= 0) {
                return FALLBACK_SEED
            }
        } catch (_: Throwable) {
            return FALLBACK_SEED
        }
        seedFromWallpaperColors(album)?.let { return it }
        return seedFromCelebi(album)
    }

    /** 系统取色；JVM 单测 / 失败时返回 null。 */
    fun seedFromWallpaperColors(album: Bitmap): Int? {
        return try {
            val colors = WallpaperColors.fromBitmap(album)
            pickMostChromatic(
                colors.primaryColor.toArgb(),
                colors.secondaryColor?.toArgb(),
                colors.tertiaryColor?.toArgb(),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** Celebi 回退路径（单测可直接覆盖）。 */
    fun seedFromCelebi(album: Bitmap): Int {
        if (album.width <= 0 || album.height <= 0 || album.isRecycled) {
            return FALLBACK_SEED
        }
        val sample = Bitmap.createScaledBitmap(album, SAMPLE_SIZE, SAMPLE_SIZE, true)
        return try {
            val pixels = IntArray(sample.width * sample.height)
            sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
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

    /** 在候选色里选彩度最高的，避免 WallpaperColors 主色偏灰。 */
    fun pickMostChromatic(vararg candidates: Int?): Int {
        var best = FALLBACK_SEED
        var bestScore = -1.0
        for (c in candidates) {
            if (c == null) continue
            val chroma = try {
                Hct.fromInt(c).chroma
            } catch (_: Throwable) {
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                (maxOf(r, g, b) - minOf(r, g, b)).toDouble()
            }
            if (chroma > bestScore) {
                bestScore = chroma
                best = c
            }
        }
        return best
    }

    private const val SAMPLE_SIZE = 128
    private const val MAX_COLORS = 32
    private const val FALLBACK_SEED = 0xFF6750A4.toInt()
}
