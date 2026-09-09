package com.leowalk.musiclockscreen.xposed

/**
 * 画报右划页歌词：开关与时间轴索引（纯函数，便于单测）。
 */
internal object MagazinePageLyricPolicy {

    fun shouldShowLyric(lyricEnabled: Boolean, showLyric: Boolean): Boolean =
        LyricDisplayPolicy.shouldShowLyric(lyricEnabled, showLyric)

    data class TimedLine(val timeMs: Long, val text: String, val translation: String = "")

    /** 二分：最后一条 time <= pos；pos 在首句前返回 -1。 */
    fun findLineIndex(lines: List<TimedLine>, posMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= posMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun resolveDisplayIndex(lines: List<TimedLine>, posMs: Long): Int =
        AodLyricDisplayPolicy.clampLyricLineIndex(findLineIndex(lines, posMs), lines.size)
}
