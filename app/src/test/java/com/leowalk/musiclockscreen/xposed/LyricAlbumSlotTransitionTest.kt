package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricAlbumSlotTransitionTest {

    @Test
    fun playbackOk_whenPlaying() {
        assertTrue(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = true,
                confirmedPaused = false,
                nowElapsedMs = 1000L,
                playbackHoldUntilMs = 0L,
                inScreenPowerTransition = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackNotOk_whenConfirmedPaused() {
        assertFalse(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = false,
                confirmedPaused = true,
                nowElapsedMs = 1000L,
                playbackHoldUntilMs = 5000L,
                inScreenPowerTransition = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackOk_duringScreenPowerTransitionWithLyric() {
        assertTrue(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = false,
                confirmedPaused = false,
                nowElapsedMs = 1000L,
                playbackHoldUntilMs = 0L,
                inScreenPowerTransition = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackOk_withinHoldAfterPlayingLost() {
        assertTrue(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = false,
                confirmedPaused = false,
                nowElapsedMs = 1500L,
                playbackHoldUntilMs = 2800L,
                inScreenPowerTransition = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackNotOk_whenHoldExpiredAndNotPlaying() {
        assertFalse(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = false,
                confirmedPaused = false,
                nowElapsedMs = 3000L,
                playbackHoldUntilMs = 2800L,
                inScreenPowerTransition = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }
}
