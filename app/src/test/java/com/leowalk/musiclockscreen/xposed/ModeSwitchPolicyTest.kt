package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSwitchPolicyTest {

    @Test
    fun shouldExit_onlyWhenModeActuallyChanges() {
        assertTrue(ModeSwitchPolicy.shouldExitActivePresentation(false, true))
        assertTrue(ModeSwitchPolicy.shouldExitActivePresentation(true, false))
        assertFalse(ModeSwitchPolicy.shouldExitActivePresentation(false, false))
        assertFalse(ModeSwitchPolicy.shouldExitActivePresentation(true, true))
    }

    @Test
    fun actionAndHint_areStable() {
        assertEquals(
            "com.leowalk.musiclockscreen.action.EXIT_ACTIVE_PRESENTATION",
            ModeSwitchPolicy.ACTION_EXIT_ACTIVE_PRESENTATION,
        )
        assertTrue(ModeSwitchPolicy.RESTART_SYSTEMUI_HINT.contains("重启系统界面"))
    }
}
