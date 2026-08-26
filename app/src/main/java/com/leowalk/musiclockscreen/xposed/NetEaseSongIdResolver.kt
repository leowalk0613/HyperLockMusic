package com.leowalk.musiclockscreen.xposed

import android.app.NotificationManager
import android.content.Context
import android.media.MediaMetadata
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云歌曲 ID：优先 mediaData 通知 shareContent（与 pic 同源），避免切歌时 metadata 与旧通知冲突。
 */
object NetEaseSongIdResolver {

    private const val TAG = "HyperLockMusic_NetEaseId"
    private const val TRACK_PREFIX = "netease:"
    private const val PKG = "com.netease.cloudmusic"

    private val SONG_ID_IN_URL = Regex("""(?:song\?|[?&]id=)(\d{5,})""")

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun trackKey(songId: Long): String = "$TRACK_PREFIX$songId"

    fun parseSongIdFromTrackKey(trackKey: String?): Long? {
        if (trackKey.isNullOrBlank() || !trackKey.startsWith(TRACK_PREFIX)) return null
        return trackKey.removePrefix(TRACK_PREFIX).toLongOrNull()
    }

    /**
     * 按优先级取单个 ID（不要求多源完全一致）：
     * 1. 当前 bind 的 mediaData 通知 shareContent
     * 2. MediaMetadata.MEDIA_ID
     * 3. 活动通知里与当前标题匹配的一条
     */
    fun resolveCanonicalSongId(
        context: Context?,
        metadata: MediaMetadata?,
        mediaData: Any?
    ): Long? {
        val fromMediaData = mediaData?.let { parseShareContent(extractMiuiFocusMediaJson(it)) }
        if (fromMediaData != null) {
            logI("song id from mediaData: $fromMediaData")
            return fromMediaData
        }

        val fromMeta = parseMediaId(metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
        if (fromMeta != null) {
            logI("song id from metadata: $fromMeta")
            return fromMeta
        }

        if (context != null) {
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val fromNotification = resolveFromActiveNotifications(context, title)
            if (fromNotification != null) {
                logI("song id from notification: $fromNotification title=$title")
                return fromNotification
            }
        }

        logI("no netease song id")
        return null
    }

    /** 调试：列出各来源 ID（可能不一致） */
    fun collectSongIds(
        context: Context?,
        metadata: MediaMetadata?,
        mediaData: Any?
    ): Set<Long> {
        val ids = linkedSetOf<Long>()
        parseMediaId(metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))?.let { ids.add(it) }
        mediaData?.let { parseShareContent(extractMiuiFocusMediaJson(it))?.let { id -> ids.add(id) } }
        if (context != null) {
            for (json in scanNotificationMediaJson(context, PKG)) {
                parseShareContent(json)?.let { ids.add(it) }
            }
        }
        return ids
    }

    fun fetchPicUrlBySongId(songId: Long): String? {
        var conn: HttpURLConnection? = null
        return try {
            val idsParam = URLEncoder.encode("[$songId]", "UTF-8")
            val apiUrl = "https://music.163.com/api/song/detail/?ids=$idsParam"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Referer", "https://music.163.com")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                )
            }
            val body = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            if (root.optInt("code", -1) != 200) {
                logI("song detail api code=${root.optInt("code")} for id=$songId")
                return null
            }
            val picUrl = root.optJSONArray("songs")
                ?.optJSONObject(0)
                ?.optJSONObject("album")
                ?.optString("picUrl")
                ?.takeIf { it.isNotBlank() }
            logI("song detail id=$songId picUrl=$picUrl")
            picUrl
        } catch (e: Throwable) {
            logE("fetchPicUrlBySongId failed id=$songId", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun parseShareContentJson(json: String?): ShareData? {
        if (json.isNullOrBlank()) return null
        return try {
            val share = JSONObject(json)
                .optJSONObject("param_v2")
                ?.optJSONObject("param_island")
                ?.optJSONObject("shareData") ?: return null
            ShareData(
                title = share.optString("title").takeIf { it.isNotBlank() },
                pic = share.optString("pic").takeIf { it.isNotBlank() },
                songId = parseMediaId(share.optString("shareContent"))
                    ?: SONG_ID_IN_URL.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            )
        } catch (_: Throwable) {
            val id = SONG_ID_IN_URL.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (id != null) ShareData(songId = id) else null
        }
    }

    data class ShareData(
        val title: String? = null,
        val pic: String? = null,
        val songId: Long? = null
    )

    private fun resolveFromActiveNotifications(context: Context, title: String?): Long? {
        for (json in scanNotificationMediaJson(context, PKG)) {
            val share = parseShareContentJson(json) ?: continue
            if (title != null && share.title != null && share.title != title) continue
            share.songId?.let { return it }
        }
        return null
    }

    private fun parseMediaId(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.trim().toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        return SONG_ID_IN_URL.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun parseShareContent(json: String?): Long? = parseShareContentJson(json)?.songId

    private fun extractMiuiFocusMediaJson(mediaData: Any): String? {
        return try {
            val field = mediaData.javaClass.getDeclaredField("notification").apply { isAccessible = true }
            val notification = field.get(mediaData) as? android.app.Notification ?: return null
            notification.extras.getString("miui.focus.param.media")
        } catch (_: Throwable) {
            null
        }
    }

    private fun scanNotificationMediaJson(context: Context, packageName: String): List<String> {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return emptyList()
            nm.activeNotifications
                .asSequence()
                .filter { it.packageName == packageName }
                .mapNotNull { it.notification.extras.getString("miui.focus.param.media") }
                .toList()
        } catch (_: Throwable) {
            emptyList()
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
