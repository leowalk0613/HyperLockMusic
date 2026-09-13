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
    /** 后台拉官方高清替换前景专辑（非本地超分；网易云 / QQ / 小米音乐） */
    private const val KEY_ALBUM_NETWORK_HD = "album_sr_enhance"

    // 歌词相关
    private const val KEY_LYRIC_ENABLED = "lyric_enabled"   // 主界面总开关：整个歌词功能
    private const val KEY_SHOW_LYRIC = "show_lyric"           // 显示歌词（设置 / 锁屏按钮）
    private const val KEY_LYRIC_SIZE = "lyric_size"       // 主行字号 sp
    private const val KEY_SWAP_LYRIC = "swap_lyric"       // 歌词/翻译互换
    private const val KEY_LYRIC_WIDTH = "lyric_width"     // 歌词区域宽度（占专辑宽度百分比）
    private const val KEY_LYRIC_BG_OFFSET_Y = "lyric_bg_offset_y" // 已弃用，保留兼容
    private const val KEY_LYRIC_BG_ANCHOR_Y = "lyric_bg_anchor_y" // 歌词底边占屏幕高度百分比
    private const val KEY_IMMERSIVE_LYRIC = "immersive_lyric"
    private const val KEY_LYRIC_HIDE_BACKGROUND = "lyric_hide_background"
    private const val KEY_LYRIC_ALIGN = "lyric_align" // left / center / right
    private const val KEY_LYRIC_TRANSITION = "lyric_transition" // fade / slide_*
    /** 沉浸歌词三行上滑（上一句/当前/下一句）；AOD 无效 */
    private const val KEY_IMMERSIVE_LYRIC_STACK = "immersive_lyric_stack"
    private const val KEY_IMMERSIVE_ALBUM = "immersive_album"
    /** 沉浸封面竖直中心占屏高百分比（与大专辑底边 [KEY_ALBUM_OFFSET_Y] 互不共用） */
    private const val KEY_IMMERSIVE_ALBUM_CENTER_Y = "immersive_album_center_y"
    /** 沉浸壁纸上下沿暗色渐变 */
    private const val KEY_IMMERSIVE_ALBUM_EDGE_GRADIENT = "immersive_album_edge_gradient"
    /** 锁屏封面基底：big_album / immersive / magazine */
    private const val KEY_LOCKSCREEN_CHROME = "lockscreen_chrome"
    /** 退出画报模式时恢复的普通封面样式 */
    private const val KEY_LAST_NORMAL_CHROME = "last_normal_chrome"
    /** 画报右划页样式（big_album / immersive），与锁屏 chrome 分离 */
    private const val KEY_MAGAZINE_PAGE_CHROME = "magazine_page_chrome"

    // 画报页独立档案（与普通锁屏键互不影响）
    private const val KEY_MAGAZINE_BLUR_RADIUS = "magazine_blur_radius"
    private const val KEY_MAGAZINE_DARK_OVERLAY = "magazine_dark_overlay"
    private const val KEY_MAGAZINE_ALBUM_SIZE = "magazine_album_size"
    private const val KEY_MAGAZINE_ALBUM_OFFSET_Y = "magazine_album_offset_y"
    private const val KEY_MAGAZINE_ALBUM_CORNER = "magazine_album_corner"
    private const val KEY_MAGAZINE_ALBUM_NETWORK_HD = "magazine_album_sr_enhance"
    private const val KEY_MAGAZINE_IMMERSIVE_ALBUM_CENTER_Y = "magazine_immersive_album_center_y"
    private const val KEY_MAGAZINE_IMMERSIVE_ALBUM_EDGE_GRADIENT = "magazine_immersive_album_edge_gradient"
    private const val KEY_MAGAZINE_SHOW_LYRIC = "magazine_show_lyric"
    private const val KEY_MAGAZINE_LYRIC_SIZE = "magazine_lyric_size"
    private const val KEY_MAGAZINE_SWAP_LYRIC = "magazine_swap_lyric"
    private const val KEY_MAGAZINE_LYRIC_WIDTH = "magazine_lyric_width"
    private const val KEY_MAGAZINE_LYRIC_BG_ANCHOR_Y = "magazine_lyric_bg_anchor_y"
    private const val KEY_MAGAZINE_IMMERSIVE_LYRIC = "magazine_immersive_lyric"
    private const val KEY_MAGAZINE_LYRIC_HIDE_BACKGROUND = "magazine_lyric_hide_background"
    private const val KEY_MAGAZINE_LYRIC_ALIGN = "magazine_lyric_align"
    private const val KEY_MAGAZINE_LYRIC_TRANSITION = "magazine_lyric_transition"
    private const val KEY_MAGAZINE_IMMERSIVE_LYRIC_STACK = "magazine_immersive_lyric_stack"
    private const val KEY_MAGAZINE_KEEP_LOCKSCREEN_ON = "magazine_keep_lockscreen_on"
    private const val KEY_MAGAZINE_TITLE_BRACKET_MODE = "magazine_title_bracket_mode"

    private const val KEY_MINIMAL_CLOCK = "minimal_clock"
    private const val KEY_MINIMAL_CLOCK_SIZE = "minimal_clock_size"
    private const val KEY_MINIMAL_CLOCK_TOP_Y = "minimal_clock_top_y"
    private const val KEY_TITLE_BRACKET_MODE = "title_bracket_mode" // default / shrink / hide
    private const val KEY_AOD_FULL_MEDIA = "aod_full_media"
    private const val KEY_DISABLE_WALLPAPER_SCALE = "disable_wallpaper_scale"
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

    const val LYRIC_TRANSITION_FADE = "fade"
    const val LYRIC_TRANSITION_SLIDE_LEFT = "slide_left"
    const val LYRIC_TRANSITION_SLIDE_RIGHT = "slide_right"
    const val LYRIC_TRANSITION_SLIDE_UP = "slide_up"
    const val LYRIC_TRANSITION_SLIDE_DOWN = "slide_down"

    const val CHROME_BIG_ALBUM = "big_album"
    const val CHROME_IMMERSIVE = "immersive"
    const val CHROME_MAGAZINE = "magazine"

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

    private const val DEFAULT_LYRIC_ENABLED = true
    private const val DEFAULT_SHOW_LYRIC = true
    private const val DEFAULT_LYRIC_SIZE = 20f
    private const val DEFAULT_SWAP_LYRIC = true
    private const val DEFAULT_LYRIC_WIDTH = 55f
    private const val DEFAULT_LYRIC_BG_OFFSET_Y = 12f
    private const val DEFAULT_LYRIC_BG_ANCHOR_Y = 62f
    private const val DEFAULT_IMMERSIVE_LYRIC = true
    private const val DEFAULT_LYRIC_HIDE_BACKGROUND = false
    private const val DEFAULT_LYRIC_ALIGN = LYRIC_ALIGN_LEFT
    private const val DEFAULT_LYRIC_TRANSITION = LYRIC_TRANSITION_FADE
    private const val DEFAULT_IMMERSIVE_LYRIC_STACK = false
    private const val DEFAULT_IMMERSIVE_ALBUM = false
    private const val DEFAULT_IMMERSIVE_ALBUM_CENTER_Y = 38f
    private const val DEFAULT_IMMERSIVE_ALBUM_EDGE_GRADIENT = true
    private const val DEFAULT_LOCKSCREEN_CHROME = CHROME_BIG_ALBUM
    private const val DEFAULT_MINIMAL_CLOCK = true
    private const val DEFAULT_MINIMAL_CLOCK_SIZE = 30f
    private const val DEFAULT_MINIMAL_CLOCK_TOP_Y = 10f
    private const val DEFAULT_TITLE_BRACKET_MODE = TITLE_BRACKET_DEFAULT
    private const val DEFAULT_AOD_FULL_MEDIA = false
    private const val DEFAULT_DISABLE_WALLPAPER_SCALE = true
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

    /** 后台拉官方高清替换前景大专辑（网易云 / QQ / 小米音乐；模糊背景仍用系统封面） */
    var albumNetworkHd: Boolean
        get() = getPrefs().getBoolean(KEY_ALBUM_NETWORK_HD, DEFAULT_ALBUM_NETWORK_HD)
        set(value) = getPrefs().edit().putBoolean(KEY_ALBUM_NETWORK_HD, value).apply()

    /** 主界面总开关：关闭后整个歌词功能不可用，锁屏按钮无法重新开启。 */
    var lyricEnabled: Boolean
        get() = getPrefs().getBoolean(KEY_LYRIC_ENABLED, DEFAULT_LYRIC_ENABLED)
        set(value) = getPrefs().edit().putBoolean(KEY_LYRIC_ENABLED, value).apply()

    /** 是否显示歌词（设置页 / 锁屏媒体按钮）；需 [lyricEnabled] 为 true 才生效。 */
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

    /** 锁屏歌词切行：fade / slide_left / slide_right / slide_up / slide_down（AOD 不播） */
    var lyricTransition: String
        get() = getPrefs().getString(KEY_LYRIC_TRANSITION, DEFAULT_LYRIC_TRANSITION)
            ?: DEFAULT_LYRIC_TRANSITION
        set(value) = getPrefs().edit().putString(KEY_LYRIC_TRANSITION, value).apply()

    /** 沉浸歌词三行上滑：显示上一句/当前/下一句（仅亮屏；AOD 退回单行） */
    var immersiveLyricStack: Boolean
        get() = getPrefs().getBoolean(KEY_IMMERSIVE_LYRIC_STACK, DEFAULT_IMMERSIVE_LYRIC_STACK)
        set(value) = getPrefs().edit().putBoolean(KEY_IMMERSIVE_LYRIC_STACK, value).apply()

    /** 沉浸专辑：Monet 取色铺底 + 完整封面 */
    var immersiveAlbum: Boolean
        get() = getPrefs().getBoolean(KEY_IMMERSIVE_ALBUM, DEFAULT_IMMERSIVE_ALBUM)
        set(value) = getPrefs().edit().putBoolean(KEY_IMMERSIVE_ALBUM, value).apply()

    /** 沉浸封面竖直中心占屏高百分比（默认 38，仅沉浸烘焙用） */
    var immersiveAlbumCenterY: Float
        get() = getPrefs().getFloat(KEY_IMMERSIVE_ALBUM_CENTER_Y, DEFAULT_IMMERSIVE_ALBUM_CENTER_Y)
        set(value) = getPrefs().edit().putFloat(KEY_IMMERSIVE_ALBUM_CENTER_Y, value).apply()

    /** 沉浸壁纸上下沿暗色渐变（范围随专辑位置变化） */
    var immersiveAlbumEdgeGradient: Boolean
        get() = getPrefs().getBoolean(
            KEY_IMMERSIVE_ALBUM_EDGE_GRADIENT,
            DEFAULT_IMMERSIVE_ALBUM_EDGE_GRADIENT,
        )
        set(value) = getPrefs().edit().putBoolean(KEY_IMMERSIVE_ALBUM_EDGE_GRADIENT, value).apply()

    /** 锁屏封面基底：big_album / immersive / magazine */
    var lockscreenChrome: String
        get() {
            val raw = getPrefs().getString(KEY_LOCKSCREEN_CHROME, null)
            if (!raw.isNullOrBlank()) return raw
            return if (immersiveAlbum) CHROME_IMMERSIVE else CHROME_BIG_ALBUM
        }
        set(value) = getPrefs().edit().putString(KEY_LOCKSCREEN_CHROME, value).apply()

    /** 退出画报后回到的普通样式（大专辑 / 沉浸）。 */
    var lastNormalChrome: String
        get() {
            val raw = getPrefs().getString(KEY_LAST_NORMAL_CHROME, null)
            return when (raw) {
                CHROME_IMMERSIVE -> CHROME_IMMERSIVE
                else -> CHROME_BIG_ALBUM
            }
        }
        set(value) {
            val normalized = if (value == CHROME_IMMERSIVE) CHROME_IMMERSIVE else CHROME_BIG_ALBUM
            getPrefs().edit().putString(KEY_LAST_NORMAL_CHROME, normalized).apply()
        }

    /**
     * 画报右划 [MagazineMusicActivity] 的封面样式；不影响锁屏本体。
     * 未设置时回落到 [lastNormalChrome]。
     */
    var magazinePageChrome: String
        get() {
            val raw = getPrefs().getString(KEY_MAGAZINE_PAGE_CHROME, null)
            return when {
                raw == CHROME_IMMERSIVE -> CHROME_IMMERSIVE
                raw == CHROME_BIG_ALBUM -> CHROME_BIG_ALBUM
                else -> lastNormalChrome
            }
        }
        set(value) {
            val normalized = if (value == CHROME_IMMERSIVE) CHROME_IMMERSIVE else CHROME_BIG_ALBUM
            getPrefs().edit().putString(KEY_MAGAZINE_PAGE_CHROME, normalized).apply()
        }

    val isMagazineMode: Boolean
        get() = lockscreenChrome == CHROME_MAGAZINE

    // ---------- 画报页独立读写（未写入前回落普通档案当前值） ----------

    var magazineBlurRadius: Float
        get() = magazineFloat(KEY_MAGAZINE_BLUR_RADIUS, blurRadius)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_BLUR_RADIUS, value).apply()

    var magazineDarkOverlay: Int
        get() = magazineInt(KEY_MAGAZINE_DARK_OVERLAY, darkOverlay)
        set(value) = getPrefs().edit().putInt(KEY_MAGAZINE_DARK_OVERLAY, value).apply()

    var magazineAlbumSize: Float
        get() = magazineFloat(KEY_MAGAZINE_ALBUM_SIZE, albumSize)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_ALBUM_SIZE, value).apply()

    var magazineAlbumAnchorY: Float
        get() = magazineFloat(KEY_MAGAZINE_ALBUM_OFFSET_Y, albumAnchorY)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_ALBUM_OFFSET_Y, value).apply()

    var magazineAlbumCorner: Float
        get() = magazineFloat(KEY_MAGAZINE_ALBUM_CORNER, albumCorner)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_ALBUM_CORNER, value).apply()

    var magazineAlbumNetworkHd: Boolean
        get() = magazineBool(KEY_MAGAZINE_ALBUM_NETWORK_HD, albumNetworkHd)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_ALBUM_NETWORK_HD, value).apply()

    var magazineImmersiveAlbumCenterY: Float
        get() = magazineFloat(KEY_MAGAZINE_IMMERSIVE_ALBUM_CENTER_Y, immersiveAlbumCenterY)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_IMMERSIVE_ALBUM_CENTER_Y, value).apply()

    var magazineImmersiveAlbumEdgeGradient: Boolean
        get() = magazineBool(KEY_MAGAZINE_IMMERSIVE_ALBUM_EDGE_GRADIENT, immersiveAlbumEdgeGradient)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_IMMERSIVE_ALBUM_EDGE_GRADIENT, value).apply()

    var magazineShowLyric: Boolean
        get() = magazineBool(KEY_MAGAZINE_SHOW_LYRIC, showLyric)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_SHOW_LYRIC, value).apply()

    var magazineLyricSize: Float
        get() = magazineFloat(KEY_MAGAZINE_LYRIC_SIZE, lyricSize)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_LYRIC_SIZE, value).apply()

    var magazineSwapLyric: Boolean
        get() = magazineBool(KEY_MAGAZINE_SWAP_LYRIC, swapLyric)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_SWAP_LYRIC, value).apply()

    var magazineLyricWidth: Float
        get() = magazineFloat(KEY_MAGAZINE_LYRIC_WIDTH, lyricWidth)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_LYRIC_WIDTH, value).apply()

    var magazineLyricBgAnchorY: Float
        get() = magazineFloat(KEY_MAGAZINE_LYRIC_BG_ANCHOR_Y, lyricBgAnchorY)
        set(value) = getPrefs().edit().putFloat(KEY_MAGAZINE_LYRIC_BG_ANCHOR_Y, value).apply()

    var magazineImmersiveLyric: Boolean
        get() = magazineBool(KEY_MAGAZINE_IMMERSIVE_LYRIC, immersiveLyric)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_IMMERSIVE_LYRIC, value).apply()

    var magazineLyricHideBackground: Boolean
        get() = magazineBool(KEY_MAGAZINE_LYRIC_HIDE_BACKGROUND, lyricHideBackground)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_LYRIC_HIDE_BACKGROUND, value).apply()

    var magazineLyricAlign: String
        get() = magazineString(KEY_MAGAZINE_LYRIC_ALIGN, lyricAlign)
        set(value) = getPrefs().edit().putString(KEY_MAGAZINE_LYRIC_ALIGN, value).apply()

    var magazineLyricTransition: String
        get() = magazineString(KEY_MAGAZINE_LYRIC_TRANSITION, lyricTransition)
        set(value) = getPrefs().edit().putString(KEY_MAGAZINE_LYRIC_TRANSITION, value).apply()

    var magazineImmersiveLyricStack: Boolean
        get() = magazineBool(KEY_MAGAZINE_IMMERSIVE_LYRIC_STACK, immersiveLyricStack)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_IMMERSIVE_LYRIC_STACK, value).apply()

    var magazineKeepLockScreenOn: Boolean
        get() = magazineBool(KEY_MAGAZINE_KEEP_LOCKSCREEN_ON, keepLockScreenOn)
        set(value) = getPrefs().edit().putBoolean(KEY_MAGAZINE_KEEP_LOCKSCREEN_ON, value).apply()

    var magazineTitleBracketMode: String
        get() = magazineString(KEY_MAGAZINE_TITLE_BRACKET_MODE, titleBracketMode)
        set(value) = getPrefs().edit().putString(KEY_MAGAZINE_TITLE_BRACKET_MODE, value).apply()

    /** 设置页：按是否画报模式读写对应档案 */
    var editBlurRadius: Float
        get() = if (isMagazineMode) magazineBlurRadius else blurRadius
        set(value) { if (isMagazineMode) magazineBlurRadius = value else blurRadius = value }

    var editDarkOverlay: Int
        get() = if (isMagazineMode) magazineDarkOverlay else darkOverlay
        set(value) { if (isMagazineMode) magazineDarkOverlay = value else darkOverlay = value }

    var editAlbumSize: Float
        get() = if (isMagazineMode) magazineAlbumSize else albumSize
        set(value) { if (isMagazineMode) magazineAlbumSize = value else albumSize = value }

    var editAlbumAnchorY: Float
        get() = if (isMagazineMode) magazineAlbumAnchorY else albumAnchorY
        set(value) { if (isMagazineMode) magazineAlbumAnchorY = value else albumAnchorY = value }

    var editAlbumCorner: Float
        get() = if (isMagazineMode) magazineAlbumCorner else albumCorner
        set(value) { if (isMagazineMode) magazineAlbumCorner = value else albumCorner = value }

    var editAlbumNetworkHd: Boolean
        get() = if (isMagazineMode) magazineAlbumNetworkHd else albumNetworkHd
        set(value) { if (isMagazineMode) magazineAlbumNetworkHd = value else albumNetworkHd = value }

    var editImmersiveAlbumCenterY: Float
        get() = if (isMagazineMode) magazineImmersiveAlbumCenterY else immersiveAlbumCenterY
        set(value) {
            if (isMagazineMode) magazineImmersiveAlbumCenterY = value else immersiveAlbumCenterY = value
        }

    var editImmersiveAlbumEdgeGradient: Boolean
        get() = if (isMagazineMode) magazineImmersiveAlbumEdgeGradient else immersiveAlbumEdgeGradient
        set(value) {
            if (isMagazineMode) magazineImmersiveAlbumEdgeGradient = value
            else immersiveAlbumEdgeGradient = value
        }

    var editShowLyric: Boolean
        get() = if (isMagazineMode) magazineShowLyric else showLyric
        set(value) { if (isMagazineMode) magazineShowLyric = value else showLyric = value }

    var editLyricSize: Float
        get() = if (isMagazineMode) magazineLyricSize else lyricSize
        set(value) { if (isMagazineMode) magazineLyricSize = value else lyricSize = value }

    var editSwapLyric: Boolean
        get() = if (isMagazineMode) magazineSwapLyric else swapLyric
        set(value) { if (isMagazineMode) magazineSwapLyric = value else swapLyric = value }

    var editLyricWidth: Float
        get() = if (isMagazineMode) magazineLyricWidth else lyricWidth
        set(value) { if (isMagazineMode) magazineLyricWidth = value else lyricWidth = value }

    var editLyricBgAnchorY: Float
        get() = if (isMagazineMode) magazineLyricBgAnchorY else lyricBgAnchorY
        set(value) { if (isMagazineMode) magazineLyricBgAnchorY = value else lyricBgAnchorY = value }

    var editImmersiveLyric: Boolean
        get() = if (isMagazineMode) magazineImmersiveLyric else immersiveLyric
        set(value) { if (isMagazineMode) magazineImmersiveLyric = value else immersiveLyric = value }

    var editLyricHideBackground: Boolean
        get() = if (isMagazineMode) magazineLyricHideBackground else lyricHideBackground
        set(value) {
            if (isMagazineMode) magazineLyricHideBackground = value else lyricHideBackground = value
        }

    var editLyricAlign: String
        get() = if (isMagazineMode) magazineLyricAlign else lyricAlign
        set(value) { if (isMagazineMode) magazineLyricAlign = value else lyricAlign = value }

    var editLyricTransition: String
        get() = if (isMagazineMode) magazineLyricTransition else lyricTransition
        set(value) { if (isMagazineMode) magazineLyricTransition = value else lyricTransition = value }

    var editImmersiveLyricStack: Boolean
        get() = if (isMagazineMode) magazineImmersiveLyricStack else immersiveLyricStack
        set(value) {
            if (isMagazineMode) magazineImmersiveLyricStack = value else immersiveLyricStack = value
        }

    var editKeepLockScreenOn: Boolean
        get() = if (isMagazineMode) magazineKeepLockScreenOn else keepLockScreenOn
        set(value) {
            if (isMagazineMode) magazineKeepLockScreenOn = value else keepLockScreenOn = value
        }

    var editTitleBracketMode: String
        get() = if (isMagazineMode) magazineTitleBracketMode else titleBracketMode
        set(value) {
            if (isMagazineMode) magazineTitleBracketMode = value else titleBracketMode = value
        }

    private fun magazineFloat(key: String, fallback: Float): Float {
        val p = getPrefs()
        return com.leowalk.musiclockscreen.xposed.DualModeSettingsPolicy.resolveStoredOrFallback(
            magazineKeyPresent = p.contains(key),
            magazineValue = p.getFloat(key, fallback),
            normalValue = fallback,
        )
    }

    private fun magazineInt(key: String, fallback: Int): Int {
        val p = getPrefs()
        return if (p.contains(key)) p.getInt(key, fallback) else fallback
    }

    private fun magazineBool(key: String, fallback: Boolean): Boolean {
        val p = getPrefs()
        return com.leowalk.musiclockscreen.xposed.DualModeSettingsPolicy.resolveStoredOrFallback(
            magazineKeyPresent = p.contains(key),
            magazineValue = p.getBoolean(key, fallback),
            normalValue = fallback,
        )
    }

    private fun magazineString(key: String, fallback: String): String {
        val p = getPrefs()
        return com.leowalk.musiclockscreen.xposed.DualModeSettingsPolicy.resolveStoredOrFallback(
            magazineKeyPresent = p.contains(key),
            magazineValue = p.getString(key, fallback) ?: fallback,
            normalValue = fallback,
        )
    }

    /**
     * 主界面：普通模式 ↔ 画报模式。
     * 画报开启前记下当前普通样式，关闭时还原。
     */
    fun setMagazineMode(enabled: Boolean) {
        if (enabled) {
            if (lockscreenChrome != CHROME_MAGAZINE) {
                lastNormalChrome = lockscreenChrome
            }
            applyChromeStyle(CHROME_MAGAZINE)
        } else {
            applyChromeStyle(lastNormalChrome)
        }
    }

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
     * 画报模式写 magazine_*，普通模式写锁屏档案。
     */
    fun applyAlbumLyricBinding(immersiveAlbumOn: Boolean) {
        val d = com.leowalk.musiclockscreen.xposed.AlbumLyricBindingPolicy
            .defaultsForImmersiveAlbum(immersiveAlbumOn)
        if (isMagazineMode) {
            magazineImmersiveLyric = d.immersiveLyric
            magazineLyricHideBackground = d.lyricHideBackground
        } else {
            immersiveLyric = d.immersiveLyric
            lyricHideBackground = d.lyricHideBackground
        }
    }

    /**
     * 画报页封面样式切换：写 [magazinePageChrome] 并套用专辑↔歌词默认绑定。
     */
    fun applyMagazinePageChrome(chrome: String) {
        val normalized = if (chrome == CHROME_IMMERSIVE) CHROME_IMMERSIVE else CHROME_BIG_ALBUM
        magazinePageChrome = normalized
        applyAlbumLyricBinding(immersiveAlbumOn = normalized == CHROME_IMMERSIVE)
    }

    /**
     * 普通模式封面基底：大专辑 / 沉浸封面；画报由主界面模式开关控制。
     * 画报：关方形专辑与沉浸烘焙，普通歌词 + 无背景（Overlay 补动画）。
     */
    fun applyChromeStyle(chrome: String) {
        when (chrome) {
            CHROME_MAGAZINE -> {
                lockscreenChrome = CHROME_MAGAZINE
                showBigAlbum = false
                immersiveAlbum = false
                // 不改普通歌词档案；画报页歌词用 magazine_* 独立键
            }
            CHROME_IMMERSIVE -> {
                lockscreenChrome = CHROME_IMMERSIVE
                lastNormalChrome = CHROME_IMMERSIVE
                showBigAlbum = true
                immersiveAlbum = true
                applyAlbumLyricBinding(true)
            }
            else -> {
                lockscreenChrome = CHROME_BIG_ALBUM
                lastNormalChrome = CHROME_BIG_ALBUM
                showBigAlbum = true
                immersiveAlbum = false
                applyAlbumLyricBinding(false)
            }
        }
    }

    /** AOD 完整媒体控件（展开 + 进度） */
    var aodFullMedia: Boolean
        get() = getPrefs().getBoolean(KEY_AOD_FULL_MEDIA, DEFAULT_AOD_FULL_MEDIA)
        set(value) = getPrefs().edit().putBoolean(KEY_AOD_FULL_MEDIA, value).apply()

    /** 音乐锁屏息屏时禁用 HyperOS 壁纸缩放动画（保留压暗） */
    var disableWallpaperScale: Boolean
        get() = getPrefs().getBoolean(KEY_DISABLE_WALLPAPER_SCALE, DEFAULT_DISABLE_WALLPAPER_SCALE)
        set(value) = getPrefs().edit().putBoolean(KEY_DISABLE_WALLPAPER_SCALE, value).apply()

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
                put("lyric_enabled", if (lyricEnabled) 1 else 0)
                put("show_lyric", if (showLyric) 1 else 0)
                put("lyric_size", lyricSize)
                put("swap_lyric", if (swapLyric) 1 else 0)
                put("lyric_width", lyricWidth)
                put("lyric_bg_offset_y", lyricBgOffsetY)
                put("lyric_bg_anchor_y", lyricBgAnchorY)
                put("immersive_lyric", if (immersiveLyric) 1 else 0)
                put("lyric_hide_background", if (lyricHideBackground) 1 else 0)
                put("lyric_align", lyricAlign)
                put("lyric_transition", lyricTransition)
                put("immersive_lyric_stack", if (immersiveLyricStack) 1 else 0)
                put("immersive_album", if (immersiveAlbum) 1 else 0)
                put("immersive_album_center_y", immersiveAlbumCenterY)
                put("immersive_album_edge_gradient", if (immersiveAlbumEdgeGradient) 1 else 0)
                put("lockscreen_chrome", lockscreenChrome)
                put("minimal_clock", if (minimalClock) 1 else 0)
                put("minimal_clock_size", minimalClockSize)
                put("minimal_clock_top_y", minimalClockTopY)
                put("aod_full_media", if (aodFullMedia) 1 else 0)
                put("disable_wallpaper_scale", if (disableWallpaperScale) 1 else 0)
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
