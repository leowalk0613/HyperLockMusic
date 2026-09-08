package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricDisplayPolicyTest {

    @Test
    fun showsLyric_onlyWhenBothEnabled() {
        assertTrue(LyricDisplayPolicy.shouldShowLyric(lyricEnabled = true, showLyric = true))
        assertFalse(LyricDisplayPolicy.shouldShowLyric(lyricEnabled = false, showLyric = true))
        assertFalse(LyricDisplayPolicy.shouldShowLyric(lyricEnabled = true, showLyric = false))
        assertFalse(LyricDisplayPolicy.shouldShowLyric(lyricEnabled = false, showLyric = false))
    }

    @Test
    fun forceBootstrapOnEnter_whenShowAlreadyOn() {
        assertTrue(
            LyricDisplayPolicy.shouldForceLyricBootstrapOnEnter(
                lyricEnabled = true,
                showLyric = true,
                musicLockscreenActive = true,
            )
        )
        assertFalse(
            LyricDisplayPolicy.shouldForceLyricBootstrapOnEnter(
                lyricEnabled = true,
                showLyric = true,
                musicLockscreenActive = false,
            )
        )
        assertFalse(
            LyricDisplayPolicy.shouldForceLyricBootstrapOnEnter(
                lyricEnabled = true,
                showLyric = false,
                musicLockscreenActive = true,
            )
        )
    }
}
