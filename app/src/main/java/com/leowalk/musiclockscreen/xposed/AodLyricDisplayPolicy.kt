package com.leowalk.musiclockscreen.xposed

import org.json.JSONObject

/**
 * AOD / 息屏下歌词刷新策略。
 *
 * 亮屏切歌：version 门闩防闪回。
 * AOD 切歌：切歌瞬间快照 provider，版本 bump 或歌词内容变化即可上屏（标题可能滞后）。
 */
internal object AodLyricDisplayPolicy {

    fun isAodLyricRefreshMode(screenInteractive: Boolean, onKeyguard: Boolean): Boolean {
        return !screenInteractive && onKeyguard
    }

    fun shouldUseStrictTrackSwitchGate(screenInteractive: Boolean, onKeyguard: Boolean): Boolean {
        return screenInteractive && onKeyguard
    }

    fun isPlaybackOkForLyricDisplay(
        isPlaying: Boolean,
        screenInteractive: Boolean,
        musicLockscreenActive: Boolean,
        onKeyguard: Boolean,
        mediaPlaybackActive: Boolean,
        hasLyricData: Boolean = false,
        hasDisplayableText: Boolean = false,
    ): Boolean {
        if (isPlaying) return true
        if (!screenInteractive && musicLockscreenActive && onKeyguard) {
            if (hasLyricData && hasDisplayableText) return true
            return mediaPlaybackActive
        }
        return false
    }

    fun shouldProbeLyricWithoutVersionBump(
        awaitingFreshLyricsAfterTrackSwitch: Boolean,
        screenInteractive: Boolean,
        onKeyguard: Boolean,
    ): Boolean {
        return awaitingFreshLyricsAfterTrackSwitch &&
            shouldUseStrictTrackSwitchGate(screenInteractive, onKeyguard)
    }

    /**
     * 是否可接受 provider 载荷上屏。
     * AOD 切歌等待期：标题 stale 时仍可能因 version bump 或 l/s/ctx 相对快照变化而接受。
     * 已上屏后标题仍滞后：允许 l/s/ctx 相对当前显示内容继续更新（进度行）。
     */
    fun canAcceptProviderLyric(
        json: JSONObject,
        vLyric: Int,
        vFd: Int,
        pendingAodTrackSwitch: Boolean,
        aodSwitchVLyric: Int,
        aodSwitchVFd: Int,
        aodSwitchLyricJsonSnapshot: String,
        providerTitleStale: Boolean,
        hasValidLines: Boolean,
        alreadyDisplayingLyric: Boolean = false,
        currentDisplayJson: String = "{}",
    ): Boolean {
        if (!hasValidLines) return false
        if (!providerTitleStale) return true
        if (pendingAodTrackSwitch) {
            if (vLyric > aodSwitchVLyric || vFd > aodSwitchVFd) return true
            return lyricContentChangedFromSnapshot(json, aodSwitchLyricJsonSnapshot)
        }
        // 标题滞后但已有上屏歌词：LyricFocus 推送的下一行 l/s 仍应接受
        if (alreadyDisplayingLyric) {
            return lyricContentChangedFromSnapshot(json, currentDisplayJson)
        }
        return false
    }

    /** 已有时间轴缓存时，不因标题滞后阻断按进度刷行。 */
    fun shouldRefreshCachedLineByPosition(
        hasCachedLines: Boolean,
        providerTitleStale: Boolean,
    ): Boolean {
        if (hasCachedLines) return true
        return !providerTitleStale
    }

    /** 拒收新载荷时是否保留已上屏歌词（避免 AOD 标题滞后把进度缓存清掉）。 */
    fun shouldKeepDisplayedLyricOnReject(
        alreadyDisplayingLyric: Boolean,
        hasCachedLines: Boolean,
    ): Boolean {
        return alreadyDisplayingLyric || hasCachedLines
    }

    internal data class LyricSnapshotFields(
        val l: String = "",
        val s: String = "",
        val title: String = "",
        val ctxFirstLine: String? = null,
    )

    internal fun parseLyricSnapshotFields(json: JSONObject): LyricSnapshotFields {
        val ctx = json.optJSONObject("ctx")
        val firstLine = ctx?.optJSONArray("lines")
            ?.optJSONObject(0)
            ?.optString("t", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return LyricSnapshotFields(
            l = json.optString("l", "").trim(),
            s = json.optString("s", "").trim(),
            title = json.optString("title", "").trim(),
            ctxFirstLine = firstLine,
        )
    }

    internal fun lyricContentChangedFromFields(
        current: LyricSnapshotFields,
        previous: LyricSnapshotFields,
    ): Boolean {
        if (current.l != previous.l || current.s != previous.s) return true
        if (current.ctxFirstLine != null && previous.ctxFirstLine == null) return true
        if (current.ctxFirstLine != null && previous.ctxFirstLine != null &&
            current.ctxFirstLine != previous.ctxFirstLine
        ) {
            return true
        }
        if (current.title.isNotEmpty() && current.title != previous.title) return true
        return false
    }

    internal fun lyricContentChangedFromSnapshot(json: JSONObject, snapshot: String): Boolean {
        if (snapshot == "{}" || snapshot.isBlank()) return false
        val snapJson = try {
            JSONObject(snapshot)
        } catch (_: Throwable) {
            return false
        }
        return lyricContentChangedFromFields(
            current = parseLyricSnapshotFields(json),
            previous = parseLyricSnapshotFields(snapJson),
        )
    }
}
