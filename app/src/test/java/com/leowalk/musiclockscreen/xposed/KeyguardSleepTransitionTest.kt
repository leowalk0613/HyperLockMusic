package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyguardSleepTransitionTest {

    @Test
    fun linkageWindow_activeWhileGoingToSleep() {
        KeyguardSleepTransition.reset()
        assertFalse(KeyguardSleepTransition.isInLinkageAnimWindow())
        KeyguardSleepTransition.markGoingToSleepForTest()
        assertTrue(KeyguardSleepTransition.isInLinkageAnimWindow())
        KeyguardSleepTransition.reset()
        assertFalse(KeyguardSleepTransition.isInLinkageAnimWindow())
    }
}
