package com.leowalk.musiclockscreen.xposed

import org.json.JSONObject

/**
 * AOD / 息屏显示辅助策略（切歌门闩见 [TrackLyricGate]）。
 */
internal object AodLyricDisplayPolicy {

    fun isAodLyricRefreshMode(screenInteractive: Boolean, onKeyguard: Boolean): Boolean {
        return !screenInteractive && onKeyguard
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

    fun lyricContentChangedFromSnapshot(json: JSONObject, snapshot: String): Boolean {
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

    fun hasValidLyricLines(json: JSONObject): Boolean {
        val l = json.optString("l", "").trim()
        val s = json.optString("s", "").trim()
        if (l.isNotEmpty() || s.isNotEmpty()) return true
        val ctx = json.optJSONObject("ctx") ?: return false
        val linesArr = ctx.optJSONArray("lines") ?: return false
        for (i in 0 until linesArr.length()) {
            val t = linesArr.optJSONObject(i)?.optString("t", "")?.trim().orEmpty()
            if (t.isNotEmpty()) return true
        }
        return false
    }
}
