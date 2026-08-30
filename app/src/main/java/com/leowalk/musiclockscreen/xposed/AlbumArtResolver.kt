package com.leowalk.musiclockscreen.xposed

import android.app.Notification
import android.app.NotificationManager
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

    private const val TAG = "HyperLockMusic_AlbumArt"

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

    /** 当前曲目封面 URL；切歌时过滤与 canonical songId 不一致的通知源 */
    fun collectArtUrlStrings(
        metadata: MediaMetadata?,
        mediaData: Any?,
        context: Context? = null
    ): List<String> {
        val songId = NetEaseSongIdResolver.resolveCanonicalSongId(context, metadata, mediaData)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val urls = mutableListOf<String>()
        if (metadata != null) {
            for (uriKey in METADATA_URI_KEYS) {
                metadata.getString(uriKey)?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
            }
        }
        if (mediaData != null) {
            urls.addAll(extractRemoteArtUrls(mediaData, songId, title))
        }
        if (context != null) {
            val pkg = packageFromMediaData(mediaData)
                ?: HookUtils.currentMediaPackage(context)
            if (!pkg.isNullOrBlank()) {
                urls.addAll(scanNotificationArtUrls(context, pkg, songId, title))
            }
        }
        return urls.distinct()
    }

    private fun scanNotificationArtUrls(
        context: Context,
        packageName: String,
        songId: Long?,
        title: String?
    ): List<String> {
        val urls = mutableListOf<String>()
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return emptyList()
            for (sbn in nm.activeNotifications) {
                if (sbn.packageName != packageName) continue
                val json = sbn.notification.extras.getString("miui.focus.param.media") ?: continue
                val share = NetEaseSongIdResolver.parseShareContentJson(json) ?: continue
                if (songId != null && share.songId != null && share.songId != songId) {
                    logI("skip stale notification pic songId=${share.songId} current=$songId")
                    continue
                }
                if (title != null && share.title != null && share.title != title) {
                    logI("skip stale notification pic title=${share.title} current=$title")
                    continue
                }
                share.pic?.let { urls.add(it) }
            }
            urls.distinct()
        } catch (e: Throwable) {
            logE("scanNotificationArtUrls failed: $packageName", e)
            emptyList()
        }
    }

    /**
     * @return 是否检测到曲目切换（用于锁屏即时刷新专辑/模糊/歌词取色）
     */
    fun refreshFromBind(
        context: Context,
        mediaData: Any?,
        metadata: MediaMetadata?
    ): Boolean {
        if (metadata != null) {
            lastBindMetadata = metadata
        }
        if (mediaData != null) {
            lastBindMediaData = mediaData
        }
        val meta = metadata ?: lastBindMetadata
        val data = mediaData ?: lastBindMediaData
        val trackKey = computeTrackKey(context, meta, data)
        val trackChanged = trackKey != null && trackKey != cachedTrackKey
        if (trackChanged) {
            logI("track changed on bind: $cachedTrackKey -> $trackKey")
        }
        val best = collectBest(
            context = context,
            drawable = null,
            metadata = meta,
            mediaData = data,
            includeCache = !trackChanged,
            // bind 路径只采本地源，避免主线程卡在 http 导致切歌不刷新
            allowRemote = false
        )
        if (best != null) {
            updateCache(best, trackKey)
        } else if (trackChanged) {
            // 切歌时封面可能尚未就绪：更新 trackKey 并清空旧封面，避免新歌挂上旧图后
            // 后续 resolve 误判「同曲」而一直复用上一首封面。
            cachedBitmap = null
            cachedTrackKey = trackKey
            logI("refresh empty on track change, cleared stale album cache")
        }
        return trackChanged
    }

    /**
     * AOD / bind 不来时：用 MediaSession metadata 同步曲目与封面缓存。
     * @return 是否切歌
     */
    fun refreshFromSessionMetadata(context: Context, metadata: MediaMetadata?): Boolean {
        if (metadata == null) return false
        lastBindMetadata = metadata
        val trackKey = computeTrackKey(context, metadata, lastBindMediaData)
        val trackChanged = trackKey != null && trackKey != cachedTrackKey
        if (trackChanged) {
            logI("track changed on session: $cachedTrackKey -> $trackKey")
        }
        val best = collectBest(
            context = context,
            drawable = null,
            metadata = metadata,
            mediaData = lastBindMediaData,
            includeCache = !trackChanged,
            allowRemote = false
        )
        if (best != null) {
            updateCache(best, trackKey)
        } else if (trackChanged) {
            cachedBitmap = null
            cachedTrackKey = trackKey
            logI("session refresh empty on track change, cleared stale album cache")
        }
        return trackChanged
    }

    /** 当前曲目已解析到可用封面（非切歌后空窗期）。 */
    fun hasResolvedArt(): Boolean {
        val b = cachedBitmap
        return b != null && !b.isRecycled
    }

    /**
     * 解析专辑图。重新进入音乐锁屏或切歌时应设 [ignoreCache]=true。
     * [allowRemote] 为 false 时跳过 http(s)，只用来自 metadata / mediaData / 本地 URI 的图，保证切歌即时。
     */
    fun resolve(
        context: Context,
        drawable: Drawable?,
        metadata: MediaMetadata? = null,
        ignoreCache: Boolean = false,
        mediaData: Any? = null,
        allowRemote: Boolean = true
    ): Bitmap? {
        val meta = metadata ?: lastBindMetadata
        val data = mediaData ?: lastBindMediaData
        val trackKey = computeTrackKey(context, meta, data)
        val trackChanged = trackKey != null && trackKey != cachedTrackKey
        // refreshFromBind 切歌后会先写入新 trackKey 并清空 bitmap。
        // 此空窗期内若误用「仍显示旧曲」的 ImageView，会把旧图挂到新曲上且长期不刷新。
        // 仅在 trackKey 已变时丢弃 drawable；同曲 artPending 时 drawable 往往是迟到的新封面。
        val artPending = trackKey != null &&
            trackKey == cachedTrackKey &&
            (cachedBitmap == null || cachedBitmap!!.isRecycled)
        val includeCache = !ignoreCache && !trackChanged && !artPending
        val safeDrawable = if (trackChanged) null else drawable

        val best = collectBest(
            context = context,
            drawable = safeDrawable,
            metadata = meta,
            mediaData = data,
            includeCache = includeCache,
            allowRemote = allowRemote
        )
        if (best != null) {
            updateCache(best, trackKey)
            logI(
                "resolved: ${best.width}x${best.height}, ignoreCache=$ignoreCache " +
                    "remote=$allowRemote pending=$artPending"
            )
            return best
        }
        // 同曲已有缓存时可回退；切歌空窗期 / artPending 绝不回退旧缓存冒充新图
        if (!trackChanged && !artPending) {
            getCached()?.let { cached ->
                logI("resolved fallback cache ${cached.width}x${cached.height}")
                return cached
            }
        }
        logE("resolve failed: no bitmap source (trackChanged=$trackChanged artPending=$artPending)")
        return null
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
        includeCache: Boolean,
        allowRemote: Boolean = true
    ): Bitmap? {
        val candidates = mutableListOf<Bitmap>()
        if (includeCache) {
            getCached()?.let { candidates.add(it) }
        }
        if (metadata != null) {
            addMetadataBitmaps(metadata, candidates)
        }
        if (mediaData != null) {
            extractIconBitmap(context, mediaData)?.let { candidates.add(it) }
        }
        for (url in collectArtUrlStrings(metadata, mediaData, context)) {
            val normalized = normalizeArtUrl(url) ?: continue
            if (!allowRemote && isRemoteUrl(normalized)) continue
            loadBitmapFromUri(context, normalized)?.let { candidates.add(it) }
        }
        extractFromDrawable(drawable)?.let { candidates.add(it) }

        val best = pickLargestVerified(candidates, metadata, mediaData, context)
        if (candidates.isNotEmpty()) {
            val sizes = candidates.joinToString { "${it.width}x${it.height}" }
            logI("candidates=[$sizes] -> best=${best?.width}x${best?.height}")
        }
        return best
    }

    private fun isRemoteUrl(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    /** 取最大候选；有 songId 时大图与当前曲一致即可，否则走 URL/视觉校验 */
    private fun pickLargestVerified(
        candidates: List<Bitmap>,
        metadata: MediaMetadata?,
        mediaData: Any?,
        context: Context
    ): Bitmap? {
        val valid = candidates.filter { !it.isRecycled && it.width > 0 && it.height > 0 }
        if (valid.isEmpty()) return null

        val referenceKeys = AlbumArtMatcher.collectReferenceKeys(metadata, mediaData, context)
        val visualRef = valid.minByOrNull { it.width * it.height } ?: return valid.maxByOrNull { it.width * it.height }

        val sorted = valid.sortedByDescending { it.width * it.height }
        for (candidate in sorted) {
            if (candidate === visualRef ||
                candidate.width * candidate.height <= visualRef.width * visualRef.height
            ) {
                return candidate
            }
            if (AlbumArtMatcher.acceptsNetworkUpgrade(visualRef, candidate, null, referenceKeys)) {
                return candidate
            }
            logI("skip unverified ${candidate.width}x${candidate.height}")
        }
        return visualRef
    }

    private fun extractRemoteArtUrls(mediaData: Any, songId: Long?, title: String?): List<String> {
        val urls = mutableListOf<String>()
        extractArtUriFromMediaData(mediaData)?.let { urls.add(it) }
        val notification = readMediaDataField(mediaData, "notification") as? Notification
        val json = notification?.extras?.getString("miui.focus.param.media")
        if (json != null) {
            val share = NetEaseSongIdResolver.parseShareContentJson(json)
            if (share != null) {
                val staleId = songId != null && share.songId != null && share.songId != songId
                val staleTitle = title != null && share.title != null && share.title != title
                if (!staleId && !staleTitle) {
                    share.pic?.let { urls.add(it) }
                } else {
                    logI("skip stale mediaData pic songId=${share.songId}/$songId title=${share.title}/$title")
                }
            } else {
                parseMiuiFocusPicJson(json)?.let { urls.add(it) }
            }
        }
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

    private fun computeTrackKey(context: Context?, metadata: MediaMetadata?, mediaData: Any?): String? {
        NetEaseSongIdResolver.resolveCanonicalSongId(context, metadata, mediaData)?.let {
            return NetEaseSongIdResolver.trackKey(it)
        }

        if (metadata != null) {
            val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            if (!mediaId.isNullOrBlank()) return "id:$mediaId"

            val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            if (!artUri.isNullOrBlank()) {
                val key = AlbumArtMatcher.netEaseImageKey(artUri) ?: artUri
                return "uri:$key"
            }

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
            if (!picUrl.isNullOrBlank()) {
                val key = AlbumArtMatcher.netEaseImageKey(picUrl) ?: picUrl
                return "pkg:$pkg|pic:$key"
            }

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

    private fun packageFromMediaData(mediaData: Any?): String? {
        if (mediaData == null) return null
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

    private fun normalizeArtUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (AlbumArtMatcher.isNetEaseArtUrl(url)) {
            NetEaseAlbumArtSource.upgradeFetchUrl(url)
        } else {
            url
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
        android.util.Log.i(TAG, msg)
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e(TAG, msg, e)
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
