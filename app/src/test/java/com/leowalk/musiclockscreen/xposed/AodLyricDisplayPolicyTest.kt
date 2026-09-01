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
    fun lyricContentChanged_whenLightLineAdvances() {
        assertTrue(
            AodLyricDisplayPolicy.lyricContentChangedFromFields(
                AodLyricDisplayPolicy.LyricSnapshotFields(l = "line2"),
                AodLyricDisplayPolicy.LyricSnapshotFields(l = "line1"),
            )
        )
    }
}
