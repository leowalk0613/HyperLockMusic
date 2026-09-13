package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazineOtherSettingsPolicyTest {

    @Test
    fun magazine_hidesLockscreenOnlyHelpers() {
        assertTrue(MagazineOtherSettingsPolicy.showBlurSection(true))
        assertTrue(MagazineOtherSettingsPolicy.showKeepLockScreenOn(true))
        assertTrue(MagazineOtherSettingsPolicy.showTitleBracket(true))
        assertFalse(MagazineOtherSettingsPolicy.showMinimalClock(true))
        assertFalse(MagazineOtherSettingsPolicy.showDisableWallpaperScale(true))
        assertFalse(MagazineOtherSettingsPolicy.showAodFullMedia(true))
        assertTrue(MagazineOtherSettingsPolicy.showLockscreenAssistSection(true))
    }

    @Test
    fun normal_showsAllSections() {
        assertTrue(MagazineOtherSettingsPolicy.showBlurSection(false))
        assertTrue(MagazineOtherSettingsPolicy.showMinimalClock(false))
        assertTrue(MagazineOtherSettingsPolicy.showDisableWallpaperScale(false))
        assertTrue(MagazineOtherSettingsPolicy.showKeepLockScreenOn(false))
        assertTrue(MagazineOtherSettingsPolicy.showAodFullMedia(false))
        assertTrue(MagazineOtherSettingsPolicy.showTitleBracket(false))
        assertTrue(MagazineOtherSettingsPolicy.showLockscreenAssistSection(false))
    }
}
