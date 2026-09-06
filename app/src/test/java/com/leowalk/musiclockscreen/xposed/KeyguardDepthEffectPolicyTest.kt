package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyguardDepthEffectPolicyTest {

    @Before
    fun setUp() {
        KeyguardDepthEffectPolicy.reset()
    }

    @Test
    fun shouldSuppressDepth_whenMusicLockscreenActive() {
        assertTrue(KeyguardDepthEffectPolicy.shouldSuppressDepth(true))
    }

    @Test
    fun shouldSuppressDepth_falseWhenInactive() {
        assertFalse(KeyguardDepthEffectPolicy.shouldSuppressDepth(false))
    }

    @Test
    fun isDepthEffectType_onlyType2() {
        assertTrue(KeyguardDepthEffectPolicy.isDepthEffectType(2))
        assertFalse(KeyguardDepthEffectPolicy.isDepthEffectType(0))
        assertFalse(KeyguardDepthEffectPolicy.isDepthEffectType(1))
        assertFalse(KeyguardDepthEffectPolicy.isDepthEffectType(3))
    }

    @Test
    fun shouldRestoreDepth_whenPanelFieldWasOnEvenIfSettingsZero() {
        assertTrue(
            KeyguardDepthEffectPolicy.shouldRestoreDepth(
                panelDepthEnable = true,
                interactorDepthEnable = false,
                actualDisplayDepth = false,
                depthVideoEnable = false,
                deductedVisibleWithDrawable = false,
                settingsDepthType = 0,
            )
        )
    }

    @Test
    fun shouldRestoreDepth_whenDeductedLayerVisible() {
        assertTrue(
            KeyguardDepthEffectPolicy.shouldRestoreDepth(
                panelDepthEnable = false,
                interactorDepthEnable = false,
                actualDisplayDepth = false,
                depthVideoEnable = false,
                deductedVisibleWithDrawable = true,
                settingsDepthType = 0,
            )
        )
    }

    @Test
    fun shouldRestoreDepth_falseWhenNothingEnabled() {
        assertFalse(
            KeyguardDepthEffectPolicy.shouldRestoreDepth(
                panelDepthEnable = false,
                interactorDepthEnable = false,
                actualDisplayDepth = false,
                depthVideoEnable = false,
                deductedVisibleWithDrawable = false,
                settingsDepthType = 0,
            )
        )
    }

    @Test
    fun beginRestoreHold_keepsSuppressUntilWallpaperRestored() {
        KeyguardDepthEffectPolicy.beginRestoreHold()
        assertTrue(KeyguardDepthEffectPolicy.isHoldingUntilWallpaperRestored())
        assertTrue(KeyguardDepthEffectPolicy.shouldSuppress())
        KeyguardDepthEffectPolicy.onOriginalWallpaperRestored()
        assertFalse(KeyguardDepthEffectPolicy.isHoldingUntilWallpaperRestored())
        assertFalse(KeyguardDepthEffectPolicy.isSuppressed())
    }

    @Test
    fun reset_clearsSuppressedAndSaved() {
        KeyguardDepthEffectPolicy.setSavedDepthEnableForTest(true)
        KeyguardDepthEffectPolicy.suppress()
        assertTrue(KeyguardDepthEffectPolicy.isSuppressed())
        KeyguardDepthEffectPolicy.reset()
        assertFalse(KeyguardDepthEffectPolicy.isSuppressed())
        assertTrue(KeyguardDepthEffectPolicy.savedDepthEnableForTest() == null)
    }

    @Test
    fun release_withoutPanel_clearsFlag() {
        KeyguardDepthEffectPolicy.suppress()
        assertTrue(KeyguardDepthEffectPolicy.isSuppressed())
        KeyguardDepthEffectPolicy.release()
        assertFalse(KeyguardDepthEffectPolicy.isSuppressed())
    }
}
