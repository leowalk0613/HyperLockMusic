package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAodProgressPolicyTest {

    @Test
    fun shouldInterceptUnregister_onlyWhenKeepAndHasHolder() {
        assertTrue(MediaAodProgressPolicy.shouldInterceptUnregister(true, true))
        assertFalse(MediaAodProgressPolicy.shouldInterceptUnregister(true, false))
        assertFalse(MediaAodProgressPolicy.shouldInterceptUnregister(false, true))
        assertFalse(MediaAodProgressPolicy.shouldInterceptUnregister(false, false))
    }

    @Test
    fun shouldForceRegisterAfterAttach_followsKeepFlag() {
        assertTrue(MediaAodProgressPolicy.shouldForceRegisterAfterAttach(true))
        assertFalse(MediaAodProgressPolicy.shouldForceRegisterAfterAttach(false))
    }

    @Test
    fun shouldBypassPanelAnimating_followsKeepFlag() {
        assertTrue(MediaAodProgressPolicy.shouldBypassPanelAnimating(true))
        assertFalse(MediaAodProgressPolicy.shouldBypassPanelAnimating(false))
    }

    @Test
    fun isProgressObserverClass_matchesOldAndNewNames() {
        assertTrue(
            MediaAodProgressPolicy.isProgressObserverClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl\$seekBarObserver\$1"
            )
        )
        assertTrue(
            MediaAodProgressPolicy.isProgressObserverClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaSeekBarProgressOwner\$progressObserver\$1"
            )
        )
        assertFalse(MediaAodProgressPolicy.isProgressObserverClass("androidx.lifecycle.ForeverObserver"))
        assertFalse(MediaAodProgressPolicy.isProgressObserverClass(""))
    }
}
