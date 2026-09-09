package com.leowalk.musiclockscreen.xposed

/**
 * 画报（Magazine）样式策略：纯函数，便于单测。
 *
 * 基底走 SystemUI 杂志层；官方 title/content 不够时用 Overlay 歌词补齐。
 */
internal object MagazineModePolicy {

    const val CHROME_BIG_ALBUM = "big_album"
    const val CHROME_IMMERSIVE = "immersive"
    const val CHROME_MAGAZINE = "magazine"

    fun isMagazineChrome(chrome: String): Boolean =
        chrome == CHROME_MAGAZINE

    /** 音乐锁屏激活且为画报样式时，强制 [isMagazineWallpaper]=true。 */
    fun shouldForceMagazineWallpaper(
        chromeMagazine: Boolean,
        musicWallpaperShowing: Boolean,
    ): Boolean = chromeMagazine && musicWallpaperShowing

    /** 画报样式下不显示方形大专辑。 */
    fun shouldShowSquareAlbum(
        chromeMagazine: Boolean,
        showBigAlbum: Boolean,
        immersiveAlbum: Boolean,
        lyricPriorityHidesAlbum: Boolean,
    ): Boolean {
        if (chromeMagazine) return false
        if (!showBigAlbum) return false
        if (immersiveAlbum) return false
        if (lyricPriorityHidesAlbum) return false
        return true
    }

    /** 画报样式不做沉浸封面烘焙。 */
    fun shouldBakeImmersiveAlbum(
        chromeMagazine: Boolean,
        showBigAlbum: Boolean,
        immersiveAlbum: Boolean,
    ): Boolean {
        if (chromeMagazine) return false
        return showBigAlbum && immersiveAlbum
    }

    /** 画报底栏 Info 不够时，用 Overlay 歌词补动画/多行。 */
    fun shouldUseLyricOverlay(
        chromeMagazine: Boolean,
        lyricEnabled: Boolean,
        showLyric: Boolean,
    ): Boolean = chromeMagazine && lyricEnabled && showLyric

    /** 激活期抑制左滑进真·画报：已改为不抑制，保留 API 供兼容。 */
    fun shouldSuppressMagazineLeftSwipe(
        chromeMagazine: Boolean,
        musicWallpaperShowing: Boolean,
    ): Boolean = false

    /**
     * 画报模式：锁屏右划/左滑进画报与官方同路径，仅劫持目标 Activity。
     * 不依赖模块主动写壁纸（系统画报本身也是被动触发）。
     */
    fun shouldRedirectMagazineLeftSwipe(
        chromeMagazine: Boolean,
    ): Boolean = chromeMagazine

    /** @deprecated 请用单参数版本；保留兼容旧调用。 */
    fun shouldRedirectMagazineLeftSwipe(
        chromeMagazine: Boolean,
        musicWallpaperShowing: Boolean,
    ): Boolean = shouldRedirectMagazineLeftSwipe(chromeMagazine)

    /**
     * 画报模式与普通模式的差异：不隐藏通知、不改勿扰条、不改写媒体控件槽位。
     * （壁纸仍可走杂志层 / 自建划入页。）
     */
    fun shouldSkipNormalMusicChromeInterventions(chromeMagazine: Boolean): Boolean =
        chromeMagazine

    /** 画报模式下不主动藏通知。 */
    fun shouldHideNotificationsInMusicLockscreen(
        chromeMagazine: Boolean,
        musicWallpaperShowing: Boolean,
    ): Boolean = !chromeMagazine && musicWallpaperShowing

    /** 画报模式下音乐锁屏不隐藏「勿扰 | N 个通知」。 */
    fun shouldHideNumStateForMusicLockscreen(
        chromeMagazine: Boolean,
        musicWallpaperShowing: Boolean,
    ): Boolean = !chromeMagazine && musicWallpaperShowing

    /** 画报模式下不改写锁屏媒体 action 槽 / 不拦截系统 custom。 */
    fun shouldRewriteMediaControlSlots(chromeMagazine: Boolean): Boolean =
        !chromeMagazine

    /** 模块包名（与 applicationId 一致）。 */
    const val MODULE_PACKAGE = "com.leowalk.musiclockscreen"

    /** 官方画报包名（可卸载；模块模式不依赖其存在）。 */
    const val OFFICIAL_MAGAZINE_PACKAGE = "com.mfashiongallery.emag"

    /** 画报侧自建页（完整类名）。 */
    const val MAGAZINE_MUSIC_ACTIVITY =
        "com.leowalk.musiclockscreen.MagazineMusicActivity"

    /**
     * 遮罩动画校验：官方 emag，或画报模式下的模块包。
     * 对齐 KeyguardMagazineHelper.checkIsMagazineRemoteAnimation。
     */
    fun isMagazineRemoteAnimationPackage(
        packageName: String?,
        chromeMagazine: Boolean,
    ): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == OFFICIAL_MAGAZINE_PACKAGE) return true
        return chromeMagazine && packageName == MODULE_PACKAGE
    }

    /** 画报模式：把「emag 已安装」查询伪装为 true，卸载后仍可走右划入口。 */
    fun shouldSpoofMagazinePackageInstalled(
        chromeMagazine: Boolean,
        queriedPackage: String?,
    ): Boolean = chromeMagazine && queriedPackage == OFFICIAL_MAGAZINE_PACKAGE

    const val EXTRA_SONG_TITLE = "hyperlockmusic_song_title"
    const val EXTRA_SONG_ARTIST = "hyperlockmusic_song_artist"
    const val EXTRA_LYRIC_LINE = "hyperlockmusic_lyric_line"

    /**
     * 写入 LockScreenMagazineWallpaperInfo.ex 的 JSON。
     * title_customized=1 让 SystemUI 使用自定义 title。
     */
    fun buildMagazineExJson(
        entryText: String = "音乐",
        source: String = "",
        sourceColor: String = "#FFFFFF",
        linkType: Int = 0,
    ): String {
        fun esc(s: String): String = buildString(s.length + 8) {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
        return """{"title_customized":1,"lks_entry_text":"${esc(entryText)}","provider":"HyperLockMusic","source":"${esc(source)}","source_color":"${esc(sourceColor)}","link_type":"$linkType"}"""
    }

    fun resolveChrome(
        chrome: String,
        immersiveAlbum: Boolean,
    ): String {
        return when {
            chrome == CHROME_MAGAZINE -> CHROME_MAGAZINE
            chrome == CHROME_IMMERSIVE || (chrome.isBlank() && immersiveAlbum) -> CHROME_IMMERSIVE
            chrome == CHROME_BIG_ALBUM -> CHROME_BIG_ALBUM
            immersiveAlbum -> CHROME_IMMERSIVE
            else -> CHROME_BIG_ALBUM
        }
    }
}
