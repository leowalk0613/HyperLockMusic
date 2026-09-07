package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.media.MediaMetadata
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * QQ 曲库歌曲标识：
 * - QQ 音乐 App：MediaData / MEDIA_ID 为数字 songid
 * - 小米音乐（QQ SDK）：岛通知 shareContent 的 songmid（mediaId 常不可用）
 */
object QqMusicSongIdResolver {

    private const val TAG = "HyperLockMusic_QqMusicId"
    const val TRACK_PREFIX = "qqmusic:"
    const val TRACK_MID_PREFIX = "qqmusic:mid:"
    const val PKG = "com.tencent.qqmusic"
    const val PKG_MIUI = "com.miui.player"

    private val QQ_CATALOG_PACKAGES = setOf(PKG, PKG_MIUI)

    private val SONGMID_IN_URL = Regex("""[?&]songmid=([0-9A-Za-z]+)""")

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun isQqCatalogPackage(pkg: String?): Boolean =
        pkg != null && pkg in QQ_CATALOG_PACKAGES

    fun trackKey(songId: Long): String = "$TRACK_PREFIX$songId"

    fun trackKeyFromSongMid(songMid: String): String = "$TRACK_MID_PREFIX$songMid"

    fun parseSongIdFromTrackKey(trackKey: String?): Long? {
        if (trackKey.isNullOrBlank() || !trackKey.startsWith(TRACK_PREFIX)) return null
        if (trackKey.startsWith(TRACK_MID_PREFIX)) return null
        return trackKey.removePrefix(TRACK_PREFIX).toLongOrNull()
    }

    fun parseSongMidFromTrackKey(trackKey: String?): String? {
        if (trackKey.isNullOrBlank() || !trackKey.startsWith(TRACK_MID_PREFIX)) return null
        return trackKey.removePrefix(TRACK_MID_PREFIX).takeIf { it.isNotBlank() }
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
     * 小米音乐等：从岛通知 shareContent 取 songmid（勿用不可靠的 mediaId）。
     */
    fun resolveSongMid(
        context: Context?,
        metadata: MediaMetadata?,
        mediaData: Any?,
    ): String? {
        val pkg = packageFromMediaData(mediaData)
            ?: context?.let { HookUtils.currentMediaPackage(it) }
        if (pkg != null && !isQqCatalogPackage(pkg)) {
            logI("skip qqmusic mid for package=$pkg")
            return null
        }

        extractMiuiFocusMediaJson(mediaData)?.let { parseSongMidFromShareJson(it) }?.let {
            logI("song mid from share: $it pkg=$pkg")
            return it
        }

        logI("no qqmusic song mid")
        return null
    }

    /** 按 trackKey / 会话解析 albummid，供官方 CDN 拉封面。 */
    fun resolveAlbumMidForCover(
        context: Context?,
        metadata: MediaMetadata?,
        mediaData: Any?,
        trackKey: String?,
    ): String? {
        parseSongIdFromTrackKey(trackKey)?.let { id ->
            fetchAlbumMidBySongId(id)?.let { return it }
        }
        parseSongMidFromTrackKey(trackKey)?.let { mid ->
            fetchAlbumMidBySongMid(mid)?.let { return it }
        }
        resolveCanonicalSongId(context, metadata, mediaData)?.let { id ->
            fetchAlbumMidBySongId(id)?.let { return it }
        }
        resolveSongMid(context, metadata, mediaData)?.let { mid ->
            fetchAlbumMidBySongMid(mid)?.let { return it }
        }
        return null
    }

    /**
     * 用数字 songid 换 albummid（官方封面 CDN 需要 mid）。
     */
    fun fetchAlbumMidBySongId(songId: Long): String? =
        fetchAlbumMidFromDetailApi("songid=$songId", "id=$songId")

    fun fetchAlbumMidBySongMid(songMid: String): String? {
        val mid = songMid.trim().takeIf { it.isNotEmpty() } ?: return null
        return fetchAlbumMidFromDetailApi("songmid=$mid", "mid=$mid")
    }

    private fun fetchAlbumMidFromDetailApi(query: String, logLabel: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val apiUrl =
                "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg" +
                    "?$query&tpl=yqq_song_detail&format=json&platform=yqq"
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
                logI("song detail api code=${root.optInt("code")} for $logLabel")
                return null
            }
            val album = root.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")
            val mid = album?.optString("mid")?.takeIf { it.isNotBlank() }
                ?: album?.optString("pmid")?.substringBefore('_')?.takeIf { it.isNotBlank() }
            logI("song detail $logLabel albumMid=$mid")
            mid
        } catch (e: Throwable) {
            logE("fetchAlbumMid failed $logLabel", e)
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

    fun parseSongMidFromShareJson(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            val share = JSONObject(json)
                .optJSONObject("param_v2")
                ?.optJSONObject("param_island")
                ?.optJSONObject("shareData")
            parseSongMidFromUrl(share?.optString("shareContent"))
                ?: SONGMID_IN_URL.find(json)?.groupValues?.getOrNull(1)
        } catch (_: Throwable) {
            SONGMID_IN_URL.find(json)?.groupValues?.getOrNull(1)
        }
    }

    fun parseSongMidFromUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return SONGMID_IN_URL.find(raw)?.groupValues?.getOrNull(1)
    }

    private fun extractMiuiFocusMediaJson(mediaData: Any?): String? {
        if (mediaData == null) return null
        // HyperOS MediaData 常直接带 mediaFocusParam；notification extras 作回退
        try {
            val field = mediaData.javaClass.getDeclaredField("mediaFocusParam").apply { isAccessible = true }
            (field.get(mediaData) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Throwable) {
        }
        return try {
            val field = mediaData.javaClass.getDeclaredField("notification").apply { isAccessible = true }
            val notification = field.get(mediaData) as? android.app.Notification ?: return null
            notification.extras.getString("miui.focus.param.media")
        } catch (_: Throwable) {
            null
        }
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
