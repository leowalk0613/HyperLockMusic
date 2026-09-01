package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyguardWallpaperScalePolicyTest {

    @Test
    fun shouldSuppress_falseWithoutContext() {
        assertFalse(KeyguardWallpaperScalePolicy.shouldSuppress(null))
    }

    @Test
    fun reset_clearsGoingToSleepFlag() {
        KeyguardWallpaperScalePolicy.markGoingToSleepForTest()
        assertTrue(KeyguardWallpaperScalePolicy.isGoingToSleep())
        KeyguardWallpaperScalePolicy.reset()
        assertFalse(KeyguardWallpaperScalePolicy.isGoingToSleep())
    }

    @Test
    fun shouldSuppressScale_whenSwitchOff() {
        assertFalse(
            KeyguardWallpaperScalePolicy.shouldSuppressScale(
                disableWallpaperScale = false,
                musicLockscreenActive = true,
                inLinkageAnimWindow = true,
                screenInteractive = false,
            )
        )
    }

    @Test
    fun shouldSuppressScale_whenMusicLockscreenOff() {
        assertFalse(
            KeyguardWallpaperScalePolicy.shouldSuppressScale(
                disableWallpaperScale = true,
                musicLockscreenActive = false,
                inLinkageAnimWindow = true,
                screenInteractive = false,
            )
        )
    }

    @Test
    fun shouldSuppressScale_onAodWhenMusicLockscreenActive() {
        assertTrue(
            KeyguardWallpaperScalePolicy.shouldSuppressScale(
                disableWallpaperScale = true,
                musicLockscreenActive = true,
                inLinkageAnimWindow = false,
                screenInteractive = false,
            )
        )
    }

    @Test
    fun shouldSuppressScale_inLinkageWindowWhileStillInteractive() {
        assertTrue(
            KeyguardWallpaperScalePolicy.shouldSuppressScale(
                disableWallpaperScale = true,
                musicLockscreenActive = true,
                inLinkageAnimWindow = true,
                screenInteractive = true,
            )
        )
    }

    @Test
    fun shouldNotSuppressScale_onInteractiveLockscreenOutsideLinkage() {
        assertFalse(
            KeyguardWallpaperScalePolicy.shouldSuppressScale(
                disableWallpaperScale = true,
                musicLockscreenActive = true,
                inLinkageAnimWindow = false,
                screenInteractive = true,
            )
        )
    }

    @Test
    fun shouldHandleSleepTransition_requiresSwitchAndMusicLockscreen() {
        assertTrue(
            KeyguardWallpaperScalePolicy.shouldHandleSleepTransition(
                disableWallpaperScale = true,
                musicLockscreenActive = true,
            )
        )
        assertFalse(
            KeyguardWallpaperScalePolicy.shouldHandleSleepTransition(
                disableWallpaperScale = false,
                musicLockscreenActive = true,
            )
        )
        assertFalse(
            KeyguardWallpaperScalePolicy.shouldHandleSleepTransition(
                disableWallpaperScale = true,
                musicLockscreenActive = false,
            )
        )
    }
}
