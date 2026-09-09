package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 切歌：快照须保留旧曲内容，才能用 contentChanged 拒「同内容旧包」、收下新词。
 */
class TrackSwitchSnapshotPolicyTest {

    @Test
    fun waiting_ignoresSamePayloadAsSwitchSnapshot() {
        val snap = TrackLyricGate.Snapshot(
            vLyric = 3,
            vFd = 1,
            lyricJson = """{"title":"Old","l":"旧句","s":""}""",
            startedAtElapsedMs = 1000L,
        )
        val decision = TrackLyricGate.decide(
            TrackLyricGate.Input(
                phase = TrackLyricGate.Phase.WAITING,
                snapshot = snap,
                nowElapsedMs = 1500L,
                vLyric = 3,
                vFd = 1,
                hasValidLines = true,
                titleMatchesMedia = false,
                contentChangedFromSwitchSnapshot = false,
                contentChangedFromCurrentDisplay = false,
            ),
        )
        assertEquals(TrackLyricGate.Decision.IGNORE, decision)
    }

    @Test
    fun waiting_acceptsContentChangedDespiteTitleLag() {
        val snap = TrackLyricGate.Snapshot(
            vLyric = 3,
            vFd = 1,
            lyricJson = """{"title":"Old","l":"旧句","s":""}""",
            startedAtElapsedMs = 1000L,
        )
        val decision = TrackLyricGate.decide(
            TrackLyricGate.Input(
                phase = TrackLyricGate.Phase.WAITING,
                snapshot = snap,
                nowElapsedMs = 1500L,
                vLyric = 3,
                vFd = 1,
                hasValidLines = true,
                titleMatchesMedia = false,
                contentChangedFromSwitchSnapshot = true,
                contentChangedFromCurrentDisplay = true,
            ),
        )
        assertEquals(TrackLyricGate.Decision.SHOW_LYRIC, decision)
    }

    @Test
    fun emptySwitchSnapshot_blocksContentChanged_soNewSongNeedsTitleOrBump() {
        // lyricContentChangedFromSnapshot("{}") 恒 false：先 purge 再拍照会导致
        // 标题滞后且未 bump 时新词也被 IGNORE —— 故生产必须先 mark 再 purge
        val emptySnap = TrackLyricGate.Snapshot(
            vLyric = -1,
            vFd = -1,
            lyricJson = "{}",
            startedAtElapsedMs = 1000L,
        )
        val newSongVsEmpty = AodLyricDisplayPolicy.lyricContentChangedFromSnapshot(
            org.json.JSONObject("""{"title":"New","l":"新句","s":""}"""),
            emptySnap.lyricJson,
        )
        assertEquals(false, newSongVsEmpty)
        val decision = TrackLyricGate.decide(
            TrackLyricGate.Input(
                phase = TrackLyricGate.Phase.WAITING,
                snapshot = emptySnap,
                nowElapsedMs = 1500L,
                vLyric = -1,
                vFd = -1,
                hasValidLines = true,
                titleMatchesMedia = false,
                contentChangedFromSwitchSnapshot = newSongVsEmpty,
                contentChangedFromCurrentDisplay = true,
            ),
        )
        assertEquals(TrackLyricGate.Decision.IGNORE, decision)
    }
}
