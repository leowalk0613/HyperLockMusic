package com.leowalk.musiclockscreen.xposed

/**
 * 切歌歌词门闩（AOD / 亮屏共用一套状态机）。
 *
 * ```
 * IDLE ──切歌──► WAITING
 *                  │
 *      有词且可信 ─┼─► IDLE + 显示歌词
 *      确认无词 / 超时 ─┼─► IDLE + 显示专辑
 * ```
 *
 * 「可信」= 标题对齐，或相对切歌快照 version bump / 歌词内容变化（应对 LyricFocus 标题滞后）。
 */
internal object TrackLyricGate {

    const val WAIT_TIMEOUT_MS = 2500L

    enum class Phase {
        IDLE,
        WAITING,
    }

    enum class Decision {
        /** 应用载荷为歌词并退出 WAITING */
        SHOW_LYRIC,
        /** 确认本曲无歌词（或等待超时），清歌词、退出 WAITING，让专辑出来 */
        SHOW_ALBUM,
        /** 忽略本包：WAITING 时保持空屏继续等；IDLE 时保留当前显示 */
        IGNORE,
    }

    data class Snapshot(
        val vLyric: Int,
        val vFd: Int,
        val lyricJson: String,
        val startedAtElapsedMs: Long,
    )

    data class Input(
        val phase: Phase,
        val snapshot: Snapshot?,
        val nowElapsedMs: Long,
        val vLyric: Int,
        val vFd: Int,
        val hasValidLines: Boolean,
        val titleMatchesMedia: Boolean,
        val contentChangedFromSwitchSnapshot: Boolean,
        val contentChangedFromCurrentDisplay: Boolean,
    )

    fun versionBumpedSinceSwitch(snapshot: Snapshot?, vLyric: Int, vFd: Int): Boolean {
        if (snapshot == null) return false
        return vLyric > snapshot.vLyric || vFd > snapshot.vFd
    }

    fun isWaitTimedOut(snapshot: Snapshot?, nowElapsedMs: Long): Boolean {
        if (snapshot == null) return false
        return nowElapsedMs - snapshot.startedAtElapsedMs >= WAIT_TIMEOUT_MS
    }

    fun decide(input: Input): Decision {
        val bumped = versionBumpedSinceSwitch(input.snapshot, input.vLyric, input.vFd)
        val timedOut = isWaitTimedOut(input.snapshot, input.nowElapsedMs)

        return when (input.phase) {
            Phase.WAITING -> decideWaiting(
                hasValidLines = input.hasValidLines,
                titleMatchesMedia = input.titleMatchesMedia,
                bumped = bumped,
                contentChangedFromSwitch = input.contentChangedFromSwitchSnapshot,
                timedOut = timedOut,
            )
            Phase.IDLE -> decideIdle(
                hasValidLines = input.hasValidLines,
                titleMatchesMedia = input.titleMatchesMedia,
                contentChangedFromCurrent = input.contentChangedFromCurrentDisplay,
            )
        }
    }

    private fun decideWaiting(
        hasValidLines: Boolean,
        titleMatchesMedia: Boolean,
        bumped: Boolean,
        contentChangedFromSwitch: Boolean,
        timedOut: Boolean,
    ): Decision {
        if (hasValidLines) {
            // 标题对齐，或切歌后 version/内容已变（标题可能仍滞后）
            if (titleMatchesMedia || bumped || contentChangedFromSwitch) {
                return Decision.SHOW_LYRIC
            }
            return Decision.IGNORE
        }
        // 无有效歌词行：标题已对齐 / version 已变 / 超时 → 确认无词，出专辑
        if (titleMatchesMedia || bumped || timedOut) {
            return Decision.SHOW_ALBUM
        }
        return Decision.IGNORE
    }

    private fun decideIdle(
        hasValidLines: Boolean,
        titleMatchesMedia: Boolean,
        contentChangedFromCurrent: Boolean,
    ): Decision {
        if (hasValidLines) {
            if (titleMatchesMedia) return Decision.SHOW_LYRIC
            // 同曲进度行：标题仍可能短暂不一致，但 l/s 已变
            if (contentChangedFromCurrent) return Decision.SHOW_LYRIC
            return Decision.IGNORE
        }
        if (titleMatchesMedia) return Decision.SHOW_ALBUM
        return Decision.IGNORE
    }

    /**
     * 标题是否同一曲：全等或互相包含（去空白），空标题不判匹配。
     */
    fun titlesMatch(providerTitle: String, mediaTitle: String): Boolean {
        val p = providerTitle.trim()
        val m = mediaTitle.trim()
        if (p.isEmpty() || m.isEmpty()) return false
        if (p == m) return true
        val ps = p.replace(" ", "")
        val ms = m.replace(" ", "")
        return ps.contains(ms) || ms.contains(ps)
    }
}
