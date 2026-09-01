package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodLyricDisplayPolicyTest {

    @Test
    fun aodRefreshMode_whenScreenOffOnKeyguard() {
        assertTrue(
            AodLyricDisplayPolicy.isAodLyricRefreshMode(
                screenInteractive = false,
                onKeyguard = true,
            )
        )
    }

    @Test
    fun strictTrackSwitchGate_onlyWhenInteractiveOnKeyguard() {
        assertTrue(
            AodLyricDisplayPolicy.shouldUseStrictTrackSwitchGate(
                screenInteractive = true,
                onKeyguard = true,
            )
        )
        assertFalse(
            AodLyricDisplayPolicy.shouldUseStrictTrackSwitchGate(
                screenInteractive = false,
                onKeyguard = true,
            )
        )
    }

    @Test
    fun playbackOk_whenPlayingRegardlessOfScreen() {
        assertTrue(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = true,
                screenInteractive = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = false,
            )
        )
    }

    @Test
    fun playbackOk_onAodWhenMediaListenerReportsActive() {
        assertTrue(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = false,
                screenInteractive = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = true,
            )
        )
    }

    @Test
    fun playbackOk_onAodWhenLyricContentAlreadyLoaded() {
        assertTrue(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = false,
                screenInteractive = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = false,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackNotOk_whenPausedOnInteractiveLockscreen() {
        assertFalse(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = false,
                screenInteractive = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = false,
            )
        )
    }

    @Test
    fun shouldProbeWithoutVersionBump_onlyOnInteractiveStrictGate() {
        assertTrue(
            AodLyricDisplayPolicy.shouldProbeLyricWithoutVersionBump(
                awaitingFreshLyricsAfterTrackSwitch = true,
                screenInteractive = true,
                onKeyguard = true,
            )
        )
        assertFalse(
            AodLyricDisplayPolicy.shouldProbeLyricWithoutVersionBump(
                awaitingFreshLyricsAfterTrackSwitch = true,
                screenInteractive = false,
                onKeyguard = true,
            )
        )
    }

    @Test
    fun lyricContentChanged_whenCurrentLineDiffers() {
        val current = AodLyricDisplayPolicy.LyricSnapshotFields(l = "new line")
        val previous = AodLyricDisplayPolicy.LyricSnapshotFields(l = "old line")
        assertTrue(AodLyricDisplayPolicy.lyricContentChangedFromFields(current, previous))
    }

    @Test
    fun lyricContentNotChanged_whenSnapshotMatches() {
        val fields = AodLyricDisplayPolicy.LyricSnapshotFields(l = "same line", title = "Song A")
        assertFalse(AodLyricDisplayPolicy.lyricContentChangedFromFields(fields, fields))
    }

    @Test
    fun canAcceptProviderLyric_whenTitleStaleButVersionBumpedOnAod() {
        val newJson = org.json.JSONObject()
        assertTrue(
            AodLyricDisplayPolicy.canAcceptProviderLyric(
                json = newJson,
                vLyric = 5,
                vFd = 3,
                pendingAodTrackSwitch = true,
                aodSwitchVLyric = 4,
                aodSwitchVFd = 3,
                aodSwitchLyricJsonSnapshot = """{"title":"Song A","l":"line1","s":""}""",
                providerTitleStale = true,
                hasValidLines = true,
            )
        )
    }

    @Test
    fun canRejectProviderLyric_whenTitleStaleOnInteractive() {
        val json = org.json.JSONObject()
        assertFalse(
            AodLyricDisplayPolicy.canAcceptProviderLyric(
                json = json,
                vLyric = 5,
                vFd = 3,
                pendingAodTrackSwitch = false,
                aodSwitchVLyric = -1,
                aodSwitchVFd = -1,
                aodSwitchLyricJsonSnapshot = "{}",
                providerTitleStale = true,
                hasValidLines = true,
            )
        )
    }

    @Test
    fun shouldRefreshCachedLine_evenWhenTitleStale() {
        assertTrue(
            AodLyricDisplayPolicy.shouldRefreshCachedLineByPosition(
                hasCachedLines = true,
                providerTitleStale = true,
            )
        )
        assertFalse(
            AodLyricDisplayPolicy.shouldRefreshCachedLineByPosition(
                hasCachedLines = false,
                providerTitleStale = true,
            )
        )
    }

    @Test
    fun shouldKeepDisplayedLyricOnReject_whenAlreadyShowing() {
        assertTrue(
            AodLyricDisplayPolicy.shouldKeepDisplayedLyricOnReject(
                alreadyDisplayingLyric = true,
                hasCachedLines = false,
            )
        )
        assertTrue(
            AodLyricDisplayPolicy.shouldKeepDisplayedLyricOnReject(
                alreadyDisplayingLyric = false,
                hasCachedLines = true,
            )
        )
        assertFalse(
            AodLyricDisplayPolicy.shouldKeepDisplayedLyricOnReject(
                alreadyDisplayingLyric = false,
                hasCachedLines = false,
            )
        )
    }

    @Test
    fun lyricContentChanged_whenLightLineAdvances() {
        assertTrue(
            AodLyricDisplayPolicy.lyricContentChangedFromFields(
                AodLyricDisplayPolicy.LyricSnapshotFields(l = "line2"),
                AodLyricDisplayPolicy.LyricSnapshotFields(l = "line1"),
            )
        )
    }
}
