package com.leowalk.musiclockscreen.xposed

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageSleepPolicyTest {

    @Test
    fun finishOnScreenOffOrUnlock() {
        assertTrue(MagazinePageSleepPolicy.shouldFinishOnBroadcast(Intent.ACTION_SCREEN_OFF))
        assertTrue(MagazinePageSleepPolicy.shouldFinishOnBroadcast(Intent.ACTION_USER_PRESENT))
        assertFalse(MagazinePageSleepPolicy.shouldFinishOnBroadcast(Intent.ACTION_SCREEN_ON))
        assertFalse(MagazinePageSleepPolicy.shouldFinishOnBroadcast(null))
    }

    @Test
    fun releaseOcclude_onlyMagazine() {
        assertTrue(MagazinePageSleepPolicy.shouldReleaseOccludeForLockscreenMatchedAod(true))
        assertFalse(MagazinePageSleepPolicy.shouldReleaseOccludeForLockscreenMatchedAod(false))
    }

    @Test
    fun blockChangeAodStyle_requiresFullAodOn() {
        assertTrue(MagazinePageSleepPolicy.shouldBlockChangeAodStyleShown(true, true))
        assertFalse(MagazinePageSleepPolicy.shouldBlockChangeAodStyleShown(true, false))
        assertFalse(MagazinePageSleepPolicy.shouldBlockChangeAodStyleShown(false, true))
        assertFalse(MagazinePageSleepPolicy.shouldBlockChangeAodStyleShown(false, false))
    }

    @Test
    fun restoreFullAod_onlyWhenSettingOn() {
        assertTrue(MagazinePageSleepPolicy.shouldRestoreFullAodAfterMagazineSleep(true, true))
        assertFalse(MagazinePageSleepPolicy.shouldRestoreFullAodAfterMagazineSleep(true, false))
        assertFalse(MagazinePageSleepPolicy.shouldRestoreFullAodAfterMagazineSleep(false, true))
    }
}
