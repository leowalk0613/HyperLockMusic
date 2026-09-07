package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricAlbumPriorityPolicyTest {

    @Test
    fun hidesAlbum_whenLyricDisplayedAndPlaying() {
        assertTrue(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = true,
                lyricCurrentlyDisplayed = true,
                trackGatePhase = TrackLyricGate.Phase.IDLE,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun showsAlbum_whenPausedEvenIfLyricReady() {
        assertFalse(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = false,
                lyricCurrentlyDisplayed = true,
                trackGatePhase = TrackLyricGate.Phase.IDLE,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun hidesAlbum_whileWaitingForFreshLyrics() {
        assertTrue(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = true,
                lyricCurrentlyDisplayed = false,
                trackGatePhase = TrackLyricGate.Phase.WAITING,
                hasLyricData = false,
                hasDisplayableText = false,
            )
        )
    }

    @Test
    fun hidesAlbum_whilePreferLyricUntilResolved() {
        assertTrue(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = true,
                lyricCurrentlyDisplayed = false,
                trackGatePhase = TrackLyricGate.Phase.IDLE,
                hasLyricData = false,
                hasDisplayableText = false,
                preferLyricUntilResolved = true,
            )
        )
    }

    @Test
    fun showsAlbum_whenIdleAndNoLyric() {
        assertFalse(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = true,
                lyricCurrentlyDisplayed = false,
                trackGatePhase = TrackLyricGate.Phase.IDLE,
                hasLyricData = false,
                hasDisplayableText = false,
            )
        )
    }

    @Test
    fun showsAlbum_whenLyricSwitchOff() {
        assertFalse(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = true,
                lyricCurrentlyDisplayed = true,
                trackGatePhase = TrackLyricGate.Phase.IDLE,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }
}
