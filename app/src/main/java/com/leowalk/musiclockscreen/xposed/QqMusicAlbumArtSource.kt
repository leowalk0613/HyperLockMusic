package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import java.net.HttpURLConnection
import java.net.URL

/**
 * QQ 音乐封面：songid → albummid → y.gtimg.cn T002 高清图。
 */
object QqMusicAlbumArtSource {

    private const val TAG = "HyperLockMusic_QqMusicArt"
    private val FETCH_SIZES = intArrayOf(1500, 800, 500)

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun fetchVerifiedHighRes(
        context: Context,
        reference: Bitmap,
        metadata: MediaMetadata?,
        mediaData: Any?,
        trackKey: String? = null,
    ): Bitmap? {
        val expectedId = QqMusicSongIdResolver.parseSongIdFromTrackKey(trackKey)
            ?: QqMusicSongIdResolver.resolveCanonicalSongId(context, metadata, mediaData)
            ?: return null.also { logI("no qqmusic song id trackKey=$trackKey") }

        logI("fetch by song id=$expectedId trackKey=$trackKey")
        val albumMid = QqMusicSongIdResolver.fetchAlbumMidBySongId(expectedId) ?: return null

        var best: Bitmap? = null
        var bestUrl: String? = null
        for (size in FETCH_SIZES) {
            val url = QqMusicSongIdResolver.coverUrlForAlbumMid(albumMid, size)
            val bmp = loadBitmapFromUrl(url) ?: continue
            if (bmp.width * bmp.height <= reference.width * reference.height) {
                logI("reject $url ${bmp.width}x${bmp.height}: not larger")
                bmp.recycle()
                continue
            }
            // 可信 songid 链路：官方 CDN，接受更大图
            if (best == null || bmp.width * bmp.height > best.width * best.height) {
                best?.recycle()
                best = bmp
                bestUrl = url
            } else {
                bmp.recycle()
            }
            // 已拿到足够大的图就停
            if (best != null && minOf(best.width, best.height) >= 1080) break
        }

        if (best != null) {
            logI("accepted by song id=$expectedId ${best.width}x${best.height} from $bestUrl")
        } else {
            logI("no larger qqmusic art for id=$expectedId")
        }
        return best
    }

    private fun loadBitmapFromUrl(urlString: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Referer", "https://y.qq.com")
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
