package com.leowalk.musiclockscreen.xposed

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Settings

/**
 * 从 ContentProvider 读取配置
 *
 * SystemUI 进程通过 ContentProvider 读取模块配置
 */
object ConfigReader {

    private const val AUTHORITY = "com.leowalk.musiclockscreen.config"
    private const val TAG = "MusicLockScreen_Config"

    private var cachedShowBigAlbum: Boolean = true
    private var cachedBlurRadius: Float = 80f
    private var cachedDarkOverlay: Int = 140
    private var cachedAlbumSize: Float = 55f
    private var cachedAlbumOffsetY: Float = -80f
    private var cachedAlbumCorner: Float = 24f
    private var cachedShowLyric: Boolean = true
    private var cachedLyricWidth: Float = 100f
    private var cachedTitleBracketMode: String = "default"
    private var cachedWhitelistEnabled: Boolean = false
    private var cachedWhitelist: String = ""
    private var lastReadTime: Long = 0
    private const val CACHE_DURATION = 1000 // 缓存 1 秒

    private const val KEY_WALLPAPER_ACTIVE = "music_lockscreen_wallpaper_active"

    private val configUri: Uri = Uri.parse("content://$AUTHORITY/config")

    /**
     * 音乐壁纸激活标记（持久化到 Settings.Secure）。用于区分"音乐壁纸是否处于激活状态"：
     *   - 开启音乐锁屏时置 true
     *   - 恢复原壁纸（按钮关闭）时置 false
     * 目的是判定何时当前锁屏壁纸可能是"残留音乐壁纸"（激活态=true 时绝不能缓存/捕获原图）。
     */
    fun setWallpaperActive(context: Context, active: Boolean) {
        try {
            Settings.Secure.putInt(
                context.contentResolver,
                KEY_WALLPAPER_ACTIVE,
                if (active) 1 else 0
            )
        } catch (_: Throwable) {
        }
    }

    fun isWallpaperActive(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, KEY_WALLPAPER_ACTIVE, 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun showBigAlbum(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedShowBigAlbum
    }

    fun blurRadius(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedBlurRadius
    }

    fun darkOverlay(context: Context): Int {
        refreshConfigIfNeeded(context)
        return cachedDarkOverlay
    }

    fun albumSize(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedAlbumSize
    }

    fun albumOffsetY(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedAlbumOffsetY
    }

    fun albumCorner(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedAlbumCorner
    }

    fun showLyric(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedShowLyric
    }

    /** 歌词区域宽度：占专辑宽度的百分比（默认 100 = 与专辑同宽） */
    fun lyricWidth(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedLyricWidth
    }

    fun titleBracketMode(context: Context): String {
        refreshConfigIfNeeded(context)
        return cachedTitleBracketMode
    }

    fun musicWhitelistEnabled(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedWhitelistEnabled
    }

    fun musicWhitelist(context: Context): String {
        refreshConfigIfNeeded(context)
        return cachedWhitelist
    }

    /** 白名单关闭时一律允许；开启时仅白名单内包名允许。 */
    fun isAllowedMusicApp(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return !musicWhitelistEnabled(context)
        if (!musicWhitelistEnabled(context)) return true
        return cachedWhitelist.split(',')
            .any { it.trim().equals(packageName, ignoreCase = true) }
    }

    fun setShowLyric(context: Context, show: Boolean): Boolean {
        return try {
            val values = ContentValues().apply {
                put("show_lyric", if (show) 1 else 0)
            }
            val updated = context.contentResolver.update(configUri, values, null, null)
            cachedShowLyric = show
            lastReadTime = System.currentTimeMillis()
            updated > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun refreshConfigIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastReadTime < CACHE_DURATION) return

        try {
            val cursor = context.contentResolver.query(configUri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val showBigAlbumIdx = cursor.getColumnIndex("show_big_album")
                val blurRadiusIdx = cursor.getColumnIndex("blur_radius")
                val darkOverlayIdx = cursor.getColumnIndex("dark_overlay")
                val albumSizeIdx = cursor.getColumnIndex("album_size")
                val albumOffsetYIdx = cursor.getColumnIndex("album_offset_y")
                val albumCornerIdx = cursor.getColumnIndex("album_corner")
                val showLyricIdx = cursor.getColumnIndex("show_lyric")
                val lyricWidthIdx = cursor.getColumnIndex("lyric_width")
                val titleBracketModeIdx = cursor.getColumnIndex("title_bracket_mode")
                val whitelistEnabledIdx = cursor.getColumnIndex("music_whitelist_enabled")
                val whitelistIdx = cursor.getColumnIndex("music_whitelist")

                if (showBigAlbumIdx >= 0) {
                    cachedShowBigAlbum = cursor.getInt(showBigAlbumIdx) == 1
                }
                if (blurRadiusIdx >= 0) {
                    cachedBlurRadius = cursor.getFloat(blurRadiusIdx)
                }
                if (darkOverlayIdx >= 0) {
                    cachedDarkOverlay = cursor.getInt(darkOverlayIdx)
                }
                if (albumSizeIdx >= 0) {
                    cachedAlbumSize = cursor.getFloat(albumSizeIdx)
                }
                if (albumOffsetYIdx >= 0) {
                    cachedAlbumOffsetY = cursor.getFloat(albumOffsetYIdx)
                }
                if (albumCornerIdx >= 0) {
                    cachedAlbumCorner = cursor.getFloat(albumCornerIdx)
                }
                if (showLyricIdx >= 0) {
                    cachedShowLyric = cursor.getInt(showLyricIdx) == 1
                }
                if (lyricWidthIdx >= 0) {
                    cachedLyricWidth = cursor.getFloat(lyricWidthIdx)
                }
                if (titleBracketModeIdx >= 0) {
                    cachedTitleBracketMode = cursor.getString(titleBracketModeIdx) ?: "default"
                }
                if (whitelistEnabledIdx >= 0) {
                    cachedWhitelistEnabled = cursor.getInt(whitelistEnabledIdx) == 1
                }
                if (whitelistIdx >= 0) {
                    cachedWhitelist = cursor.getString(whitelistIdx) ?: ""
                }
                cursor.close()
            }
            lastReadTime = now
        } catch (e: Throwable) {
            // 读取失败，用默认值
        }
    }
}
