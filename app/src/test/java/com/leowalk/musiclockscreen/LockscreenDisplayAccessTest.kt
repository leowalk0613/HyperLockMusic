package com.leowalk.musiclockscreen

import android.app.AppOpsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockscreenDisplayAccessTest {

    @Test
    fun opShowWhenLocked_isMiuiLockscreenDisplayCode() {
        assertEquals(10020, LockscreenDisplayAccess.OP_SHOW_WHEN_LOCKED)
    }

    @Test
    fun modeAllowed_onlyWhenAppOpsAllowed() {
        assertTrue(LockscreenDisplayAccess.modeAllowed(AppOpsManager.MODE_ALLOWED))
        assertFalse(LockscreenDisplayAccess.modeAllowed(AppOpsManager.MODE_IGNORED))
        assertFalse(LockscreenDisplayAccess.modeAllowed(AppOpsManager.MODE_ERRORED))
        assertFalse(LockscreenDisplayAccess.modeAllowed(AppOpsManager.MODE_DEFAULT))
        assertFalse(LockscreenDisplayAccess.modeAllowed(null))
    }
}
