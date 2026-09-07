package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.media.MediaMetadata
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * QQ 音乐歌曲 ID：MediaData.mediaId / MEDIA_ID 为数字 songid（非 songmid）。
 */
object QqMusicSongIdResolver {

    private const val TAG = "HyperLockMusic_QqMusicId"
    const val TRACK_PREFIX = "qqmusic:"
    const val PKG = "com.tencent.qqmusic"

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun trackKey(songId: Long): String = "$TRACK_PREFIX$songId"

    fun parseSongIdFromTrackKey(trackKey: String?): Long? {
        if (trackKey.isNullOrBlank() || !trackKey.startsWith(TRACK_PREFIX)) return null
        return trackKey.removePrefix(TRACK_PREFIX).toLongOrNull()
    }

    fun resolveCanonicalSongId(
        context: Context?,
        metadata: MediaMetadata?,
        mediaData: Any?,
    ): Long? {
        val pkg = packageFromMediaData(mediaData)
        if (pkg != null && pkg != PKG) {
            logI("skip qqmusic id for package=$pkg")
            return null
        }

        readMediaDataSongId(mediaData)?.let {
            logI("song id from mediaData: $it")
            return it
        }

        if (pkg == PKG) {
            parseSongId(metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))?.let {
                logI("song id from metadata: $it")
                return it
            }
        }

        logI("no qqmusic song id")
        return null
    }

    /**
     * 用数字 songid 换 albummid（官方封面 CDN 需要 mid）。
     */
    fun fetchAlbumMidBySongId(songId: Long): String? {
        var conn: HttpURLConnection? = null
        return try {
            val apiUrl =
                "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg" +
                    "?songid=$songId&tpl=yqq_song_detail&format=json&platform=yqq"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Referer", "https://y.qq.com")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
                )
            }
            val body = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            if (root.optInt("code", -1) != 0) {
                logI("song detail api code=${root.optInt("code")} for id=$songId")
                return null
            }
            val album = root.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")
            val mid = album?.optString("mid")?.takeIf { it.isNotBlank() }
                ?: album?.optString("pmid")?.substringBefore('_')?.takeIf { it.isNotBlank() }
            logI("song detail id=$songId albumMid=$mid")
            mid
        } catch (e: Throwable) {
            logE("fetchAlbumMidBySongId failed id=$songId", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun coverUrlForAlbumMid(albumMid: String, size: Int = 1500): String {
        val mid = albumMid.substringBefore('_')
        return "https://y.gtimg.cn/music/photo_new/T002R${size}x${size}M000$mid.jpg"
    }

    fun parseSongId(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().toLongOrNull()?.takeIf { it > 0 }
    }

    private fun readMediaDataSongId(mediaData: Any?): Long? {
        if (mediaData == null) return null
        return try {
            val field = mediaData.javaClass.getDeclaredField("mediaId").apply { isAccessible = true }
            when (val v = field.get(mediaData)) {
                is Long -> v.takeIf { it > 0 }
                is Int -> v.toLong().takeIf { it > 0 }
                is String -> parseSongId(v)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun packageFromMediaData(mediaData: Any?): String? {
        if (mediaData == null) return null
        return try {
            val field = mediaData.javaClass.getDeclaredField("packageName").apply { isAccessible = true }
            (field.get(mediaData) as? String)?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
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
