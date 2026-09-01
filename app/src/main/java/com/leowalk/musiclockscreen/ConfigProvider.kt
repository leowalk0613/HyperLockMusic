package com.leowalk.musiclockscreen

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

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
        const val KEY_ALBUM_NETWORK_HD = "album_sr_enhance"

        // 歌词配置
        const val KEY_SHOW_LYRIC = "show_lyric"
        const val KEY_LYRIC_SIZE = "lyric_size"
        const val KEY_SWAP_LYRIC = "swap_lyric"
        const val KEY_LYRIC_WIDTH = "lyric_width"
        const val KEY_LYRIC_BG_OFFSET_Y = "lyric_bg_offset_y"
        const val KEY_LYRIC_BG_ANCHOR_Y = "lyric_bg_anchor_y"
        const val KEY_IMMERSIVE_LYRIC = "immersive_lyric"
        const val KEY_LYRIC_HIDE_BACKGROUND = "lyric_hide_background"
        const val KEY_LYRIC_ALIGN = "lyric_align"
        const val KEY_IMMERSIVE_ALBUM = "immersive_album"
        const val KEY_IMMERSIVE_ALBUM_CENTER_Y = "immersive_album_center_y"
        const val KEY_MINIMAL_CLOCK = "minimal_clock"
        const val KEY_MINIMAL_CLOCK_SIZE = "minimal_clock_size"
        const val KEY_MINIMAL_CLOCK_TOP_Y = "minimal_clock_top_y"
        const val KEY_AOD_FULL_MEDIA = "aod_full_media"
        const val KEY_DISABLE_WALLPAPER_SCALE = "disable_wallpaper_scale"
        const val KEY_KEEP_LOCKSCREEN_ON = "keep_lockscreen_on"
        const val KEY_TITLE_BRACKET_MODE = "title_bracket_mode"
        const val KEY_MEDIA_WALLPAPER_ACTIVE = "media_wallpaper_active"
        const val KEY_MEDIA_LISTENER_READY = "media_listener_ready"
        const val KEY_MEDIA_PLAYBACK_ACTIVE = "media_playback_active"
        const val KEY_MEDIA_PLAYBACK_PACKAGE = "media_playback_package"
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
                KEY_ALBUM_NETWORK_HD,
                KEY_SHOW_LYRIC,
                KEY_LYRIC_SIZE,
                KEY_SWAP_LYRIC,
                KEY_LYRIC_WIDTH,
                KEY_LYRIC_BG_OFFSET_Y,
                KEY_LYRIC_BG_ANCHOR_Y,
                KEY_IMMERSIVE_LYRIC,
                KEY_LYRIC_HIDE_BACKGROUND,
                KEY_LYRIC_ALIGN,
                KEY_IMMERSIVE_ALBUM,
                KEY_IMMERSIVE_ALBUM_CENTER_Y,
                KEY_MINIMAL_CLOCK,
                KEY_MINIMAL_CLOCK_SIZE,
                KEY_MINIMAL_CLOCK_TOP_Y,
                KEY_AOD_FULL_MEDIA,
                KEY_DISABLE_WALLPAPER_SCALE,
                KEY_KEEP_LOCKSCREEN_ON,
                KEY_TITLE_BRACKET_MODE,
                KEY_MEDIA_WALLPAPER_ACTIVE,
                KEY_MEDIA_LISTENER_READY,
                KEY_MEDIA_PLAYBACK_ACTIVE,
                KEY_MEDIA_PLAYBACK_PACKAGE,
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
                if (prefs.getBoolean(KEY_ALBUM_NETWORK_HD, false)) 1 else 0,
                if (prefs.getBoolean(KEY_SHOW_LYRIC, true)) 1 else 0,
                prefs.getFloat(KEY_LYRIC_SIZE, 20f),
                if (prefs.getBoolean(KEY_SWAP_LYRIC, true)) 1 else 0,
                prefs.getFloat(KEY_LYRIC_WIDTH, 55f),
                prefs.getFloat(KEY_LYRIC_BG_OFFSET_Y, 12f),
                prefs.getFloat(KEY_LYRIC_BG_ANCHOR_Y, 62f),
                if (prefs.getBoolean(KEY_IMMERSIVE_LYRIC, true)) 1 else 0,
                if (prefs.getBoolean(KEY_LYRIC_HIDE_BACKGROUND, false)) 1 else 0,
                prefs.getString(KEY_LYRIC_ALIGN, ModuleConfig.LYRIC_ALIGN_LEFT)
                    ?: ModuleConfig.LYRIC_ALIGN_LEFT,
                if (prefs.getBoolean(KEY_IMMERSIVE_ALBUM, false)) 1 else 0,
                prefs.getFloat(KEY_IMMERSIVE_ALBUM_CENTER_Y, 38f),
                if (prefs.getBoolean(KEY_MINIMAL_CLOCK, true)) 1 else 0,
                prefs.getFloat(KEY_MINIMAL_CLOCK_SIZE, 30f),
                prefs.getFloat(KEY_MINIMAL_CLOCK_TOP_Y, 10f),
                if (prefs.getBoolean(KEY_AOD_FULL_MEDIA, false)) 1 else 0,
                if (prefs.getBoolean(KEY_DISABLE_WALLPAPER_SCALE, true)) 1 else 0,
                if (prefs.getBoolean(KEY_KEEP_LOCKSCREEN_ON, false)) 1 else 0,
                prefs.getString(KEY_TITLE_BRACKET_MODE, ModuleConfig.TITLE_BRACKET_DEFAULT)
                                    ?: ModuleConfig.TITLE_BRACKET_DEFAULT,
                if (prefs.getBoolean(KEY_MEDIA_WALLPAPER_ACTIVE, false)) 1 else 0,
                if (prefs.getBoolean(KEY_MEDIA_LISTENER_READY, false)) 1 else 0,
                if (prefs.getBoolean(KEY_MEDIA_PLAYBACK_ACTIVE, false)) 1 else 0,
                prefs.getString(KEY_MEDIA_PLAYBACK_PACKAGE, "") ?: "",
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
                if (values.containsKey(KEY_ALBUM_NETWORK_HD)) {
                    editor.putBoolean(KEY_ALBUM_NETWORK_HD, values.getAsInteger(KEY_ALBUM_NETWORK_HD) == 1)
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
                if (values.containsKey(KEY_IMMERSIVE_LYRIC)) {
                    editor.putBoolean(KEY_IMMERSIVE_LYRIC, values.getAsInteger(KEY_IMMERSIVE_LYRIC) == 1)
                }
                if (values.containsKey(KEY_LYRIC_HIDE_BACKGROUND)) {
                    editor.putBoolean(KEY_LYRIC_HIDE_BACKGROUND, values.getAsInteger(KEY_LYRIC_HIDE_BACKGROUND) == 1)
                }
                if (values.containsKey(KEY_LYRIC_ALIGN)) {
                    editor.putString(KEY_LYRIC_ALIGN, values.getAsString(KEY_LYRIC_ALIGN))
                }
                if (values.containsKey(KEY_IMMERSIVE_ALBUM)) {
                    editor.putBoolean(KEY_IMMERSIVE_ALBUM, values.getAsInteger(KEY_IMMERSIVE_ALBUM) == 1)
                }
                if (values.containsKey(KEY_IMMERSIVE_ALBUM_CENTER_Y)) {
                    editor.putFloat(
                        KEY_IMMERSIVE_ALBUM_CENTER_Y,
                        values.getAsFloat(KEY_IMMERSIVE_ALBUM_CENTER_Y)
                    )
                }
                if (values.containsKey(KEY_MINIMAL_CLOCK)) {
                    editor.putBoolean(KEY_MINIMAL_CLOCK, values.getAsInteger(KEY_MINIMAL_CLOCK) == 1)
                }
                if (values.containsKey(KEY_MINIMAL_CLOCK_SIZE)) {
                    editor.putFloat(KEY_MINIMAL_CLOCK_SIZE, values.getAsFloat(KEY_MINIMAL_CLOCK_SIZE))
                }
                if (values.containsKey(KEY_MINIMAL_CLOCK_TOP_Y)) {
                    editor.putFloat(KEY_MINIMAL_CLOCK_TOP_Y, values.getAsFloat(KEY_MINIMAL_CLOCK_TOP_Y))
                }
                if (values.containsKey(KEY_AOD_FULL_MEDIA)) {
                    editor.putBoolean(KEY_AOD_FULL_MEDIA, values.getAsInteger(KEY_AOD_FULL_MEDIA) == 1)
                }
                if (values.containsKey(KEY_DISABLE_WALLPAPER_SCALE)) {
                    editor.putBoolean(
                        KEY_DISABLE_WALLPAPER_SCALE,
                        values.getAsInteger(KEY_DISABLE_WALLPAPER_SCALE) == 1
                    )
                }
                if (values.containsKey(KEY_KEEP_LOCKSCREEN_ON)) {
                    editor.putBoolean(KEY_KEEP_LOCKSCREEN_ON, values.getAsInteger(KEY_KEEP_LOCKSCREEN_ON) == 1)
                }
                if (values.containsKey(KEY_TITLE_BRACKET_MODE)) {
                    editor.putString(KEY_TITLE_BRACKET_MODE, values.getAsString(KEY_TITLE_BRACKET_MODE))
                }
                if (values.containsKey(KEY_MEDIA_WALLPAPER_ACTIVE)) {
                    editor.putBoolean(KEY_MEDIA_WALLPAPER_ACTIVE, values.getAsInteger(KEY_MEDIA_WALLPAPER_ACTIVE) == 1)
                }
                if (values.containsKey(KEY_MEDIA_LISTENER_READY)) {
                    editor.putBoolean(
                        KEY_MEDIA_LISTENER_READY,
                        values.getAsInteger(KEY_MEDIA_LISTENER_READY) == 1
                    )
                }
                if (values.containsKey(KEY_MEDIA_PLAYBACK_ACTIVE)) {
                    editor.putBoolean(
                        KEY_MEDIA_PLAYBACK_ACTIVE,
                        values.getAsInteger(KEY_MEDIA_PLAYBACK_ACTIVE) == 1
                    )
                }
                if (values.containsKey(KEY_MEDIA_PLAYBACK_PACKAGE)) {
                    editor.putString(
                        KEY_MEDIA_PLAYBACK_PACKAGE,
                        values.getAsString(KEY_MEDIA_PLAYBACK_PACKAGE) ?: ""
                    )
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
}
