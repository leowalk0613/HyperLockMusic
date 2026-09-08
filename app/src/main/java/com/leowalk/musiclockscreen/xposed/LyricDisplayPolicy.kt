package com.leowalk.musiclockscreen.xposed

/**
 * 歌词总开关 [lyricEnabled] 与显示开关 [showLyric] 分离：
 * - 主界面总开关关闭时，整个歌词功能不可用，锁屏媒体按钮不能重新开启；
 * - 设置页 / 锁屏按钮的「显示歌词」仅控制 [showLyric]。
 */
object LyricDisplayPolicy {

    fun shouldShowLyric(lyricEnabled: Boolean, showLyric: Boolean): Boolean {
        return lyricEnabled && showLyric
    }

    /**
     * 进入音乐锁屏时：即使 show_lyric 相对内存未变，也要强制 bootstrap。
     * 配置 observer 只在值边沿重拉，默认已开启时进屏不会触发。
     */
    fun shouldForceLyricBootstrapOnEnter(
        lyricEnabled: Boolean,
        showLyric: Boolean,
        musicLockscreenActive: Boolean,
    ): Boolean {
        return musicLockscreenActive && shouldShowLyric(lyricEnabled, showLyric)
    }
}
