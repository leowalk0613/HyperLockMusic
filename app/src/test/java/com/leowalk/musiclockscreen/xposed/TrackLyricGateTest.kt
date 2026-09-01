package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLyricGateTest {

    private fun snap(
        vLyric: Int = 3,
        vFd: Int = 2,
        json: String = """{"title":"Old","l":"old line"}""",
        startedAt: Long = 1000L,
    ) = TrackLyricGate.Snapshot(vLyric, vFd, json, startedAt)

    private fun input(
        phase: TrackLyricGate.Phase,
        snapshot: TrackLyricGate.Snapshot? = snap(),
        now: Long = 1500L,
        vLyric: Int = 3,
        vFd: Int = 2,
        hasValidLines: Boolean = true,
        titleMatches: Boolean = false,
        contentFromSwitch: Boolean = false,
        contentFromCurrent: Boolean = false,
    ) = TrackLyricGate.Input(
        phase = phase,
        snapshot = snapshot,
        nowElapsedMs = now,
        vLyric = vLyric,
        vFd = vFd,
        hasValidLines = hasValidLines,
        titleMatchesMedia = titleMatches,
        contentChangedFromSwitchSnapshot = contentFromSwitch,
        contentChangedFromCurrentDisplay = contentFromCurrent,
    )

    @Test
    fun waiting_acceptsLyric_whenTitleMatches() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_LYRIC,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    titleMatches = true,
                    hasValidLines = true,
                )
            )
        )
    }

    @Test
    fun waiting_acceptsLyric_whenVersionBumpedDespiteTitleLag() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_LYRIC,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    vLyric = 5,
                    titleMatches = false,
                    hasValidLines = true,
                )
            )
        )
    }

    @Test
    fun waiting_acceptsLyric_whenContentChangedFromSwitchSnapshot() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_LYRIC,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    titleMatches = false,
                    contentFromSwitch = true,
                    hasValidLines = true,
                )
            )
        )
    }

    @Test
    fun waiting_showsAlbum_whenEmptyAndVersionBumped() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_ALBUM,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    vLyric = 5,
                    hasValidLines = false,
                    titleMatches = false,
                )
            )
        )
    }

    @Test
    fun waiting_showsAlbum_whenEmptyAndTitleMatches() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_ALBUM,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    hasValidLines = false,
                    titleMatches = true,
                )
            )
        )
    }

    @Test
    fun waiting_showsAlbum_onTimeout() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_ALBUM,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    now = 1000L + TrackLyricGate.WAIT_TIMEOUT_MS,
                    hasValidLines = false,
                    titleMatches = false,
                )
            )
        )
    }

    @Test
    fun waiting_ignores_staleSamePayload() {
        assertEquals(
            TrackLyricGate.Decision.IGNORE,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.WAITING,
                    hasValidLines = true,
                    titleMatches = false,
                    contentFromSwitch = false,
                    vLyric = 3,
                    vFd = 2,
                )
            )
        )
    }

    @Test
    fun idle_acceptsMatchingLyric() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_LYRIC,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.IDLE,
                    snapshot = null,
                    titleMatches = true,
                    hasValidLines = true,
                )
            )
        )
    }

    @Test
    fun idle_acceptsLineAdvanceWithTitleLag() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_LYRIC,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.IDLE,
                    snapshot = null,
                    titleMatches = false,
                    contentFromCurrent = true,
                    hasValidLines = true,
                )
            )
        )
    }

    @Test
    fun idle_showsAlbum_whenEmptyAndTitleMatches() {
        assertEquals(
            TrackLyricGate.Decision.SHOW_ALBUM,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.IDLE,
                    snapshot = null,
                    hasValidLines = false,
                    titleMatches = true,
                )
            )
        )
    }

    @Test
    fun idle_ignoresUnrelatedStalePayload() {
        assertEquals(
            TrackLyricGate.Decision.IGNORE,
            TrackLyricGate.decide(
                input(
                    phase = TrackLyricGate.Phase.IDLE,
                    snapshot = null,
                    hasValidLines = true,
                    titleMatches = false,
                    contentFromCurrent = false,
                )
            )
        )
    }

    @Test
    fun titlesMatch_containsRelation() {
        assertTrue(TrackLyricGate.titlesMatch("Song (Live)", "Song"))
        assertTrue(TrackLyricGate.titlesMatch("你好", "你好 - 歌手"))
        assertFalse(TrackLyricGate.titlesMatch("A", "B"))
        assertFalse(TrackLyricGate.titlesMatch("", "A"))
    }
}
