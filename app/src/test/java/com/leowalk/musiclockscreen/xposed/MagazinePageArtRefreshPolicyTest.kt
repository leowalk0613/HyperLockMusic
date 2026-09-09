package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageArtRefreshPolicyTest {

    @Test
    fun stableArtKey_ignoresBitmapGeneration() {
        val a = MagazinePageArtRefreshPolicy.buildStableArtKey(
            trackKey = "netease:1",
            packageName = "com.netease.cloudmusic",
            title = "A",
            artist = "B",
            artWidth = 300,
            artHeight = 300,
            style = "big_album",
            wantHd = true,
            bakeFingerprint = "fp",
        )
        val b = MagazinePageArtRefreshPolicy.buildStableArtKey(
            trackKey = "netease:1",
            packageName = "com.netease.cloudmusic",
            title = "A",
            artist = "B",
            artWidth = 300,
            artHeight = 300,
            style = "big_album",
            wantHd = true,
            bakeFingerprint = "fp",
        )
        assertEquals(a, b)
    }

    @Test
    fun shouldStartBake_falseWhenSameKeyEvenIfHdPending() {
        assertFalse(
            MagazinePageArtRefreshPolicy.shouldStartBake(
                stableArtKey = "k",
                lastArtKey = "k",
                wantHd = true,
                hdAppliedForKey = false,
                enhanceInFlight = true,
            ),
        )
        assertFalse(
            MagazinePageArtRefreshPolicy.shouldStartBake(
                stableArtKey = "k",
                lastArtKey = "k",
                wantHd = true,
                hdAppliedForKey = true,
                enhanceInFlight = false,
            ),
        )
    }

    @Test
    fun shouldStartBake_trueOnTrackOrParamChange() {
        assertTrue(
            MagazinePageArtRefreshPolicy.shouldStartBake(
                stableArtKey = "k2",
                lastArtKey = "k1",
                wantHd = true,
                hdAppliedForKey = true,
                enhanceInFlight = false,
            ),
        )
    }

    @Test
    fun shouldFetchHd_skipsWhenAlreadyAppliedOrInFlight() {
        assertFalse(
            MagazinePageArtRefreshPolicy.shouldFetchHd(
                wantHd = true,
                referenceMinSide = 300,
                hdAppliedForKey = true,
                enhanceInFlight = false,
            ),
        )
        assertFalse(
            MagazinePageArtRefreshPolicy.shouldFetchHd(
                wantHd = true,
                referenceMinSide = 300,
                hdAppliedForKey = false,
                enhanceInFlight = true,
            ),
        )
        assertTrue(
            MagazinePageArtRefreshPolicy.shouldFetchHd(
                wantHd = true,
                referenceMinSide = 300,
                hdAppliedForKey = false,
                enhanceInFlight = false,
            ),
        )
    }
}
