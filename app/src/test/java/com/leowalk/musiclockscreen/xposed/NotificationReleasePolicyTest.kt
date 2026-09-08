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
    fun releaseWhenFilterInactive_onlyIfStillHidden() {
        assertTrue(
            NotificationReleasePolicy.shouldReleaseWhenFilterInactive(
                shouldFilter = false,
                moduleHidden = true,
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
