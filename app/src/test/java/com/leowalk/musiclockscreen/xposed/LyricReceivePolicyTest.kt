package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricReceivePolicyTest {

    @Test
    fun blankTitle_isNotConfirmedSameSong() {
        assertFalse(LyricReceivePolicy.titlesConfirmedSame("", "Song B"))
        assertFalse(LyricReceivePolicy.titlesConfirmedSame("Song A", ""))
        assertTrue(LyricReceivePolicy.titlesConfirmedSame("Song A", "Song A"))
    }

    @Test
    fun neverMergeCtx_whenTitleUnconfirmedOrWaiting() {
        assertFalse(
            LyricReceivePolicy.shouldMergePreviousCtx(
                incomingHasCtx = false,
                previousHasCtx = true,
                titlesConfirmedSame = false,
                waitingForNewTrack = false,
            ),
        )
        assertFalse(
            LyricReceivePolicy.shouldMergePreviousCtx(
                incomingHasCtx = false,
                previousHasCtx = true,
                titlesConfirmedSame = true,
                waitingForNewTrack = true,
            ),
        )
        assertTrue(
            LyricReceivePolicy.shouldMergePreviousCtx(
                incomingHasCtx = false,
                previousHasCtx = true,
                titlesConfirmedSame = true,
                waitingForNewTrack = false,
            ),
        )
        assertFalse(
            LyricReceivePolicy.shouldMergePreviousCtx(
                incomingHasCtx = false,
                previousHasCtx = true,
                titlesConfirmedSame = true,
                waitingForNewTrack = false,
                incomingClearOrLoading = true,
            ),
        )
    }

    @Test
    fun loadingPush_isTrackClearEvenWithTitle() {
        assertTrue(
            LyricReceivePolicy.isTrackClearOrLoading(
                loading = true,
                lyricLine = "",
                secondLine = "",
                title = "New Song",
                hasCtxLines = false,
            ),
        )
        assertFalse(
            LyricReceivePolicy.isTrackClearOrLoading(
                loading = false,
                lyricLine = "",
                secondLine = "",
                title = "New Song",
                hasCtxLines = false,
            ),
        )
        assertTrue(
            LyricReceivePolicy.isTrackClearOrLoading(
                loading = false,
                lyricLine = "",
                secondLine = "",
                title = "",
                hasCtxLines = false,
            ),
        )
        assertFalse(
            LyricReceivePolicy.isTrackClearOrLoading(
                loading = false,
                lyricLine = "",
                secondLine = "",
                title = "",
                hasCtxLines = true,
            ),
        )
    }

    @Test
    fun dropTimeline_onlyWhileWaitingForNewTrack() {
        // IDLE：焦点短暂对不上仍保留时间轴，否则切歌后只剩轻量行、上滑失效
        assertFalse(
            LyricReceivePolicy.shouldDropCachedTimeline(
                lightMain = "新歌当前行",
                cachedLineCount = 12,
                lightMatchesCachedLine = false,
                waitingForNewTrack = false,
            ),
        )
        assertFalse(
            LyricReceivePolicy.shouldDropCachedTimeline(
                lightMain = "旧行",
                cachedLineCount = 12,
                lightMatchesCachedLine = true,
                waitingForNewTrack = false,
            ),
        )
        assertFalse(
            LyricReceivePolicy.shouldDropCachedTimeline(
                lightMain = "旧行",
                cachedLineCount = 12,
                lightMatchesCachedLine = true,
                waitingForNewTrack = true,
            ),
        )
        assertTrue(
            LyricReceivePolicy.shouldDropCachedTimeline(
                lightMain = "新歌当前行",
                cachedLineCount = 12,
                lightMatchesCachedLine = false,
                waitingForNewTrack = true,
            ),
        )
    }

    @Test
    fun mediaTitleChange_resetsEvenIfTrackKeyLag() {
        assertTrue(LyricReceivePolicy.shouldResetForMediaTitleChange("Old Song", "New Song"))
        assertFalse(LyricReceivePolicy.shouldResetForMediaTitleChange("Song", "Song"))
        assertFalse(LyricReceivePolicy.shouldResetForMediaTitleChange("", "New Song"))
    }

    @Test
    fun preferDisplayLineIndex_allowsPositionToLeadStuckFocus() {
        assertEquals(5, LyricReceivePolicy.preferDisplayLineIndex(focusIdx = 0, positionIdx = 5))
        assertEquals(3, LyricReceivePolicy.preferDisplayLineIndex(focusIdx = 3, positionIdx = 2))
        assertEquals(4, LyricReceivePolicy.preferDisplayLineIndex(focusIdx = -1, positionIdx = 4))
        assertEquals(1, LyricReceivePolicy.preferDisplayLineIndex(focusIdx = 1, positionIdx = -1))
    }
}
