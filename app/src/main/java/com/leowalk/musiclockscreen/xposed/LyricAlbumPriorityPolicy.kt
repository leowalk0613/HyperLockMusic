package com.leowalk.musiclockscreen.xposed

/**
 * 歌词开关开启时：有歌词（或切歌等待新歌词）优先占专辑位，无歌词才显示方形专辑。
 */
internal object LyricAlbumPriorityPolicy {

    fun shouldHideSquareAlbum(
        showLyricEnabled: Boolean,
        musicLockscreenActive: Boolean,
        onKeyguard: Boolean,
        lyricCurrentlyDisplayed: Boolean,
        awaitingFreshLyricsAfterTrackSwitch: Boolean,
        hasLyricData: Boolean,
        hasDisplayableText: Boolean,
    ): Boolean {
        if (!showLyricEnabled || !musicLockscreenActive || !onKeyguard) return false
        if (lyricCurrentlyDisplayed) return true
        if (awaitingFreshLyricsAfterTrackSwitch) return true
        if (hasLyricData && hasDisplayableText) return true
        return false
    }
}
