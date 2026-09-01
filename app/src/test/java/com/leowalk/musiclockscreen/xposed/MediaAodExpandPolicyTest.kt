package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAodExpandPolicyTest {

    @Test
    fun shouldSuppressAnimateHeight_onlyForNonZero() {
        assertFalse(MediaAodExpandPolicy.shouldSuppressAnimateHeight(0))
        assertTrue(MediaAodExpandPolicy.shouldSuppressAnimateHeight(120))
    }

    @Test
    fun shouldForceSnapHeight_whenKeepingExpandedAndAnimateRequested() {
        assertTrue(MediaAodExpandPolicy.shouldForceSnapHeight(true, true))
        assertFalse(MediaAodExpandPolicy.shouldForceSnapHeight(true, false))
        assertFalse(MediaAodExpandPolicy.shouldForceSnapHeight(false, true))
    }

    @Test
    fun shouldKeepExpandedDuringSleepLinkage_coversUnlockThenSleep() {
        assertTrue(
            MediaAodExpandPolicy.shouldKeepExpandedDuringSleepLinkage(
                aodFullMedia = true,
                onKeyguard = false,
                inLinkage = true,
                goingToSleep = true,
            )
        )
        assertFalse(
            MediaAodExpandPolicy.shouldKeepExpandedDuringSleepLinkage(
                aodFullMedia = true,
                onKeyguard = false,
                inLinkage = true,
                goingToSleep = false,
            )
        )
        assertTrue(
            MediaAodExpandPolicy.shouldKeepExpandedDuringSleepLinkage(
                aodFullMedia = true,
                onKeyguard = true,
                inLinkage = false,
                goingToSleep = false,
            )
        )
    }

    @Test
    fun isWakeSleepFolmeType_matchesSystemUiConstant() {
        assertTrue(MediaAodExpandPolicy.isWakeSleepFolmeType(11030))
        assertFalse(MediaAodExpandPolicy.isWakeSleepFolmeType(11018))
    }
}
