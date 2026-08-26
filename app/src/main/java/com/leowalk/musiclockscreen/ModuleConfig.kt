package com.leowalk.musiclockscreen

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * 模块配置
 */
object ModuleConfig {
    private const val PREFS_NAME = "music_lockscreen_prefs"
    private const val KEY_SHOW_BIG_ALBUM = "show_big_album"
    private const val KEY_BLUR_RADIUS = "blur_radius"
    private const val KEY_DARK_OVERLAY = "dark_overlay"
    private const val KEY_ALBUM_SIZE = "album_size"
    private const val KEY_ALBUM_OFFSET_Y = "album_offset_y"
    private const val KEY_ALBUM_CORNER = "album_corner"

    // 歌词相关
    private const val KEY_SHOW_LYRIC = "show_lyric"
    private const val KEY_LYRIC_SIZE = "lyric_size"       // 主行字号 sp
    private const val KEY_SWAP_LYRIC = "swap_lyric"       // 歌词/翻译互换
    private const val KEY_LYRIC_WIDTH = "lyric_width"     // 歌词区域宽度（占专辑宽度百分比）
    private const val KEY_LYRIC_BG_OFFSET_Y = "lyric_bg_offset_y" // 歌词背景底边微调（dp，正值下移）
    private const val KEY_LYRIC_BG_ANCHOR_Y = "lyric_bg_anchor_y" // 歌词背景底边占屏幕高度百分比
    private const val KEY_TITLE_BRACKET_MODE = "title_bracket_mode" // default / shrink / hide
    private const val KEY_MUSIC_WHITELIST_ENABLED = "music_whitelist_enabled"
    private const val KEY_MUSIC_WHITELIST = "music_whitelist"

    const val TITLE_BRACKET_DEFAULT = "default"
    const val TITLE_BRACKET_SHRINK = "shrink"
    const val TITLE_BRACKET_HIDE = "hide"
    const val TITLE_BRACKET_LINE = "line"

    /** 默认音乐应用白名单包名 */
    val DEFAULT_WHITELIST: List<String> = listOf(
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
        "com.luna.music",
        "com.miui.player",
        "com.kugou.android",
        "com.kuwo.kwmusiccar",
        "cn.kuwo.player",
        "com.apple.android.music",
        "com.google.android.apps.youtube.music",
        "com.spotify.music",
    )

    private const val DEFAULT_SHOW_BIG_ALBUM = true
    private const val DEFAULT_BLUR_RADIUS = 80f
    private const val DEFAULT_DARK_OVERLAY = 140
    private const val DEFAULT_ALBUM_SIZE = 55f
    private const val DEFAULT_ALBUM_OFFSET_Y = -80f
    private const val DEFAULT_ALBUM_CORNER = 24f

    private const val DEFAULT_SHOW_LYRIC = true
    private const val DEFAULT_LYRIC_SIZE = 20f
    private const val DEFAULT_SWAP_LYRIC = true
    private const val DEFAULT_LYRIC_WIDTH = 100f
    private const val DEFAULT_LYRIC_BG_OFFSET_Y = 0f
    private const val DEFAULT_LYRIC_BG_ANCHOR_Y = 62f
    private const val DEFAULT_TITLE_BRACKET_MODE = TITLE_BRACKET_DEFAULT
    private const val DEFAULT_MUSIC_WHITELIST_ENABLED = false

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getPrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("ModuleConfig not initialized")
    }

    var showBigAlbum: Boolean
        get() = getPrefs().getBoolean(KEY_SHOW_BIG_ALBUM, DEFAULT_SHOW_BIG_ALBUM)
        set(value) = getPrefs().edit().putBoolean(KEY_SHOW_BIG_ALBUM, value).apply()

    var blurRadius: Float
        get() = getPrefs().getFloat(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)
        set(value) = getPrefs().edit().putFloat(KEY_BLUR_RADIUS, value).apply()

    var darkOverlay: Int
        get() = getPrefs().getInt(KEY_DARK_OVERLAY, DEFAULT_DARK_OVERLAY)
        set(value) = getPrefs().edit().putInt(KEY_DARK_OVERLAY, value).apply()

    var albumSize: Float
        get() = getPrefs().getFloat(KEY_ALBUM_SIZE, DEFAULT_ALBUM_SIZE)
        set(value) = getPrefs().edit().putFloat(KEY_ALBUM_SIZE, value).apply()

    var albumOffsetY: Float
        get() = getPrefs().getFloat(KEY_ALBUM_OFFSET_Y, DEFAULT_ALBUM_OFFSET_Y)
        set(value) = getPrefs().edit().putFloat(KEY_ALBUM_OFFSET_Y, value).apply()

    var albumCorner: Float
        get() = getPrefs().getFloat(KEY_ALBUM_CORNER, DEFAULT_ALBUM_CORNER)
        set(value) = getPrefs().edit().putFloat(KEY_ALBUM_CORNER, value).apply()

    var showLyric: Boolean
        get() = getPrefs().getBoolean(KEY_SHOW_LYRIC, DEFAULT_SHOW_LYRIC)
        set(value) = getPrefs().edit().putBoolean(KEY_SHOW_LYRIC, value).apply()

    var lyricSize: Float
        get() = getPrefs().getFloat(KEY_LYRIC_SIZE, DEFAULT_LYRIC_SIZE)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_SIZE, value).apply()

    var swapLyric: Boolean
        get() = getPrefs().getBoolean(KEY_SWAP_LYRIC, DEFAULT_SWAP_LYRIC)
        set(value) = getPrefs().edit().putBoolean(KEY_SWAP_LYRIC, value).apply()

    /** 歌词区域宽度：占专辑宽度的百分比（默认 100 = 与专辑同宽） */
    var lyricWidth: Float
        get() = getPrefs().getFloat(KEY_LYRIC_WIDTH, DEFAULT_LYRIC_WIDTH)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_WIDTH, value).apply()

    /** 歌词背景底边微调（dp）：正值下移，负值上移。 */
    var lyricBgOffsetY: Float
        get() = getPrefs().getFloat(KEY_LYRIC_BG_OFFSET_Y, DEFAULT_LYRIC_BG_OFFSET_Y)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_BG_OFFSET_Y, value).apply()

    /** 歌词背景底边占屏幕高度百分比（默认 62）。 */
    var lyricBgAnchorY: Float
        get() = getPrefs().getFloat(KEY_LYRIC_BG_ANCHOR_Y, DEFAULT_LYRIC_BG_ANCHOR_Y)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_BG_ANCHOR_Y, value).apply()

    /** 媒体标题括号：default=原样 / shrink=右侧缩小 / hide=去掉括号 */
    var titleBracketMode: String
        get() = getPrefs().getString(KEY_TITLE_BRACKET_MODE, DEFAULT_TITLE_BRACKET_MODE)
            ?: DEFAULT_TITLE_BRACKET_MODE
        set(value) = getPrefs().edit().putString(KEY_TITLE_BRACKET_MODE, value).apply()

    /** 开启后仅白名单内应用可开启/保持音乐锁屏 */
    var musicWhitelistEnabled: Boolean
        get() = getPrefs().getBoolean(KEY_MUSIC_WHITELIST_ENABLED, DEFAULT_MUSIC_WHITELIST_ENABLED)
        set(value) = getPrefs().edit().putBoolean(KEY_MUSIC_WHITELIST_ENABLED, value).apply()

    /** 白名单包名（逗号分隔存储） */
    var musicWhitelist: String
        get() = getPrefs().getString(KEY_MUSIC_WHITELIST, "") ?: ""
        set(value) = getPrefs().edit().putString(KEY_MUSIC_WHITELIST, value).apply()

    fun getWhitelist(): List<String> {
        val raw = musicWhitelist
        if (raw.isEmpty()) return emptyList()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun saveWhitelist(list: List<String>) {
        musicWhitelist = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
    }

    fun ensureDefaultWhitelistIfEmpty() {
        if (getWhitelist().isEmpty()) {
            saveWhitelist(DEFAULT_WHITELIST)
        }
    }

    /** 变更后推送全部配置到 SystemUI 进程（经 ConfigProvider）。 */
    fun push(context: Context) {
        try {
            val uri = Uri.parse("content://com.leowalk.musiclockscreen.config/config")
            val values = ContentValues().apply {
                put("show_big_album", if (showBigAlbum) 1 else 0)
                put("blur_radius", blurRadius)
                put("dark_overlay", darkOverlay)
                put("album_size", albumSize)
                put("album_offset_y", albumOffsetY)
                put("album_corner", albumCorner)
                put("show_lyric", if (showLyric) 1 else 0)
                put("lyric_size", lyricSize)
                put("swap_lyric", if (swapLyric) 1 else 0)
                put("lyric_width", lyricWidth)
                put("lyric_bg_offset_y", lyricBgOffsetY)
                put("lyric_bg_anchor_y", lyricBgAnchorY)
                put("title_bracket_mode", titleBracketMode)
                put("music_whitelist_enabled", if (musicWhitelistEnabled) 1 else 0)
                put("music_whitelist", musicWhitelist)
            }
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
