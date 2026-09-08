package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReleasePolicyTest {

    @Test
    fun activelyHide_onlyOnCleanMusicKeyguard() {
        assertTrue(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                statusBarState = NotificationReleasePolicy.STATUS_KEYGUARD,
                controlCenterOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                statusBarState = NotificationReleasePolicy.STATUS_SHADE,
                controlCenterOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                statusBarState = NotificationReleasePolicy.STATUS_SHADE_LOCKED,
                controlCenterOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                statusBarState = NotificationReleasePolicy.STATUS_KEYGUARD,
                controlCenterOpen = true,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = false,
                statusBarState = NotificationReleasePolicy.STATUS_KEYGUARD,
                controlCenterOpen = false,
            )
        )
    }

    @Test
    fun releaseOnShadeAndShadeLocked_whenIntervening() {
        assertTrue(
            NotificationReleasePolicy.shouldReleaseOnStatusBarState(
                NotificationReleasePolicy.STATUS_SHADE,
                NotificationReleasePolicy.HidePhase.HIDDEN,
            )
        )
        assertTrue(
            NotificationReleasePolicy.shouldReleaseOnStatusBarState(
                NotificationReleasePolicy.STATUS_SHADE_LOCKED,
                NotificationReleasePolicy.HidePhase.PANEL_CC,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseOnStatusBarState(
                NotificationReleasePolicy.STATUS_KEYGUARD,
                NotificationReleasePolicy.HidePhase.HIDDEN,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseOnStatusBarState(
                NotificationReleasePolicy.STATUS_SHADE,
                NotificationReleasePolicy.HidePhase.IDLE,
            )
        )
    }

    @Test
    fun releaseWhenFilterInactive_skipsTemporaryPanel() {
        assertTrue(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                phase = NotificationReleasePolicy.HidePhase.HIDDEN,
                temporaryPanelOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                phase = NotificationReleasePolicy.HidePhase.HIDDEN,
                temporaryPanelOpen = true,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                phase = NotificationReleasePolicy.HidePhase.IDLE,
                temporaryPanelOpen = false,
            )
        )
    }

    @Test
    fun phaseAfterControlCenter_keepsGoneWhileExpanded() {
        assertEquals(
            NotificationReleasePolicy.HidePhase.PANEL_CC,
            NotificationReleasePolicy.phaseAfterControlCenter(
                expanded = true,
                musicWallpaperShowing = true,
                statusBarState = NotificationReleasePolicy.STATUS_KEYGUARD,
                current = NotificationReleasePolicy.HidePhase.HIDDEN,
            ),
        )
        assertEquals(
            NotificationReleasePolicy.HidePhase.HIDDEN,
            NotificationReleasePolicy.phaseAfterControlCenter(
                expanded = false,
                musicWallpaperShowing = true,
                statusBarState = NotificationReleasePolicy.STATUS_KEYGUARD,
                current = NotificationReleasePolicy.HidePhase.PANEL_CC,
            ),
        )
    }

    @Test
    fun phaseAfterStatusBarState_shadeReleases() {
        assertEquals(
            NotificationReleasePolicy.HidePhase.RELEASED,
            NotificationReleasePolicy.phaseAfterStatusBarState(
                newState = NotificationReleasePolicy.STATUS_SHADE,
                musicWallpaperShowing = true,
                controlCenterOpen = false,
                current = NotificationReleasePolicy.HidePhase.HIDDEN,
            ),
        )
    }
}
