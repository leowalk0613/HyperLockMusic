package com.leowalk.musiclockscreen

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * 歌词数据 ContentProvider（与 aodchange 的 NotificationProvider 保持同样的协议）
 *
 * LyricFocus 在 aodchange 外部渲染模式下，检测到本 provider 存在时，
 * 会同步将歌词数据推送到这里，供锁屏歌词 overlay 消费。
 *
 * 协议（与 aodchange 完全一致）：
 * - call("versions", ...) → 返回各数据版本号（lyric/lyricfd）
 * - call("lyric", ...) → 获取轻量歌词 JSON（l/s/t/title/artist）
 * - call("lyric_fd", ...) → 获取全量歌词文件描述符（含 ctx.lines）
 * - call("putlyric", ..., extras) → LyricFocus 写入轻量歌词
 * - call("putlyricfd", ..., extras with fd) → LyricFocus 写入全量歌词 FD
 */
class LyricDataProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.leowalk.musiclockscreen.lyric"
        val URI: Uri = Uri.parse("content://$AUTHORITY")

        private const val TAG = "MusicLockScreen_Provider"

        @Volatile
        private var sLyricJson: String = "{}"

        @Volatile
        private var sLyricFd: ParcelFileDescriptor? = null

        @Volatile
        private var sVLyric: Int = 0

        @Volatile
        private var sVLyricFd: Int = 0

        @Volatile
        private var sCtx: android.content.Context? = null

        fun updateLyric(json: String) {
            sLyricJson = json
            sVLyric++
            notifyChanged()
        }

        fun updateLyricFd(fd: ParcelFileDescriptor) {
            sLyricFd?.let {
                try { it.close() } catch (_: Throwable) {}
            }
            sLyricFd = fd
            sVLyricFd++
            notifyChanged()
        }

        private fun clearLyricFd() {
            sLyricFd?.let {
                try { it.close() } catch (_: Throwable) {}
            }
            sLyricFd = null
            sVLyricFd++
            notifyChanged()
        }

        private fun isEmptyLyricPush(json: String): Boolean {
            return try {
                val jo = org.json.JSONObject(json)
                val l = jo.optString("l", "").trim()
                val s = jo.optString("s", "").trim()
                val ctx = jo.optJSONObject("ctx")
                val hasCtxLines = ctx?.optJSONArray("lines")?.let { it.length() > 0 } == true
                l.isEmpty() && s.isEmpty() && !hasCtxLines
            } catch (_: Throwable) {
                false
            }
        }

        private fun notifyChanged() {
            try {
                sCtx?.contentResolver?.notifyChange(URI, null)
            } catch (_: Throwable) {}
        }
    }

    override fun onCreate(): Boolean {
        sCtx = context
        Log.i(TAG, "LyricDataProvider created, process=" + android.os.Process.myPid())
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        Log.d(TAG, "call received: method=$method, arg=$arg, extrasKeys=${extras?.keySet()}")
        return when (method) {
            "versions" -> {
                Bundle().apply {
                    putInt("lyric", sVLyric)
                    putInt("lyricfd", sVLyricFd)
                }
            }
            "lyric" -> {
                Log.d(TAG, "lyric query: returning json=${sLyricJson.take(100)}, len=${sLyricJson.length}")
                Bundle().apply { putString("n", sLyricJson) }
            }
            "lyric_fd" -> {
                Bundle().apply {
                    sLyricFd?.let { putParcelable("fd", it) }
                }
            }
            "putlyric" -> {
                val json = extras?.getString("n")
                if (json != null) {
                    if (isEmptyLyricPush(json)) {
                        updateLyric(json)
                        clearLyricFd()
                        Log.d(TAG, "putlyric empty lyric, cleared fd, v=$sVLyric/$sVLyricFd, json=$json")
                    } else {
                        updateLyric(json)
                        Log.d(TAG, "putlyric received, v=$sVLyric, json=$json")
                    }
                } else {
                    Log.d(TAG, "putlyric received but json is null, extrasKeys=${extras?.keySet()}")
                }
                Bundle()
            }
            "putlyricfd" -> {
                val fd = extras?.getParcelable("fd") as? ParcelFileDescriptor
                if (fd != null) {
                    updateLyricFd(fd)
                    Log.d(TAG, "putlyricfd received, v=$sVLyricFd")
                }
                Bundle()
            }
            else -> null
        }
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
