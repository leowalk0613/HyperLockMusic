package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata

/**
 * 校验网络高清图与当前封面是否为同一张（URL 同源 + 缩略图相似度）。
 */
object AlbumArtMatcher {

    private const val THUMB = 32
    /** 32×32 平均色差；同图不同分辨率/JPEG 质量通常 < 120，不同封面通常 > 400 */
    private const val MAX_MSE = 180.0

    private val NETEASE_PATH = Regex("""(?:https?://)?(?:p\d+\.)?music\.126\.net/([^?#]+)""")

    fun netEaseImageKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val match = NETEASE_PATH.find(url.trim()) ?: return null
        return match.groupValues[1].trimEnd('/')
    }

    fun isNetEaseArtUrl(url: String?): Boolean = netEaseImageKey(url) != null

    fun sameNetEaseImage(urlA: String?, urlB: String?): Boolean {
        val a = netEaseImageKey(urlA) ?: return false
        val b = netEaseImageKey(urlB) ?: return false
        return a == b
    }

    fun collectReferenceKeys(
        metadata: MediaMetadata?,
        mediaData: Any?,
        context: Context? = null
    ): Set<String> {
        val keys = mutableSetOf<String>()
        for (url in AlbumArtResolver.collectArtUrlStrings(metadata, mediaData, context)) {
            netEaseImageKey(url)?.let { keys.add(it) }
        }
        return keys
    }

    fun looksLikeSameCover(reference: Bitmap, candidate: Bitmap): Boolean {
        if (reference.isRecycled || candidate.isRecycled) return false
        val refThumb = downscaleSquare(reference, THUMB) ?: return false
        val candThumb = downscaleSquare(candidate, THUMB) ?: return false
        return meanSquaredError(refThumb, candThumb) <= MAX_MSE
    }

    /**
     * 网络图可用条件：分辨率更大，且 URL 同源或缩略图相似。
     */
    fun acceptsNetworkUpgrade(
        reference: Bitmap,
        candidate: Bitmap,
        sourceUrl: String?,
        referenceKeys: Set<String>,
        trackKey: String? = null
    ): Boolean {
        if (reference.isRecycled || candidate.isRecycled) return false
        if (candidate.width * candidate.height <= reference.width * reference.height) return false

        val sourceKey = netEaseImageKey(sourceUrl)
        if (sourceKey != null && referenceKeys.contains(sourceKey)) return true
        if (sourceKey != null && trackKey != null && trackKey.contains(sourceKey)) return true
        return looksLikeSameCover(reference, candidate)
    }

    private fun downscaleSquare(source: Bitmap, size: Int): Bitmap? {
        return try {
            val side = minOf(source.width, source.height)
            if (side <= 0) return null
            val x = (source.width - side) / 2
            val y = (source.height - side) / 2
            val cropped = Bitmap.createBitmap(source, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
            if (cropped != source && !cropped.isRecycled) cropped.recycle()
            scaled
        } catch (_: Throwable) {
            null
        }
    }

    private fun meanSquaredError(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return Double.MAX_VALUE
        var sum = 0.0
        val n = a.width * a.height
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                val dr = ((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)
                val dg = ((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)
                val db = (pa and 0xFF) - (pb and 0xFF)
                sum += dr * dr + dg * dg + db * db
            }
        }
        return sum / n
    }
}
