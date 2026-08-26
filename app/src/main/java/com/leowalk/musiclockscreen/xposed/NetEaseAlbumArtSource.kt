package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import java.net.HttpURLConnection
import java.net.URL

/**
 * 网易云封面：优先按歌曲 ID 拉官方 picUrl，ID 不一致则拒绝替换。
 */
object NetEaseAlbumArtSource {

    private const val TAG = "MusicLockScreen_NetEaseArt"
    private const val TARGET_MIN_SIDE = 1080

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun fetchVerifiedHighRes(
        context: Context,
        reference: Bitmap,
        metadata: MediaMetadata?,
        mediaData: Any?,
        trackKey: String? = null
    ): Bitmap? {
        val expectedId = NetEaseSongIdResolver.parseSongIdFromTrackKey(trackKey)
            ?: NetEaseSongIdResolver.resolveCanonicalSongId(context, metadata, mediaData)

        if (expectedId != null) {
            logI("fetch by song id=$expectedId trackKey=$trackKey")
            val picUrl = NetEaseSongIdResolver.fetchPicUrlBySongId(expectedId) ?: return null
            val bmp = loadBitmapFromUrl(NetEaseAlbumArtSource.upgradeFetchUrl(picUrl)) ?: return null
            if (bmp.width * bmp.height <= reference.width * reference.height) {
                logI("reject id=$expectedId ${bmp.width}x${bmp.height}: not larger")
                bmp.recycle()
                return null
            }
            logI("accepted by song id=$expectedId ${bmp.width}x${bmp.height}")
            return bmp
        }

        logI("no canonical song id, fallback url match ref=${reference.width}x${reference.height}")
        return fetchByVerifiedUrls(context, reference, metadata, mediaData, trackKey)
    }

    private fun fetchByVerifiedUrls(
        context: Context,
        reference: Bitmap,
        metadata: MediaMetadata?,
        mediaData: Any?,
        trackKey: String?
    ): Bitmap? {
        val referenceKeys = AlbumArtMatcher.collectReferenceKeys(metadata, mediaData, context)
        val urls = buildFetchUrls(context, metadata, mediaData)
        if (urls.isEmpty()) {
            logI("no netease urls")
            return null
        }

        var best: Bitmap? = null
        var bestUrl: String? = null
        for (url in urls) {
            val bmp = loadBitmapFromUrl(url) ?: continue
            if (!AlbumArtMatcher.acceptsNetworkUpgrade(reference, bmp, url, referenceKeys, trackKey)) {
                logI("reject $url ${bmp.width}x${bmp.height}: not same cover")
                if (best == null || bmp !== reference) bmp.recycle()
                continue
            }
            if (best == null || bmp.width * bmp.height > best.width * best.height) {
                best?.recycle()
                best = bmp
                bestUrl = url
            } else {
                bmp.recycle()
            }
        }

        if (best != null) {
            logI("accepted ${best.width}x${best.height} from $bestUrl")
        } else {
            logI("no verified netease art")
        }
        return best
    }

    fun tryResolveHighRes(
        context: Context,
        reference: Bitmap?,
        metadata: MediaMetadata?,
        mediaData: Any?,
        trackKey: String? = null
    ): Bitmap? {
        if (reference == null || reference.isRecycled) return null
        val minSide = minOf(reference.width, reference.height)
        if (minSide >= TARGET_MIN_SIDE) return null
        return fetchVerifiedHighRes(context, reference, metadata, mediaData, trackKey)
    }

    fun upgradeFetchUrl(url: String): String {
        var u = url.trim()
        if (u.startsWith("http://")) {
            u = "https://" + u.removePrefix("http://")
        }
        val q = u.indexOf('?')
        if (q >= 0) u = u.substring(0, q)
        return u
    }

    private fun buildFetchUrls(
        context: Context,
        metadata: MediaMetadata?,
        mediaData: Any?
    ): List<String> {
        val ordered = LinkedHashSet<String>()
        for (raw in AlbumArtResolver.collectArtUrlStrings(metadata, mediaData, context)) {
            if (!AlbumArtMatcher.isNetEaseArtUrl(raw)) continue
            ordered.add(upgradeFetchUrl(raw))
            ordered.add(upgradeFetchUrl(raw) + "?param=1000y1000")
        }
        return ordered.toList()
    }

    private fun loadBitmapFromUrl(urlString: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false })
            }?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        } catch (e: Throwable) {
            logE("load failed: $urlString", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun logI(msg: String) {
        android.util.Log.i(TAG, msg)
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e(TAG, msg, e)
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
