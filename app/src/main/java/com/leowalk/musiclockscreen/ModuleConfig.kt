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
    /** 后台拉网易云高清替换前景专辑（非本地超分） */
    private const val KEY_ALBUM_NETWORK_HD = "album_sr_enhance"

    // 歌词相关
    private const val KEY_SHOW_LYRIC = "show_lyric"
    private const val KEY_LYRIC_SIZE = "lyric_size"       // 主行字号 sp
    private const val KEY_SWAP_LYRIC = "swap_lyric"       // 歌词/翻译互换
    private const val KEY_LYRIC_WIDTH = "lyric_width"     // 歌词区域宽度（占专辑宽度百分比）
    private const val KEY_LYRIC_BG_OFFSET_Y = "lyric_bg_offset_y" // 已弃用，保留兼容
    private const val KEY_LYRIC_BG_ANCHOR_Y = "lyric_bg_anchor_y" // 歌词底边占屏幕高度百分比
    private const val KEY_IMMERSIVE_LYRIC = "immersive_lyric"
    private const val KEY_LYRIC_HIDE_BACKGROUND = "lyric_hide_background"
    private const val KEY_LYRIC_ALIGN = "lyric_align" // left / center / right
    private const val KEY_IMMERSIVE_ALBUM = "immersive_album"
    /** 沉浸封面竖直中心占屏高百分比（与大专辑底边 [KEY_ALBUM_OFFSET_Y] 互不共用） */
    private const val KEY_IMMERSIVE_ALBUM_CENTER_Y = "immersive_album_center_y"
    private const val KEY_MINIMAL_CLOCK = "minimal_clock"
    private const val KEY_MINIMAL_CLOCK_SIZE = "minimal_clock_size"
    private const val KEY_MINIMAL_CLOCK_TOP_Y = "minimal_clock_top_y"
    private const val KEY_TITLE_BRACKET_MODE = "title_bracket_mode" // default / shrink / hide
    private const val KEY_AOD_FULL_MEDIA = "aod_full_media"
    private const val KEY_KEEP_LOCKSCREEN_ON = "keep_lockscreen_on"
    private const val KEY_MUSIC_WHITELIST_ENABLED = "music_whitelist_enabled"
    private const val KEY_MUSIC_WHITELIST = "music_whitelist"

    const val TITLE_BRACKET_DEFAULT = "default"
    const val TITLE_BRACKET_SHRINK = "shrink"
    const val TITLE_BRACKET_HIDE = "hide"
    const val TITLE_BRACKET_LINE = "line"

    const val LYRIC_ALIGN_LEFT = "left"
    const val LYRIC_ALIGN_CENTER = "center"
    const val LYRIC_ALIGN_RIGHT = "right"

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
    private const val DEFAULT_ALBUM_OFFSET_Y = 55f
    private const val DEFAULT_ALBUM_CORNER = 24f
    private const val DEFAULT_ALBUM_NETWORK_HD = false

    private const val DEFAULT_SHOW_LYRIC = true
    private const val DEFAULT_LYRIC_SIZE = 20f
    private const val DEFAULT_SWAP_LYRIC = true
    private const val DEFAULT_LYRIC_WIDTH = 55f
    private const val DEFAULT_LYRIC_BG_OFFSET_Y = 12f
    private const val DEFAULT_LYRIC_BG_ANCHOR_Y = 62f
    private const val DEFAULT_IMMERSIVE_LYRIC = true
    private const val DEFAULT_LYRIC_HIDE_BACKGROUND = false
    private const val DEFAULT_LYRIC_ALIGN = LYRIC_ALIGN_LEFT
    private const val DEFAULT_IMMERSIVE_ALBUM = false
    private const val DEFAULT_IMMERSIVE_ALBUM_CENTER_Y = 38f
    private const val DEFAULT_MINIMAL_CLOCK = true
    private const val DEFAULT_MINIMAL_CLOCK_SIZE = 30f
    private const val DEFAULT_MINIMAL_CLOCK_TOP_Y = 10f
    private const val DEFAULT_TITLE_BRACKET_MODE = TITLE_BRACKET_DEFAULT
    private const val DEFAULT_AOD_FULL_MEDIA = false
    private const val DEFAULT_KEEP_LOCKSCREEN_ON = false
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

    /** 专辑底边占屏幕高度百分比（默认 55） */
    var albumOffsetY: Float
        get() = getPrefs().getFloat(KEY_ALBUM_OFFSET_Y, DEFAULT_ALBUM_OFFSET_Y)
        set(value) = getPrefs().edit().putFloat(KEY_ALBUM_OFFSET_Y, value).apply()

    /** 与 [albumOffsetY] 同义，便于 Xposed 侧阅读 */
    var albumAnchorY: Float
        get() = albumOffsetY
        set(value) { albumOffsetY = value }

    var albumCorner: Float
        get() = getPrefs().getFloat(KEY_ALBUM_CORNER, DEFAULT_ALBUM_CORNER)
        set(value) = getPrefs().edit().putFloat(KEY_ALBUM_CORNER, value).apply()

    /** 后台拉网易云高清替换前景大专辑（模糊背景仍用系统封面） */
    var albumNetworkHd: Boolean
        get() = getPrefs().getBoolean(KEY_ALBUM_NETWORK_HD, DEFAULT_ALBUM_NETWORK_HD)
        set(value) = getPrefs().edit().putBoolean(KEY_ALBUM_NETWORK_HD, value).apply()

    var showLyric: Boolean
        get() = getPrefs().getBoolean(KEY_SHOW_LYRIC, DEFAULT_SHOW_LYRIC)
        set(value) = getPrefs().edit().putBoolean(KEY_SHOW_LYRIC, value).apply()

    var lyricSize: Float
        get() = getPrefs().getFloat(KEY_LYRIC_SIZE, DEFAULT_LYRIC_SIZE)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_SIZE, value).apply()

    var swapLyric: Boolean
        get() = getPrefs().getBoolean(KEY_SWAP_LYRIC, DEFAULT_SWAP_LYRIC)
        set(value) = getPrefs().edit().putBoolean(KEY_SWAP_LYRIC, value).apply()

    /** 歌词区域宽度：占屏幕宽度的百分比（默认 55） */
    var lyricWidth: Float
        get() = getPrefs().getFloat(KEY_LYRIC_WIDTH, DEFAULT_LYRIC_WIDTH)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_WIDTH, value).apply()

    /** 歌词底边占屏幕高度百分比（默认 62） */
    var lyricBgAnchorY: Float
        get() = getPrefs().getFloat(KEY_LYRIC_BG_ANCHOR_Y, DEFAULT_LYRIC_BG_ANCHOR_Y)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_BG_ANCHOR_Y, value).apply()

    /** 沉浸歌词：仅显示当前行大字，隐藏方形专辑 */
    var immersiveLyric: Boolean
        get() = getPrefs().getBoolean(KEY_IMMERSIVE_LYRIC, DEFAULT_IMMERSIVE_LYRIC)
        set(value) = getPrefs().edit().putBoolean(KEY_IMMERSIVE_LYRIC, value).apply()

    /** 隐藏歌词雾状背景 */
    var lyricHideBackground: Boolean
        get() = getPrefs().getBoolean(KEY_LYRIC_HIDE_BACKGROUND, DEFAULT_LYRIC_HIDE_BACKGROUND)
        set(value) = getPrefs().edit().putBoolean(KEY_LYRIC_HIDE_BACKGROUND, value).apply()

    /** 沉浸歌词排版：left / center / right */
    var lyricAlign: String
        get() = getPrefs().getString(KEY_LYRIC_ALIGN, DEFAULT_LYRIC_ALIGN) ?: DEFAULT_LYRIC_ALIGN
        set(value) = getPrefs().edit().putString(KEY_LYRIC_ALIGN, value).apply()

    /** 沉浸专辑：Monet 取色铺底 + 完整封面 */
    var immersiveAlbum: Boolean
        get() = getPrefs().getBoolean(KEY_IMMERSIVE_ALBUM, DEFAULT_IMMERSIVE_ALBUM)
        set(value) = getPrefs().edit().putBoolean(KEY_IMMERSIVE_ALBUM, value).apply()

    /** 沉浸封面竖直中心占屏高百分比（默认 38，仅沉浸烘焙用） */
    var immersiveAlbumCenterY: Float
        get() = getPrefs().getFloat(KEY_IMMERSIVE_ALBUM_CENTER_Y, DEFAULT_IMMERSIVE_ALBUM_CENTER_Y)
        set(value) = getPrefs().edit().putFloat(KEY_IMMERSIVE_ALBUM_CENTER_Y, value).apply()

    /** 音乐锁屏简洁时钟：隐藏系统大时钟，顶部显示一行时间日期 */
    var minimalClock: Boolean
        get() = getPrefs().getBoolean(KEY_MINIMAL_CLOCK, DEFAULT_MINIMAL_CLOCK)
        set(value) = getPrefs().edit().putBoolean(KEY_MINIMAL_CLOCK, value).apply()

    /** 简洁时钟字号（sp） */
    var minimalClockSize: Float
        get() = getPrefs().getFloat(KEY_MINIMAL_CLOCK_SIZE, DEFAULT_MINIMAL_CLOCK_SIZE)
        set(value) = getPrefs().edit().putFloat(KEY_MINIMAL_CLOCK_SIZE, value).apply()

    /** 简洁时钟顶边占屏高百分比 */
    var minimalClockTopY: Float
        get() = getPrefs().getFloat(KEY_MINIMAL_CLOCK_TOP_Y, DEFAULT_MINIMAL_CLOCK_TOP_Y)
        set(value) = getPrefs().edit().putFloat(KEY_MINIMAL_CLOCK_TOP_Y, value).apply()

    /**
     * 专辑样式 ↔ 歌词样式绑定：
     * - 沉浸封面 → 普通歌词 + 无背景
     * - 大专辑（非沉浸封面）→ 沉浸歌词
     */
    fun applyAlbumLyricBinding(immersiveAlbumOn: Boolean) {
        if (immersiveAlbumOn) {
            immersiveLyric = false
            lyricHideBackground = true
        } else {
            immersiveLyric = true
            lyricHideBackground = false
        }
    }

    /** AOD 完整媒体控件（展开 + 进度） */
    var aodFullMedia: Boolean
        get() = getPrefs().getBoolean(KEY_AOD_FULL_MEDIA, DEFAULT_AOD_FULL_MEDIA)
        set(value) = getPrefs().edit().putBoolean(KEY_AOD_FULL_MEDIA, value).apply()

    /** 音乐锁屏时保持常亮 */
    var keepLockScreenOn: Boolean
        get() = getPrefs().getBoolean(KEY_KEEP_LOCKSCREEN_ON, DEFAULT_KEEP_LOCKSCREEN_ON)
        set(value) = getPrefs().edit().putBoolean(KEY_KEEP_LOCKSCREEN_ON, value).apply()

    /** @deprecated 请用 [lyricBgAnchorY] */
    var lyricBgOffsetY: Float
        get() = getPrefs().getFloat(KEY_LYRIC_BG_OFFSET_Y, DEFAULT_LYRIC_BG_OFFSET_Y)
        set(value) = getPrefs().edit().putFloat(KEY_LYRIC_BG_OFFSET_Y, value).apply()

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

    /** 白名单关闭则一律允许；开启则仅白名单内包名。 */
    fun isPackageAllowed(packageName: String?): Boolean {
        if (!musicWhitelistEnabled) return true
        if (packageName.isNullOrEmpty()) return false
        val list = getWhitelist().ifEmpty { DEFAULT_WHITELIST }
        return list.any { it.equals(packageName, ignoreCase = true) }
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
                put("album_sr_enhance", if (albumNetworkHd) 1 else 0)
                put("show_lyric", if (showLyric) 1 else 0)
                put("lyric_size", lyricSize)
                put("swap_lyric", if (swapLyric) 1 else 0)
                put("lyric_width", lyricWidth)
                put("lyric_bg_offset_y", lyricBgOffsetY)
                put("lyric_bg_anchor_y", lyricBgAnchorY)
                put("immersive_lyric", if (immersiveLyric) 1 else 0)
                put("lyric_hide_background", if (lyricHideBackground) 1 else 0)
                put("lyric_align", lyricAlign)
                put("immersive_album", if (immersiveAlbum) 1 else 0)
                put("immersive_album_center_y", immersiveAlbumCenterY)
                put("minimal_clock", if (minimalClock) 1 else 0)
                put("minimal_clock_size", minimalClockSize)
                put("minimal_clock_top_y", minimalClockTopY)
                put("aod_full_media", if (aodFullMedia) 1 else 0)
                put("keep_lockscreen_on", if (keepLockScreenOn) 1 else 0)
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
