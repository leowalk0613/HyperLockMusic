package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata

/**
 * 从 MediaMetadata / MediaData.artwork 解析尽可能高清的专辑图。
 * 避免仅使用通知卡片 ImageView 里的小缩略图。
 */
object AlbumArtResolver {

    private const val TAG = "MusicLockScreen_AlbumArt"

    @Volatile
    private var cachedBitmap: Bitmap? = null

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun updateCache(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        cachedBitmap = bitmap
        logI("cache updated: ${bitmap.width}x${bitmap.height}")
    }

    fun getCached(): Bitmap? {
        val b = cachedBitmap
        return if (b != null && !b.isRecycled) b else null
    }

    /**
     * bindMediaData 后调用，从 MediaData + Controller 元数据更新缓存。
     */
    fun refreshFromBind(
        context: Context,
        mediaData: Any?,
        metadata: MediaMetadata?
    ) {
        val candidates = mutableListOf<Bitmap>()
        if (metadata != null) {
            addMetadataBitmaps(metadata, candidates)
        }
        if (mediaData != null) {
            extractIconBitmap(context, mediaData)?.let { candidates.add(it) }
        }
        pickLargest(candidates)?.let { updateCache(it) }
    }

    /**
     * 设置壁纸时解析专辑图：缓存 > metadata > drawable
     */
    fun resolve(
        context: Context,
        drawable: Drawable?,
        metadata: MediaMetadata? = null
    ): Bitmap? {
        getCached()?.let { return it }

        val candidates = mutableListOf<Bitmap>()
        if (metadata != null) {
            addMetadataBitmaps(metadata, candidates)
        }
        extractFromDrawable(drawable)?.let { candidates.add(it) }

        val best = pickLargest(candidates)
        if (best != null) {
            updateCache(best)
            logI("resolved: ${best.width}x${best.height}")
        } else {
            logE("resolve failed: no bitmap source")
        }
        return best
    }

    fun readControllerMetadata(controller: Any?): MediaMetadata? {
        return try {
            val m = controller?.javaClass?.getMethod("getMetadata")
            m?.invoke(controller) as? MediaMetadata
        } catch (_: Throwable) {
            null
        }
    }

    private fun addMetadataBitmaps(metadata: MediaMetadata, out: MutableList<Bitmap>) {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )
        for (key in keys) {
            try {
                val bmp = metadata.getBitmap(key)
                if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                    out.add(bmp)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun extractIconBitmap(context: Context, mediaData: Any): Bitmap? {
        return try {
            val field = mediaData.javaClass.getDeclaredField("artwork")
            field.isAccessible = true
            val icon = field.get(mediaData) as? Icon ?: return null
            val d = icon.loadDrawable(context) ?: return null
            drawableToBitmap(d)
        } catch (e: Throwable) {
            logE("extractIconBitmap error", e)
            null
        }
    }

    private fun extractFromDrawable(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        return when (drawable) {
            is BitmapDrawable -> drawable.bitmap?.takeIf { !it.isRecycled }
            else -> drawableToBitmap(drawable)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            val w = when {
                drawable.intrinsicWidth > 0 -> drawable.intrinsicWidth
                else -> 512
            }
            val h = when {
                drawable.intrinsicHeight > 0 -> drawable.intrinsicHeight
                else -> 512
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp
        } catch (e: Throwable) {
            logE("drawableToBitmap error", e)
            null
        }
    }

    private fun pickLargest(bitmaps: List<Bitmap>): Bitmap? {
        return bitmaps
            .filter { !it.isRecycled && it.width > 0 && it.height > 0 }
            .maxByOrNull { it.width * it.height }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
