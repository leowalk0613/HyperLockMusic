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
        // 亮屏锁屏：MediaSession 播放态常滞后，歌词已就绪且 NLS 报告媒体活跃时仍显示
        if (screenInteractive && musicLockscreenActive && onKeyguard &&
            hasLyricData && hasDisplayableText && mediaPlaybackActive
        ) {
            return true
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

    /**
     * Provider 载荷是否属于当前 MediaSession 曲目。
     * 切歌后 LyricFocus 推送有滞后，Provider 里短暂保留上一首歌词是正常的——此时应隐藏而非上屏。
     */
    fun isProviderLyricStaleForMedia(providerJson: JSONObject, mediaTitle: String): Boolean {
        return isProviderLyricStaleForMedia(
            providerJson.optString("title", ""),
            mediaTitle,
        )
    }

    fun isProviderLyricStaleForMedia(providerTitle: String, mediaTitle: String): Boolean {
        val provider = providerTitle.trim()
        val expected = mediaTitle.trim()
        if (provider.isEmpty() || expected.isEmpty()) return false
        return !isSameSongLyricPayload(provider, expected)
    }

    /** 两次 Provider 快照是否同一曲（用于禁止跨曲合并 ctx / 残留旧词）。 */
    fun isSameSongLyricPayload(oldTitle: String, neuTitle: String): Boolean {
        val old = oldTitle.trim()
        val neu = neuTitle.trim()
        if (old.isBlank() || neu.isBlank()) return true
        return TrackLyricGate.titlesMatch(old, neu)
    }

    fun isSameSongLyricPayload(old: JSONObject, neu: JSONObject): Boolean {
        return isSameSongLyricPayload(
            old.optString("title", ""),
            neu.optString("title", ""),
        )
    }

    /** 仅 trackKey 真正切换时重置歌词快照（null→首键不算）。 */
    fun shouldResetLyricForTrackKeyChange(previousKey: String?, newKey: String?): Boolean {
        return AlbumArtResolver.isRealTrackSwitch(previousKey, newKey)
    }

    internal data class LyricLineDisplay(
        val main: String,
        val second: String,
        val hasSecond: Boolean,
        val isTranslation: Boolean,
    )

    /**
     * LyricFocus 轻量 s：有翻译时为译文，无翻译时常为下一句原文。
     * [nextLineText] / [songHasTranslation] 用于消歧，避免无翻译时误触发互换。
     */
    fun resolveLightLyricDisplay(
        l: String,
        s: String,
        nextLineText: String = "",
        songHasTranslation: Boolean? = null,
    ): LyricLineDisplay {
        val main = l.trim().ifBlank { " " }
        val second = s.trim()
        val hasSecond = second.isNotEmpty()
        if (!hasSecond) {
            return LyricLineDisplay(main, "", hasSecond = false, isTranslation = false)
        }
        val isTranslation = isSecondaryLineTranslation(second, nextLineText, songHasTranslation)
        return LyricLineDisplay(main, second, hasSecond = true, isTranslation = isTranslation)
    }

    /**
     * 全量 ctx 当前行：优先行内 r；轻量 s 仅在不像「下一句」时作翻译回退。
     */
    fun resolveCachedLineDisplay(
        currentText: String,
        lineTranslation: String,
        nextLineText: String,
        lightMain: String,
        lightTranslation: String,
        immersiveLyric: Boolean,
        songHasTranslation: Boolean? = null,
    ): LyricLineDisplay {
        val main = currentText.trim().ifBlank { " " }
        val next = nextLineText.trim()
        val trans = lineTranslation.trim().ifBlank {
            val lightS = lightTranslation.trim()
            val lightL = lightMain.trim()
            val lightOk = lightS.isNotEmpty() &&
                (lightL.isEmpty() || lightL == currentText.trim()) &&
                isSecondaryLineTranslation(lightS, next, songHasTranslation)
            if (lightOk) lightS else ""
        }
        if (trans.isNotEmpty()) {
            return LyricLineDisplay(main, trans, hasSecond = true, isTranslation = true)
        }
        if (!immersiveLyric) {
            if (next.isNotEmpty()) {
                return LyricLineDisplay(main, next, hasSecond = true, isTranslation = false)
            }
        }
        return LyricLineDisplay(main, "", hasSecond = false, isTranslation = false)
    }

    /**
     * 副行是否为翻译：全曲已确认无翻译 → false；与下一句相同 → false（LyricFocus 无译惯例）。
     */
    fun isSecondaryLineTranslation(
        secondary: String,
        nextLineText: String = "",
        songHasTranslation: Boolean? = null,
    ): Boolean {
        val second = secondary.trim()
        if (second.isEmpty()) return false
        if (songHasTranslation == false) return false
        if (songHasTranslation == true) return true
        val next = nextLineText.trim()
        if (next.isNotEmpty() && second == next) return false
        return true
    }

    /** ctx.lines 是否出现过非空 r；无 lines 时返回 null。 */
    fun songHasTranslationFromCtx(json: JSONObject): Boolean? {
        val lines = json.optJSONObject("ctx")?.optJSONArray("lines") ?: return null
        if (lines.length() == 0) return null
        val rs = ArrayList<String>(lines.length())
        for (i in 0 until lines.length()) {
            rs.add(lines.optJSONObject(i)?.optString("r", "").orEmpty())
        }
        return songHasTranslationFromRs(rs)
    }

    fun songHasTranslationFromRs(translations: Iterable<String>): Boolean {
        return translations.any { it.trim().isNotEmpty() }
    }

    /** 本地歌词快照是否为空（需向 Provider 重拉）。 */
    fun isLyricSnapshotEmpty(lastLyricJson: String): Boolean {
        return lastLyricJson.isBlank() || lastLyricJson.trim() == "{}"
    }

    /**
     * 是否应重读全量 FD。
     * 快照为空时即使 version 未变也要重拉——避免「先记 version、读失败」后卡死，
     * 直到 LyricFocus 切源 bump 才恢复。
     */
    fun shouldReloadLyricFd(
        oldVLyricFd: Int,
        newVLyricFd: Int,
        snapshotEmpty: Boolean,
    ): Boolean {
        if (oldVLyricFd != newVLyricFd) return true
        if (snapshotEmpty && newVLyricFd >= 0) return true
        return oldVLyricFd < 0 && snapshotEmpty
    }

    /** 是否应重读轻量 lyric（全量未成功或仅 light 版本变化）。 */
    fun shouldReloadLightLyric(
        oldVLyric: Int,
        newVLyric: Int,
        snapshotEmpty: Boolean,
        fdVersionUnchangedOrFdFailed: Boolean,
    ): Boolean {
        if (!fdVersionUnchangedOrFdFailed) return false
        if (oldVLyric != newVLyric || oldVLyric < 0) return true
        return snapshotEmpty
    }

    /** 原文/翻译互换：仅当第二行确认为翻译时生效。 */
    fun applyLyricSwap(
        rawMain: String,
        rawSecond: String,
        hasSecond: Boolean,
        isTranslation: Boolean,
        swapEnabled: Boolean,
    ): LyricLineDisplay {
        val hasTrans = isTranslation && hasSecond && rawSecond.isNotBlank()
        return if (swapEnabled && hasTrans) {
            LyricLineDisplay(rawSecond, rawMain, hasSecond = true, isTranslation = true)
        } else {
            LyricLineDisplay(rawMain, rawSecond, hasSecond, isTranslation)
        }
    }
}
