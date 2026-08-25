package com.leowalk.musiclockscreen.xposed

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 MediaMetadata（含 URI/HTTP）/ MediaData / 通知 extras 解析尽可能高清的专辑图。
 * 同一首歌内取最大分辨率；切歌或重新进入音乐锁屏时强制用当前曲目新图。
 */
object AlbumArtResolver {

    private const val TAG = "MusicLockScreen_AlbumArt"

    @Volatile
    private var cachedBitmap: Bitmap? = null

    @Volatile
    private var cachedTrackKey: String? = null

    @Volatile
    private var lastBindMetadata: MediaMetadata? = null

    @Volatile
    private var lastBindMediaData: Any? = null

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun getBindMetadata(): MediaMetadata? = lastBindMetadata

    fun getBindMediaData(): Any? = lastBindMediaData

    fun getCachedTrackKey(): String? = cachedTrackKey

    fun updateCache(bitmap: Bitmap?, trackKey: String? = null) {
        if (bitmap == null || bitmap.isRecycled) return
        val key = trackKey ?: cachedTrackKey
        val current = cachedBitmap
        if (key != null && key == cachedTrackKey &&
            current != null && !current.isRecycled &&
            current.width * current.height >= bitmap.width * bitmap.height
        ) {
            return
        }
        cachedBitmap = bitmap
        if (key != null) {
            cachedTrackKey = key
        }
        logI("cache updated: ${bitmap.width}x${bitmap.height}, key=$cachedTrackKey")
    }

    fun getCached(): Bitmap? {
        val b = cachedBitmap
        return if (b != null && !b.isRecycled) b else null
    }

    fun refreshFromBind(
        context: Context,
        mediaData: Any?,
        metadata: MediaMetadata?
    ) {
        if (metadata != null) {
            lastBindMetadata = metadata
        }
        if (mediaData != null) {
            lastBindMediaData = mediaData
        }
        val meta = metadata ?: lastBindMetadata
        val data = mediaData ?: lastBindMediaData
        val trackKey = computeTrackKey(meta, data)
        val trackChanged = trackKey != null && trackKey != cachedTrackKey
        if (trackChanged) {
            logI("track changed on bind: $cachedTrackKey -> $trackKey")
        }
        val best = collectBest(
            context = context,
            drawable = null,
            metadata = meta,
            mediaData = data,
            includeCache = !trackChanged
        )
        if (best != null) {
            updateCache(best, trackKey)
        } else if (trackChanged && trackKey != null) {
            cachedTrackKey = trackKey
            cachedBitmap = null
        }
    }

    /**
     * 解析专辑图。重新进入音乐锁屏或切歌时应设 [ignoreCache]=true。
     */
    fun resolve(
        context: Context,
        drawable: Drawable?,
        metadata: MediaMetadata? = null,
        ignoreCache: Boolean = false,
        mediaData: Any? = null
    ): Bitmap? {
        val meta = metadata ?: lastBindMetadata
        val data = mediaData ?: lastBindMediaData
        val trackKey = computeTrackKey(meta, data)
        val trackChanged = trackKey != null && trackKey != cachedTrackKey
        val includeCache = !ignoreCache && !trackChanged

        val best = collectBest(
            context = context,
            drawable = drawable,
            metadata = meta,
            mediaData = data,
            includeCache = includeCache
        )
        if (best != null) {
            updateCache(best, trackKey)
            logI("resolved: ${best.width}x${best.height}, ignoreCache=$ignoreCache")
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

    private fun collectBest(
        context: Context,
        drawable: Drawable?,
        metadata: MediaMetadata?,
        mediaData: Any?,
        includeCache: Boolean
    ): Bitmap? {
        val candidates = mutableListOf<Bitmap>()
        if (includeCache) {
            getCached()?.let { candidates.add(it) }
        }
        if (metadata != null) {
            addMetadataSources(context, metadata, candidates)
        }
        if (mediaData != null) {
            addMediaDataSources(context, mediaData, candidates)
        }
        extractFromDrawable(drawable)?.let { candidates.add(it) }

        val best = pickLargest(candidates)
        if (candidates.isNotEmpty()) {
            val sizes = candidates.joinToString { "${it.width}x${it.height}" }
            logI("candidates=[$sizes] -> best=${best?.width}x${best?.height}")
        }
        return best
    }

    private fun addMediaDataSources(context: Context, mediaData: Any, out: MutableList<Bitmap>) {
        extractIconBitmap(context, mediaData)?.let { out.add(it) }
        for (url in extractRemoteArtUrls(mediaData)) {
            loadBitmapFromUri(context, url)?.let { out.add(it) }
        }
    }

    private fun extractRemoteArtUrls(mediaData: Any): List<String> {
        val urls = mutableListOf<String>()
        extractArtUriFromMediaData(mediaData)?.let { urls.add(it) }
        extractMiuiFocusPicUrl(mediaData)?.let { urls.add(it) }
        return urls.distinct()
    }

    private fun extractMiuiFocusPicUrl(mediaData: Any): String? {
        val notification = readMediaDataField(mediaData, "notification") as? Notification
        val json = notification?.extras?.getString("miui.focus.param.media") ?: return null
        return parseMiuiFocusPicJson(json)
    }

    private fun parseMiuiFocusPicJson(json: String): String? {
        return try {
            JSONObject(json)
                .optJSONObject("param_v2")
                ?.optJSONObject("param_island")
                ?.optJSONObject("shareData")
                ?.optString("pic")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            Regex(""""pic"\s*:\s*"((?:\\.|[^"\\])*)"""")
                .find(json)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\/", "/")
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun computeTrackKey(metadata: MediaMetadata?, mediaData: Any?): String? {
        if (metadata != null) {
            val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            if (!mediaId.isNullOrBlank()) return "id:$mediaId"

            val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            if (!artUri.isNullOrBlank()) return "uri:$artUri"

            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
            if (title.isNotEmpty() || artist.isNotEmpty()) {
                return "t:$title|a:$artist|al:$album"
            }
        }

        if (mediaData != null) {
            val pkg = packageFromMediaData(mediaData).orEmpty()
            val picUrl = extractMiuiFocusPicUrl(mediaData)
            if (!picUrl.isNullOrBlank()) return "pkg:$pkg|pic:$picUrl"

            val uri = extractArtUriFromMediaData(mediaData)
            if (!uri.isNullOrBlank()) return "pkg:$pkg|uri:$uri"

            val song = readMediaDataString(mediaData, "song")
                ?: readMediaDataString(mediaData, "title")
            val artist = readMediaDataString(mediaData, "artist")
            if (song != null || artist != null) {
                return "pkg:$pkg|t:${song.orEmpty()}|a:${artist.orEmpty()}"
            }
        }
        return null
    }

    private fun packageFromMediaData(mediaData: Any): String? {
        return readMediaDataString(mediaData, "packageName")
    }

    private fun readMediaDataString(mediaData: Any, name: String): String? {
        return try {
            val field = mediaData.javaClass.getDeclaredField(name).apply { isAccessible = true }
            (field.get(mediaData) as? String)?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readMediaDataField(mediaData: Any, name: String): Any? {
        return try {
            val field = mediaData.javaClass.getDeclaredField(name).apply { isAccessible = true }
            field.get(mediaData)
        } catch (_: Throwable) {
            null
        }
    }

    private fun addMetadataSources(
        context: Context,
        metadata: MediaMetadata,
        out: MutableList<Bitmap>
    ) {
        addMetadataBitmaps(metadata, out)
        for (uriKey in METADATA_URI_KEYS) {
            loadBitmapFromUri(context, metadata.getString(uriKey))?.let { out.add(it) }
        }
    }

    private fun addMetadataBitmaps(metadata: MediaMetadata, out: MutableList<Bitmap>) {
        for (key in METADATA_BITMAP_KEYS) {
            try {
                val bmp = metadata.getBitmap(key)
                if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                    out.add(bmp)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            when (Uri.parse(uriString).scheme?.lowercase()) {
                "http", "https" -> loadBitmapFromUrl(uriString)
                else -> context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    decodeBitmapStream(stream)
                }
            }?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        } catch (e: Throwable) {
            logE("loadBitmapFromUri failed: $uriString", e)
            null
        }
    }

    private fun loadBitmapFromUrl(urlString: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { stream -> decodeBitmapStream(stream) }
        } catch (e: Throwable) {
            logE("loadBitmapFromUrl failed: $urlString", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun decodeBitmapStream(stream: java.io.InputStream): Bitmap? {
        val options = BitmapFactory.Options().apply { inScaled = false }
        return BitmapFactory.decodeStream(stream, null, options)
    }

    private fun extractArtUriFromMediaData(mediaData: Any): String? {
        for (name in MEDIA_DATA_URI_FIELDS) {
            try {
                val field = mediaData.javaClass.getDeclaredField(name).apply { isAccessible = true }
                when (val value = field.get(mediaData)) {
                    is String -> if (value.isNotBlank()) return value
                    is Uri -> return value.toString()
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun extractIconBitmap(context: Context, mediaData: Any): Bitmap? {
        return try {
            val field = mediaData.javaClass.getDeclaredField("artwork")
            field.isAccessible = true
            val icon = field.get(mediaData) as? Icon ?: return null
            val d = icon.loadDrawable(context) ?: return null
            drawableToBitmap(d, preferDrawableIntrinsic = false)
        } catch (e: Throwable) {
            logE("extractIconBitmap error", e)
            null
        }
    }

    private fun extractFromDrawable(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        return when (drawable) {
            is BitmapDrawable -> {
                val bmp = drawable.bitmap
                if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                    bmp
                } else {
                    drawableToBitmap(drawable, preferDrawableIntrinsic = true)
                }
            }
            else -> drawableToBitmap(drawable, preferDrawableIntrinsic = true)
        }
    }

    private fun drawableToBitmap(drawable: Drawable, preferDrawableIntrinsic: Boolean): Bitmap? {
        return try {
            val intrinsicW = drawable.intrinsicWidth
            val intrinsicH = drawable.intrinsicHeight
            val w = when {
                drawable is BitmapDrawable &&
                    drawable.bitmap != null &&
                    !preferDrawableIntrinsic -> drawable.bitmap.width
                intrinsicW > 0 -> intrinsicW
                else -> 512
            }
            val h = when {
                drawable is BitmapDrawable &&
                    drawable.bitmap != null &&
                    !preferDrawableIntrinsic -> drawable.bitmap.height
                intrinsicH > 0 -> intrinsicH
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

    private val METADATA_BITMAP_KEYS = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON
    )

    private val METADATA_URI_KEYS = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    )

    private val MEDIA_DATA_URI_FIELDS = listOf(
        "artworkUri",
        "artUri",
        "albumArtUri"
    )

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
