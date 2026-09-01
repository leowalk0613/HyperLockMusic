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
}
