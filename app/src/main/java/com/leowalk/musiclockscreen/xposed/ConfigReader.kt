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
    private const val TAG = "HyperLockMusic_Config"

    private var cachedShowBigAlbum: Boolean = true
    private var cachedBlurRadius: Float = 80f
    private var cachedDarkOverlay: Int = 140
    private var cachedAlbumSize: Float = 55f
    private var cachedAlbumOffsetY: Float = 55f
    private var cachedAlbumCorner: Float = 24f
    private var cachedAlbumNetworkHd: Boolean = false
    private var cachedLyricEnabled: Boolean = true
    private var cachedShowLyric: Boolean = true
    private var cachedLyricWidth: Float = 55f
    private var cachedLyricBgOffsetY: Float = 12f
    private var cachedLyricBgAnchorY: Float = 62f
    private var cachedImmersiveLyric: Boolean = true
    private var cachedLyricHideBackground: Boolean = false
    private var cachedLyricAlign: String = "left"
    private var cachedKeepLockScreenOn: Boolean = false
    private var cachedImmersiveAlbum: Boolean = false
    private var cachedImmersiveAlbumCenterY: Float = 38f
    private var cachedImmersiveAlbumEdgeGradient: Boolean = true
    private var cachedMinimalClock: Boolean = true
    private var cachedMinimalClockSize: Float = 30f
    private var cachedMinimalClockTopY: Float = 10f
    private var cachedTitleBracketMode: String = "default"
    private var cachedAodFullMedia: Boolean = true
    private var cachedDisableWallpaperScale: Boolean = true
    private var cachedWhitelistEnabled: Boolean = false
    private var cachedWhitelist: String = ""
    private var cachedMediaListenerReady: Boolean = false
    private var cachedMediaPlaybackActive: Boolean = false
    private var lastReadTime: Long = 0
    private const val CACHE_DURATION = 1000 // 缓存 1 秒
    /** 播放状态信号缓存更短，便于杀 App 后尽快退出音乐锁屏 */
    private const val MEDIA_CACHE_DURATION = 400
    private var lastMediaReadTime: Long = 0

    /** 强制下次读取走 ContentProvider（配置变更后调用） */
    fun invalidate() {
        lastReadTime = 0
        lastMediaReadTime = 0
    }

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

    /** 专辑底边占屏幕高度百分比（大专辑 overlay / 沉浸歌词区块） */
    fun albumAnchorY(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedAlbumOffsetY.coerceIn(10f, 95f)
    }

    /** @deprecated 同 [albumAnchorY] */
    fun albumOffsetY(context: Context): Float = albumAnchorY(context)

    /** 沉浸封面竖直中心占屏高百分比（仅壁纸烘焙，与 [albumAnchorY] 独立） */
    fun immersiveAlbumCenterY(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedImmersiveAlbumCenterY.coerceIn(18f, 70f)
    }

    /** 沉浸壁纸上下沿暗色渐变 */
    fun immersiveAlbumEdgeGradient(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedImmersiveAlbumEdgeGradient
    }

    fun albumCorner(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedAlbumCorner
    }

    /** 是否后台拉网易云高清替换前景专辑 */
    fun albumNetworkHd(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedAlbumNetworkHd
    }

    fun lyricEnabled(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedLyricEnabled
    }

    fun showLyric(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedShowLyric
    }

    fun shouldShowLyric(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return LyricDisplayPolicy.shouldShowLyric(cachedLyricEnabled, cachedShowLyric)
    }

    /** 歌词区域宽度：占屏幕宽度的百分比 */
    fun lyricWidth(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedLyricWidth
    }

    /** 歌词底边占屏幕高度百分比 */
    fun lyricBgAnchorY(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedLyricBgAnchorY.coerceIn(10f, 95f)
    }

    fun immersiveLyric(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedImmersiveLyric
    }

    fun lyricHideBackground(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedLyricHideBackground
    }

    fun lyricAlign(context: Context): String {
        refreshConfigIfNeeded(context)
        return cachedLyricAlign
    }

    fun keepLockScreenOn(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedKeepLockScreenOn
    }

    fun immersiveAlbum(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedImmersiveAlbum
    }

    /** 音乐锁屏简洁时钟：隐藏系统大时钟，顶部显示一行时间日期 */
    fun minimalClock(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedMinimalClock
    }

    /** 简洁时钟字号（sp） */
    fun minimalClockSize(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedMinimalClockSize.coerceIn(16f, 48f)
    }

    /** 简洁时钟顶边占屏高百分比 */
    fun minimalClockTopY(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedMinimalClockTopY.coerceIn(2f, 25f)
    }

    /** 方形专辑 overlay 是否应显示 */
    fun shouldShowSquareAlbum(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        if (!cachedShowBigAlbum) return false
        if (cachedImmersiveAlbum) return false
        if (cachedShowLyric && MusicLockscreenManager.isLyricPriorityOverAlbum()) {
            return false
        }
        return true
    }

    /** 沉浸专辑是否应合成进壁纸（与沉浸歌词显隐无关） */
    fun shouldBakeImmersiveAlbumInWallpaper(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedShowBigAlbum && cachedImmersiveAlbum
    }

    /** @deprecated 沉浸专辑已合成进壁纸，overlay 不再使用 */
    fun shouldShowImmersiveAlbum(context: Context): Boolean = false

    /** @deprecated 请用 [lyricBgAnchorY] */
    fun lyricBgOffsetY(context: Context): Float {
        refreshConfigIfNeeded(context)
        return cachedLyricBgOffsetY
    }

    fun titleBracketMode(context: Context): String {
        refreshConfigIfNeeded(context)
        return cachedTitleBracketMode
    }

    /** AOD 时完整显示媒体控件并实时更新进度条 */
    fun aodFullMedia(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedAodFullMedia
    }

    /** 音乐锁屏息屏时禁用壁纸缩放动画 */
    fun disableWallpaperScale(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedDisableWallpaperScale
    }

    fun musicWhitelistEnabled(context: Context): Boolean {
        refreshConfigIfNeeded(context)
        return cachedWhitelistEnabled
    }

    fun musicWhitelist(context: Context): String {
        refreshConfigIfNeeded(context)
        return cachedWhitelist
    }

    /**
     * 通知使用权 Listener 是否已连接并上报过状态。
     * 未就绪时不应把「无上报」当成「无播放」。
     */
    fun mediaListenerReady(context: Context): Boolean {
        refreshMediaPlaybackIfNeeded(context)
        return cachedMediaListenerReady
    }

    /** Listener 判定：是否仍有音乐相关活跃会话（含暂停）。 */
    fun mediaPlaybackActive(context: Context): Boolean {
        refreshMediaPlaybackIfNeeded(context)
        return cachedMediaPlaybackActive
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
                val albumNetworkHdIdx = cursor.getColumnIndex("album_sr_enhance")
                val lyricEnabledIdx = cursor.getColumnIndex("lyric_enabled")
                val showLyricIdx = cursor.getColumnIndex("show_lyric")
                val lyricWidthIdx = cursor.getColumnIndex("lyric_width")
                val lyricBgOffsetYIdx = cursor.getColumnIndex("lyric_bg_offset_y")
                val lyricBgAnchorYIdx = cursor.getColumnIndex("lyric_bg_anchor_y")
                val immersiveLyricIdx = cursor.getColumnIndex("immersive_lyric")
                val lyricHideBackgroundIdx = cursor.getColumnIndex("lyric_hide_background")
                val lyricAlignIdx = cursor.getColumnIndex("lyric_align")
                val keepLockScreenOnIdx = cursor.getColumnIndex("keep_lockscreen_on")
                val immersiveAlbumIdx = cursor.getColumnIndex("immersive_album")
                val immersiveAlbumCenterYIdx = cursor.getColumnIndex("immersive_album_center_y")
                val immersiveAlbumEdgeGradientIdx = cursor.getColumnIndex("immersive_album_edge_gradient")
                val minimalClockIdx = cursor.getColumnIndex("minimal_clock")
                val minimalClockSizeIdx = cursor.getColumnIndex("minimal_clock_size")
                val minimalClockTopYIdx = cursor.getColumnIndex("minimal_clock_top_y")
                val titleBracketModeIdx = cursor.getColumnIndex("title_bracket_mode")
                val aodFullMediaIdx = cursor.getColumnIndex("aod_full_media")
                val disableWallpaperScaleIdx = cursor.getColumnIndex("disable_wallpaper_scale")
                val whitelistEnabledIdx = cursor.getColumnIndex("music_whitelist_enabled")
                val whitelistIdx = cursor.getColumnIndex("music_whitelist")
                val mediaListenerReadyIdx = cursor.getColumnIndex("media_listener_ready")
                val mediaPlaybackActiveIdx = cursor.getColumnIndex("media_playback_active")

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
                if (albumNetworkHdIdx >= 0) {
                    cachedAlbumNetworkHd = cursor.getInt(albumNetworkHdIdx) == 1
                }
                if (lyricEnabledIdx >= 0) {
                    cachedLyricEnabled = cursor.getInt(lyricEnabledIdx) == 1
                }
                if (showLyricIdx >= 0) {
                    cachedShowLyric = cursor.getInt(showLyricIdx) == 1
                }
                if (lyricWidthIdx >= 0) {
                    cachedLyricWidth = cursor.getFloat(lyricWidthIdx)
                }
                if (lyricBgOffsetYIdx >= 0) {
                    cachedLyricBgOffsetY = cursor.getFloat(lyricBgOffsetYIdx)
                }
                if (lyricBgAnchorYIdx >= 0) {
                    cachedLyricBgAnchorY = cursor.getFloat(lyricBgAnchorYIdx)
                }
                if (immersiveLyricIdx >= 0) {
                    cachedImmersiveLyric = cursor.getInt(immersiveLyricIdx) == 1
                }
                if (lyricHideBackgroundIdx >= 0) {
                    cachedLyricHideBackground = cursor.getInt(lyricHideBackgroundIdx) == 1
                }
                if (lyricAlignIdx >= 0) {
                    cachedLyricAlign = cursor.getString(lyricAlignIdx) ?: "left"
                }
                if (keepLockScreenOnIdx >= 0) {
                    cachedKeepLockScreenOn = cursor.getInt(keepLockScreenOnIdx) == 1
                }
                if (immersiveAlbumIdx >= 0) {
                    cachedImmersiveAlbum = cursor.getInt(immersiveAlbumIdx) == 1
                }
                if (immersiveAlbumCenterYIdx >= 0) {
                    cachedImmersiveAlbumCenterY = cursor.getFloat(immersiveAlbumCenterYIdx)
                }
                if (immersiveAlbumEdgeGradientIdx >= 0) {
                    cachedImmersiveAlbumEdgeGradient = cursor.getInt(immersiveAlbumEdgeGradientIdx) == 1
                }
                if (minimalClockIdx >= 0) {
                    cachedMinimalClock = cursor.getInt(minimalClockIdx) == 1
                }
                if (minimalClockSizeIdx >= 0) {
                    cachedMinimalClockSize = cursor.getFloat(minimalClockSizeIdx)
                }
                if (minimalClockTopYIdx >= 0) {
                    cachedMinimalClockTopY = cursor.getFloat(minimalClockTopYIdx)
                }
                if (titleBracketModeIdx >= 0) {
                    cachedTitleBracketMode = cursor.getString(titleBracketModeIdx) ?: "default"
                }
                if (aodFullMediaIdx >= 0) {
                    cachedAodFullMedia = cursor.getInt(aodFullMediaIdx) == 1
                }
                if (disableWallpaperScaleIdx >= 0) {
                    cachedDisableWallpaperScale = cursor.getInt(disableWallpaperScaleIdx) == 1
                }
                if (whitelistEnabledIdx >= 0) {
                    cachedWhitelistEnabled = cursor.getInt(whitelistEnabledIdx) == 1
                }
                if (whitelistIdx >= 0) {
                    cachedWhitelist = cursor.getString(whitelistIdx) ?: ""
                }
                if (mediaListenerReadyIdx >= 0) {
                    cachedMediaListenerReady = cursor.getInt(mediaListenerReadyIdx) == 1
                }
                if (mediaPlaybackActiveIdx >= 0) {
                    cachedMediaPlaybackActive = cursor.getInt(mediaPlaybackActiveIdx) == 1
                }
                cursor.close()
                lastReadTime = now
                lastMediaReadTime = now
            }
            // 读失败不刷新 lastReadTime，下次继续重试（避免重启后首帧锁死默认值）
        } catch (e: Throwable) {
            // 读取失败，用默认值并允许马上重试
        }
    }

    private fun refreshMediaPlaybackIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastMediaReadTime < MEDIA_CACHE_DURATION && lastMediaReadTime > 0) return
        // 复用全量刷新；媒体字段与其它配置同 cursor
        lastReadTime = 0
        refreshConfigIfNeeded(context)
    }
}
