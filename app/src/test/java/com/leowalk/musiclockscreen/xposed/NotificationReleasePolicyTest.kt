package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReleasePolicyTest {

    @Test
    fun releaseOnShade_whenModuleHidden() {
        assertTrue(NotificationReleasePolicy.shouldReleaseOnStatusShade(moduleHidden = true))
        assertFalse(NotificationReleasePolicy.shouldReleaseOnStatusShade(moduleHidden = false))
    }

    @Test
    fun activelyHide_onlyOnCleanMusicKeyguard() {
        assertTrue(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                onKeyguard = true,
                notificationShadeOpen = false,
                controlCenterOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                onKeyguard = true,
                notificationShadeOpen = true,
                controlCenterOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                onKeyguard = true,
                notificationShadeOpen = false,
                controlCenterOpen = true,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldActivelyHideNotifications(
                musicWallpaperShowing = true,
                onKeyguard = false,
                notificationShadeOpen = false,
                controlCenterOpen = false,
            )
        )
    }

    @Test
    fun releaseWhenFilterInactive_skipsTemporaryPanel() {
        assertTrue(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                moduleHidden = true,
                temporaryPanelOpen = false,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                moduleHidden = true,
                temporaryPanelOpen = true,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = true,
                moduleHidden = true,
            )
        )
        assertFalse(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                moduleHidden = false,
            )
        )
    }
}
