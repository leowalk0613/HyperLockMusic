package com.leowalk.musiclockscreen.xposed

/** 歌词底边 pin 与配置锚点一致性（纯函数，供 MediaFollow / LyricView 共用判定）。 */
internal object MediaFollowLyricPinPolicy {

    fun shouldClearPinOnAnchorChange(pinnedAnchorPercent: Float, configuredPercent: Float): Boolean {
        if (pinnedAnchorPercent.isNaN()) return true
        return kotlin.math.abs(pinnedAnchorPercent - configuredPercent) > 0.05f
    }
}
