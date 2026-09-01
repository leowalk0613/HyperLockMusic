package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyguardOverlayVisibilitySyncTest {

    @Before
    fun reset() {
        KeyguardOverlayVisibilitySync.reset()
    }

    @Test
    fun shouldApply_onlyWhenStateChanges() {
        assertTrue(KeyguardOverlayVisibilitySync.shouldApply(true))
        assertFalse(KeyguardOverlayVisibilitySync.shouldApply(true))
        assertTrue(KeyguardOverlayVisibilitySync.shouldApply(false))
        assertFalse(KeyguardOverlayVisibilitySync.shouldApply(false))
    }

    @Test
    fun reset_allowsReapply() {
        assertTrue(KeyguardOverlayVisibilitySync.shouldApply(true))
        KeyguardOverlayVisibilitySync.reset()
        assertTrue(KeyguardOverlayVisibilitySync.shouldApply(true))
    }
}
