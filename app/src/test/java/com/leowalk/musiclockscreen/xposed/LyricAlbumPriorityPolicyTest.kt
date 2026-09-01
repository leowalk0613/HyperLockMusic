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
                awaitingFreshLyricsAfterTrackSwitch = false,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun hidesAlbum_whileAwaitingFreshLyricsOnAodTrackChange() {
        assertTrue(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                lyricCurrentlyDisplayed = false,
                awaitingFreshLyricsAfterTrackSwitch = true,
                hasLyricData = false,
                hasDisplayableText = false,
            )
        )
    }

    @Test
    fun showsAlbum_whenLyricOffOrNoLyricData() {
        assertFalse(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                lyricCurrentlyDisplayed = true,
                awaitingFreshLyricsAfterTrackSwitch = false,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
        assertFalse(
            LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
                showLyricEnabled = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                lyricCurrentlyDisplayed = false,
                awaitingFreshLyricsAfterTrackSwitch = false,
                hasLyricData = false,
                hasDisplayableText = false,
            )
        )
    }
}
