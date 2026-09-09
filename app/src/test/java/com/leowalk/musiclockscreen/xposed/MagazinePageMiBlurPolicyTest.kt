package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageMiBlurPolicyTest {

    @Test
    fun prefersStablePassBlurMiBlur() {
        assertFalse(MagazinePageMiBlurPolicy.useImmediateAlbumTintPaint())
        assertTrue(MagazinePageMiBlurPolicy.enablePassWindowBlur())
        assertTrue(MagazinePageMiBlurPolicy.sampleSiblingContent())
        assertFalse(MagazinePageMiBlurPolicy.hideUntilBlurReady())
        assertFalse(MagazinePageMiBlurPolicy.hideChromeUntilBlurReady())
        assertTrue(MagazinePageMiBlurPolicy.stableRetryCount() >= 5)
        assertEquals(0L, MagazinePageMiBlurPolicy.stableDelayAt(0))
    }
}
