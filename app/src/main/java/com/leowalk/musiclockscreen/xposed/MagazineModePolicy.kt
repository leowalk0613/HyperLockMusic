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

    /** 激活期抑制左滑进真·画报 App。 */
    fun shouldSuppressMagazineLeftSwipe(
        chromeMagazine: Boolean,
        musicWallpaperShowing: Boolean,
    ): Boolean = chromeMagazine && musicWallpaperShowing

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
