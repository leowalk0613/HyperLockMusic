package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricAlbumPriorityPolicyTest {

    @Test
    fun hidesAlbum_whenLyricDisplayed() {
        assertTrue(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
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
                lyricCurrentlyDisplayed = false,
                trackGatePhase = TrackLyricGate.Phase.WAITING,
                hasLyricData = false,
                hasDisplayableText = false,
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
                lyricCurrentlyDisplayed = true,
                trackGatePhase = TrackLyricGate.Phase.IDLE,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }
}
