package com.leowalk.musiclockscreen.xposed

/**
 * 画报大专辑页：沉浸歌词占专辑槽时，壁纸烘焙不叠前景方形专辑。
 * 对标锁屏：[isLyricPriorityOverAlbum]（仅沉浸）+ [LyricAlbumPriorityPolicy] 播放/暂停/等词。
 * 普通歌词独立锚点，不藏大专辑。
 * 确认无词 / 关歌词 / 暂停后 [hideAlbumForLyricPriority]=false，须恢复前景专辑。
 */
internal object MagazinePageAlbumSlotPolicy {

    /**
     * 是否在模糊底上烘焙前景大专辑。
     * @param immersiveLyric 画报是否为沉浸歌词（普通歌词恒为 false 侧）
     * @param hideAlbumForLyricPriority 沉浸歌词优先占槽（含切歌 WAITING）；无词后为 false
     */
    fun shouldBakeBigAlbumForeground(
        pageStyle: String,
        immersiveLyric: Boolean,
        hideAlbumForLyricPriority: Boolean,
    ): Boolean {
        if (!MagazinePageStylePolicy.shouldShowForegroundAlbum(pageStyle)) return false
        if (immersiveLyric && hideAlbumForLyricPriority) return false
        return true
    }

    /** 无歌词占槽时必须烘焙大专辑（大专辑页）。 */
    fun shouldRestoreBigAlbumWhenNoLyric(
        pageStyle: String,
        hideAlbumForLyricPriority: Boolean,
    ): Boolean {
        if (!MagazinePageStylePolicy.shouldShowForegroundAlbum(pageStyle)) return false
        return !hideAlbumForLyricPriority
    }
}
