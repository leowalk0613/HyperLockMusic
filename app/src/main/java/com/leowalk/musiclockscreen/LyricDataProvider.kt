package com.leowalk.musiclockscreen

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
 *
 * 轻量 JSON / 全量歌词 / 版本号落盘：应用进程被杀后冷拉起仍能恢复，无需自启动保活。
 */
class LyricDataProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.leowalk.musiclockscreen.lyric"
        val URI: Uri = Uri.parse("content://$AUTHORITY")

        private const val TAG = "HyperLockMusic_Provider"
        private const val LIGHT_FILE = "lyric_light.json"
        private const val FULL_FILE = "lyric_full.json"
        private const val META_PREFS = "lyric_provider_meta"
        private const val KEY_V_LYRIC = "v_lyric"
        private const val KEY_V_LYRIC_FD = "v_lyric_fd"

        @Volatile
        private var sLyricJson: String = "{}"

        @Volatile
        private var sLyricFd: ParcelFileDescriptor? = null

        @Volatile
        private var sVLyric: Int = 0

        @Volatile
        private var sVLyricFd: Int = 0

        @Volatile
        private var sCtx: Context? = null

        @Volatile
        private var sRestored: Boolean = false

        fun updateLyric(json: String) {
            sLyricJson = json
            sVLyric++
            persistLightAndMeta()
            notifyChanged()
        }

        fun updateLyricFd(fd: ParcelFileDescriptor) {
            val ctx = sCtx
            if (ctx == null) {
                sLyricFd?.let { try { it.close() } catch (_: Throwable) {} }
                sLyricFd = fd
                sVLyricFd++
                notifyChanged()
                return
            }
            try {
                val fullFile = File(ctx.filesDir, FULL_FILE)
                FileInputStream(fd.fileDescriptor).use { input ->
                    FileOutputStream(fullFile).use { output ->
                        input.copyTo(output)
                    }
                }
                try { fd.close() } catch (_: Throwable) {}
                openFullFdFromDisk(ctx)
                sVLyricFd++
                persistMeta(ctx)
                notifyChanged()
            } catch (e: Throwable) {
                Log.e(TAG, "updateLyricFd persist failed", e)
                try { fd.close() } catch (_: Throwable) {}
            }
        }

        private fun clearLyricFd() {
            sLyricFd?.let {
                try { it.close() } catch (_: Throwable) {}
            }
            sLyricFd = null
            sVLyricFd++
            sCtx?.let { ctx ->
                try {
                    File(ctx.filesDir, FULL_FILE).delete()
                } catch (_: Throwable) {
                }
                persistMeta(ctx)
            }
            notifyChanged()
        }

        private fun isEmptyLyricPush(json: String): Boolean {
            return try {
                val jo = org.json.JSONObject(json)
                val l = jo.optString("l", "").trim()
                val s = jo.optString("s", "").trim()
                val title = jo.optString("title", "").trim()
                val ctx = jo.optJSONObject("ctx")
                val hasCtxLines = ctx?.optJSONArray("lines")?.let { it.length() > 0 } == true
                // 仅有歌名的切歌轻量包不是「清空」——清掉全量 FD 会导致歌词闪空
                if (title.isNotBlank() || hasCtxLines) return false
                l.isEmpty() && s.isEmpty()
            } catch (_: Throwable) {
                false
            }
        }

        private fun notifyChanged() {
            try {
                sCtx?.contentResolver?.notifyChange(URI, null)
            } catch (_: Throwable) {}
        }

        private fun persistLightAndMeta() {
            val ctx = sCtx ?: return
            try {
                File(ctx.filesDir, LIGHT_FILE).writeText(sLyricJson, Charsets.UTF_8)
                persistMeta(ctx)
            } catch (e: Throwable) {
                Log.e(TAG, "persist light lyric failed", e)
            }
        }

        private fun persistMeta(ctx: Context) {
            try {
                ctx.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_V_LYRIC, sVLyric)
                    .putInt(KEY_V_LYRIC_FD, sVLyricFd)
                    .apply()
            } catch (e: Throwable) {
                Log.e(TAG, "persist lyric meta failed", e)
            }
        }

        private fun openFullFdFromDisk(ctx: Context): Boolean {
            val fullFile = File(ctx.filesDir, FULL_FILE)
            if (!fullFile.isFile || fullFile.length() <= 0L) {
                sLyricFd?.let { try { it.close() } catch (_: Throwable) {} }
                sLyricFd = null
                return false
            }
            return try {
                val opened = ParcelFileDescriptor.open(fullFile, ParcelFileDescriptor.MODE_READ_ONLY)
                sLyricFd?.let { try { it.close() } catch (_: Throwable) {} }
                sLyricFd = opened
                true
            } catch (e: Throwable) {
                Log.e(TAG, "open full lyric fd failed", e)
                sLyricFd = null
                false
            }
        }

        /** 进程冷启动：从磁盘恢复歌词与版本，避免 SystemUI 读到空 provider。 */
        fun restoreFromDiskIfNeeded(ctx: Context) {
            if (sRestored) return
            synchronized(this) {
                if (sRestored) return
                sCtx = ctx.applicationContext
                try {
                    val light = File(ctx.filesDir, LIGHT_FILE)
                    if (light.isFile && light.length() > 0L) {
                        sLyricJson = light.readText(Charsets.UTF_8)
                    }
                    openFullFdFromDisk(ctx)
                    val prefs = ctx.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
                    sVLyric = prefs.getInt(KEY_V_LYRIC, 0).coerceAtLeast(0)
                    sVLyricFd = prefs.getInt(KEY_V_LYRIC_FD, 0).coerceAtLeast(0)
                    Log.i(
                        TAG,
                        "restored lyric from disk: lightLen=${sLyricJson.length} " +
                            "hasFd=${sLyricFd != null} v=$sVLyric/$sVLyricFd"
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, "restore lyric from disk failed", e)
                } finally {
                    sRestored = true
                }
            }
        }
    }

    override fun onCreate(): Boolean {
        val ctx = context
        if (ctx != null) {
            restoreFromDiskIfNeeded(ctx)
        } else {
            sCtx = null
        }
        Log.i(TAG, "LyricDataProvider created, process=" + android.os.Process.myPid())
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        sCtx?.let { restoreFromDiskIfNeeded(it) }
            ?: context?.let { restoreFromDiskIfNeeded(it) }

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
                // 每次返回新的 dup，避免调用方 close 掉我们持有的 FD
                Bundle().apply {
                    sLyricFd?.let { owned ->
                        try {
                            putParcelable("fd", ParcelFileDescriptor.dup(owned.fileDescriptor))
                        } catch (e: Throwable) {
                            Log.e(TAG, "dup lyric fd failed", e)
                        }
                    }
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
