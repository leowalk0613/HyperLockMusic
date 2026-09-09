package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualModeSettingsPolicyTest {

    @Test
    fun prefKey_prefixesMagazine() {
        assertEquals("blur_radius", DualModeSettingsPolicy.prefKey("blur_radius", false))
        assertEquals("magazine_blur_radius", DualModeSettingsPolicy.prefKey("blur_radius", true))
        assertEquals("keep_lockscreen_on", DualModeSettingsPolicy.prefKey("keep_lockscreen_on", false))
        assertEquals(
            "magazine_keep_lockscreen_on",
            DualModeSettingsPolicy.prefKey("keep_lockscreen_on", true),
        )
        assertEquals("title_bracket_mode", DualModeSettingsPolicy.prefKey("title_bracket_mode", false))
        assertEquals(
            "magazine_title_bracket_mode",
            DualModeSettingsPolicy.prefKey("title_bracket_mode", true),
        )
    }

    @Test
    fun fallback_untilMagazineKeyWritten() {
        assertEquals(80f, DualModeSettingsPolicy.resolveStoredOrFallback(false, 10f, 80f), 0.01f)
        assertEquals(10f, DualModeSettingsPolicy.resolveStoredOrFallback(true, 10f, 80f), 0.01f)
        assertTrue(DualModeSettingsPolicy.resolveStoredOrFallback(false, false, true))
        assertFalse(DualModeSettingsPolicy.resolveStoredOrFallback(true, false, true))
        assertEquals("default", DualModeSettingsPolicy.resolveStoredOrFallback(false, "hide", "default"))
        assertEquals("hide", DualModeSettingsPolicy.resolveStoredOrFallback(true, "hide", "default"))
    }

    @Test
    fun enterMagazine_doesNotTouchNormalLyric() {
        assertFalse(DualModeSettingsPolicy.shouldMutateNormalLyricOnEnterMagazine())
    }
}
