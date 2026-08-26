package com.leowalk.musiclockscreen

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

/**
 * 配置提供者 - 用于跨进程读取配置
 *
 * SystemUI/AOD 进程通过 ContentProvider 读取配置
 */
class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.leowalk.musiclockscreen.config"
        const val KEY_SHOW_BIG_ALBUM = "show_big_album"
        const val KEY_BLUR_RADIUS = "blur_radius"
        const val KEY_DARK_OVERLAY = "dark_overlay"
        const val KEY_ALBUM_SIZE = "album_size"
        const val KEY_ALBUM_OFFSET_Y = "album_offset_y"
        const val KEY_ALBUM_CORNER = "album_corner"
        const val KEY_ALBUM_SR_ENHANCE = "album_sr_enhance"

        // 歌词配置
        const val KEY_SHOW_LYRIC = "show_lyric"
        const val KEY_LYRIC_SIZE = "lyric_size"
        const val KEY_SWAP_LYRIC = "swap_lyric"
        const val KEY_LYRIC_WIDTH = "lyric_width"
        const val KEY_LYRIC_BG_OFFSET_Y = "lyric_bg_offset_y"
        const val KEY_LYRIC_BG_ANCHOR_Y = "lyric_bg_anchor_y"
        const val KEY_TITLE_BRACKET_MODE = "title_bracket_mode"
        const val KEY_MEDIA_WALLPAPER_ACTIVE = "media_wallpaper_active"
        const val KEY_MUSIC_WHITELIST_ENABLED = "music_whitelist_enabled"
        const val KEY_MUSIC_WHITELIST = "music_whitelist"

        private const val CODE_CONFIG = 1

        private const val PREFS_NAME = "music_lockscreen_prefs"

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "config", CODE_CONFIG)
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> queryConfig(ctx)
            else -> null
        }
    }

    private fun queryConfig(ctx: Context): Cursor {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, 0)
        val cursor = MatrixCursor(
            arrayOf(
                KEY_SHOW_BIG_ALBUM,
                KEY_BLUR_RADIUS,
                KEY_DARK_OVERLAY,
                KEY_ALBUM_SIZE,
                KEY_ALBUM_OFFSET_Y,
                KEY_ALBUM_CORNER,
                KEY_ALBUM_SR_ENHANCE,
                KEY_SHOW_LYRIC,
                KEY_LYRIC_SIZE,
                KEY_SWAP_LYRIC,
                KEY_LYRIC_WIDTH,
                KEY_LYRIC_BG_OFFSET_Y,
                KEY_LYRIC_BG_ANCHOR_Y,
                KEY_TITLE_BRACKET_MODE,
                KEY_MEDIA_WALLPAPER_ACTIVE,
                KEY_MUSIC_WHITELIST_ENABLED,
                KEY_MUSIC_WHITELIST
            )
        )
        cursor.addRow(
            arrayOf<Any>(
                if (prefs.getBoolean(KEY_SHOW_BIG_ALBUM, true)) 1 else 0,
                prefs.getFloat(KEY_BLUR_RADIUS, 80f),
                prefs.getInt(KEY_DARK_OVERLAY, 140),
                prefs.getFloat(KEY_ALBUM_SIZE, 55f),
                prefs.getFloat(KEY_ALBUM_OFFSET_Y, 55f),
                prefs.getFloat(KEY_ALBUM_CORNER, 24f),
                if (prefs.getBoolean(KEY_ALBUM_SR_ENHANCE, false)) 1 else 0,
                if (prefs.getBoolean(KEY_SHOW_LYRIC, true)) 1 else 0,
                prefs.getFloat(KEY_LYRIC_SIZE, 20f),
                if (prefs.getBoolean(KEY_SWAP_LYRIC, true)) 1 else 0,
                prefs.getFloat(KEY_LYRIC_WIDTH, 55f),
                prefs.getFloat(KEY_LYRIC_BG_OFFSET_Y, 12f),
                prefs.getFloat(KEY_LYRIC_BG_ANCHOR_Y, 62f),
                prefs.getString(KEY_TITLE_BRACKET_MODE, ModuleConfig.TITLE_BRACKET_DEFAULT)
                                    ?: ModuleConfig.TITLE_BRACKET_DEFAULT,
                if (prefs.getBoolean(KEY_MEDIA_WALLPAPER_ACTIVE, false)) 1 else 0,
                if (prefs.getBoolean(KEY_MUSIC_WHITELIST_ENABLED, false)) 1 else 0,
                prefs.getString(KEY_MUSIC_WHITELIST, "") ?: ""
            )
        )
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        val ctx = context ?: return 0
        if (values == null) return 0

        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, 0)
                val editor = prefs.edit()

                if (values.containsKey(KEY_SHOW_BIG_ALBUM)) {
                    editor.putBoolean(KEY_SHOW_BIG_ALBUM, values.getAsInteger(KEY_SHOW_BIG_ALBUM) == 1)
                }
                if (values.containsKey(KEY_BLUR_RADIUS)) {
                    editor.putFloat(KEY_BLUR_RADIUS, values.getAsFloat(KEY_BLUR_RADIUS))
                }
                if (values.containsKey(KEY_DARK_OVERLAY)) {
                    editor.putInt(KEY_DARK_OVERLAY, values.getAsInteger(KEY_DARK_OVERLAY))
                }
                if (values.containsKey(KEY_ALBUM_SIZE)) {
                    editor.putFloat(KEY_ALBUM_SIZE, values.getAsFloat(KEY_ALBUM_SIZE))
                }
                if (values.containsKey(KEY_ALBUM_OFFSET_Y)) {
                    editor.putFloat(KEY_ALBUM_OFFSET_Y, values.getAsFloat(KEY_ALBUM_OFFSET_Y))
                }
                if (values.containsKey(KEY_ALBUM_CORNER)) {
                    editor.putFloat(KEY_ALBUM_CORNER, values.getAsFloat(KEY_ALBUM_CORNER))
                }
                if (values.containsKey(KEY_ALBUM_SR_ENHANCE)) {
                    editor.putBoolean(KEY_ALBUM_SR_ENHANCE, values.getAsInteger(KEY_ALBUM_SR_ENHANCE) == 1)
                }
                if (values.containsKey(KEY_SHOW_LYRIC)) {
                    editor.putBoolean(KEY_SHOW_LYRIC, values.getAsInteger(KEY_SHOW_LYRIC) == 1)
                }
                if (values.containsKey(KEY_LYRIC_SIZE)) {
                    editor.putFloat(KEY_LYRIC_SIZE, values.getAsFloat(KEY_LYRIC_SIZE))
                }
                if (values.containsKey(KEY_SWAP_LYRIC)) {
                    editor.putBoolean(KEY_SWAP_LYRIC, values.getAsInteger(KEY_SWAP_LYRIC) == 1)
                }
                if (values.containsKey(KEY_LYRIC_WIDTH)) {
                    editor.putFloat(KEY_LYRIC_WIDTH, values.getAsFloat(KEY_LYRIC_WIDTH))
                }
                if (values.containsKey(KEY_LYRIC_BG_OFFSET_Y)) {
                    editor.putFloat(KEY_LYRIC_BG_OFFSET_Y, values.getAsFloat(KEY_LYRIC_BG_OFFSET_Y))
                }
                if (values.containsKey(KEY_LYRIC_BG_ANCHOR_Y)) {
                    editor.putFloat(KEY_LYRIC_BG_ANCHOR_Y, values.getAsFloat(KEY_LYRIC_BG_ANCHOR_Y))
                }
                if (values.containsKey(KEY_TITLE_BRACKET_MODE)) {
                    editor.putString(KEY_TITLE_BRACKET_MODE, values.getAsString(KEY_TITLE_BRACKET_MODE))
                }
                if (values.containsKey(KEY_MEDIA_WALLPAPER_ACTIVE)) {
                    editor.putBoolean(KEY_MEDIA_WALLPAPER_ACTIVE, values.getAsInteger(KEY_MEDIA_WALLPAPER_ACTIVE) == 1)
                }
                if (values.containsKey(KEY_MUSIC_WHITELIST_ENABLED)) {
                    editor.putBoolean(
                        KEY_MUSIC_WHITELIST_ENABLED,
                        values.getAsInteger(KEY_MUSIC_WHITELIST_ENABLED) == 1
                    )
                }
                if (values.containsKey(KEY_MUSIC_WHITELIST)) {
                    editor.putString(KEY_MUSIC_WHITELIST, values.getAsString(KEY_MUSIC_WHITELIST) ?: "")
                }

                editor.apply()
                ctx.contentResolver.notifyChange(uri, null)
                1
            }
            else -> 0
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (method == "enhanceAlbumSelfTest") {
            return try {
                val size = (arg?.toIntOrNull() ?: 180).coerceIn(64, 512)
                val src = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(src)
                val paint = android.graphics.Paint()
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        paint.color = android.graphics.Color.rgb(
                            (x * 255 / size),
                            (y * 255 / size),
                            ((x + y) * 255 / (2 * size))
                        )
                        canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                    }
                }
                val t0 = android.os.SystemClock.elapsedRealtime()
                val enhanced = try {
                    AlbumSrEngine.enhanceTo720(ctx, src)
                } finally {
                    if (!src.isRecycled) src.recycle()
                }
                val ms = android.os.SystemClock.elapsedRealtime() - t0
                if (enhanced == null) {
                    android.util.Log.e("MusicLockScreen_Config", "enhanceAlbumSelfTest failed null")
                    return Bundle().apply {
                        putBoolean("ok", false)
                        putLong("ms", ms)
                    }
                }
                val out = Bundle().apply {
                    putBoolean("ok", true)
                    putInt("in", size)
                    putInt("out_w", enhanced.width)
                    putInt("out_h", enhanced.height)
                    putLong("ms", ms)
                }
                if (!enhanced.isRecycled) enhanced.recycle()
                android.util.Log.i(
                    "MusicLockScreen_Config",
                    "enhanceAlbumSelfTest ok in=${size} out=${out.getInt("out_w")}x${out.getInt("out_h")} ms=$ms"
                )
                out
            } catch (e: Throwable) {
                android.util.Log.e("MusicLockScreen_Config", "enhanceAlbumSelfTest error", e)
                Bundle().apply {
                    putBoolean("ok", false)
                    putString("error", e.message ?: e.javaClass.simpleName)
                }
            }
        }
        if (method != "enhanceAlbum") return null
        val jpeg = extras?.getByteArray("jpeg") ?: return null
        return try {
            val src = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                ?: return null
            val enhanced = try {
                AlbumSrEngine.enhanceTo720(ctx, src)
            } finally {
                if (!src.isRecycled) src.recycle()
            } ?: return null
            val out = java.io.ByteArrayOutputStream()
            enhanced.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            if (!enhanced.isRecycled) enhanced.recycle()
            Bundle().apply { putByteArray("jpeg", out.toByteArray()) }
        } catch (e: Throwable) {
            android.util.Log.e("MusicLockScreen_Config", "enhanceAlbum failed", e)
            null
        }
    }
}
