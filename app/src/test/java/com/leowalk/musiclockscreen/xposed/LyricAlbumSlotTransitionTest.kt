package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
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
    fun playbackNotOk_whenConfirmedPausedOutsideTransition() {
        assertFalse(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = false,
                confirmedPaused = true,
                nowElapsedMs = 1000L,
                playbackHoldUntilMs = 5000L,
                inScreenPowerTransition = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackOk_duringScreenPowerTransitionEvenIfFalsePaused() {
        assertTrue(
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = false,
                confirmedPaused = true,
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

    @Test
    fun pinLyric_duringPowerTransitionWithContent() {
        assertTrue(
            LyricAlbumSlotTransition.shouldPinLyricVisibility(
                nowElapsedMs = 1000L,
                pinUntilMs = 0L,
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
                confirmedPaused = false,
                inScreenPowerTransition = true,
            )
        )
    }

    @Test
    fun pinLyric_withinPinWindowIgnoresFalsePause() {
        assertTrue(
            LyricAlbumSlotTransition.shouldPinLyricVisibility(
                nowElapsedMs = 2000L,
                pinUntilMs = 3500L,
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
                confirmedPaused = true,
                inScreenPowerTransition = false,
            )
        )
    }

    @Test
    fun pinLyric_expiresAfterPinWindow() {
        assertFalse(
            LyricAlbumSlotTransition.shouldPinLyricVisibility(
                nowElapsedMs = 4000L,
                pinUntilMs = 3500L,
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                hasLyricData = true,
                hasDisplayableText = true,
                confirmedPaused = false,
                inScreenPowerTransition = false,
            )
        )
    }

    @Test
    fun snapKeepVisible_whenPinnedAndAlreadyShowing() {
        assertTrue(
            LyricAlbumSlotTransition.shouldSnapKeepVisible(
                alreadyVisible = true,
                pinActive = true,
                hasDisplayableText = true,
            )
        )
        assertFalse(
            LyricAlbumSlotTransition.shouldSnapKeepVisible(
                alreadyVisible = false,
                pinActive = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun setVisibilitySideEffects_skipWhenUnchangedOrSuppressed() {
        assertFalse(
            LyricAlbumSlotTransition.shouldRunSetVisibilitySideEffects(
                previousVisibility = android.view.View.VISIBLE,
                newVisibility = android.view.View.VISIBLE,
                suppressingSideEffects = false,
            )
        )
        assertFalse(
            LyricAlbumSlotTransition.shouldRunSetVisibilitySideEffects(
                previousVisibility = android.view.View.GONE,
                newVisibility = android.view.View.VISIBLE,
                suppressingSideEffects = true,
            )
        )
        assertTrue(
            LyricAlbumSlotTransition.shouldRunSetVisibilitySideEffects(
                previousVisibility = android.view.View.GONE,
                newVisibility = android.view.View.VISIBLE,
                suppressingSideEffects = false,
            )
        )
    }

    @Test
    fun forceShowAlbum_onlyWhenLyricNotOccupyingSlot() {
        assertFalse(
            LyricAlbumSlotTransition.shouldForceShowAlbumAfterVisibilityUpdate(
                lyricPriorityOverAlbum = true,
            )
        )
        assertTrue(
            LyricAlbumSlotTransition.shouldForceShowAlbumAfterVisibilityUpdate(
                lyricPriorityOverAlbum = false,
            )
        )
    }

    @Test
    fun preferWaitTimeout_exitsWaitingToIdleForAlbumRestore() {
        assertEquals(
            TrackLyricGate.Phase.IDLE,
            LyricAlbumSlotTransition.trackGatePhaseAfterPreferWaitTimeout(
                TrackLyricGate.Phase.WAITING,
            ),
        )
        assertEquals(
            TrackLyricGate.Phase.IDLE,
            LyricAlbumSlotTransition.trackGatePhaseAfterPreferWaitTimeout(
                TrackLyricGate.Phase.IDLE,
            ),
        )
        // 超时后 IDLE + prefer=false → 无词时不应再藏专辑
        assertFalse(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                isPlaying = true,
                lyricCurrentlyDisplayed = false,
                trackGatePhase = LyricAlbumSlotTransition.trackGatePhaseAfterPreferWaitTimeout(
                    TrackLyricGate.Phase.WAITING,
                ),
                hasLyricData = false,
                hasDisplayableText = false,
                preferLyricUntilResolved = false,
            ),
        )
        // 若误留 WAITING，专辑会永远不回来（历史 bug）
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
                preferLyricUntilResolved = false,
            ),
        )
    }
}
