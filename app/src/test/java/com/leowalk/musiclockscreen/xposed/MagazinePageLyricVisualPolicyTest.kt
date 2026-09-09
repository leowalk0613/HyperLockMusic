package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageLyricVisualPolicyTest {

    @Test
    fun albumTint_allowedOnMagazineWithoutKeyguard() {
        assertTrue(MagazinePageLyricVisualPolicy.allowsAlbumTintEffects(true, false))
        assertTrue(MagazinePageLyricVisualPolicy.allowsAlbumTintEffects(false, true))
        assertFalse(MagazinePageLyricVisualPolicy.allowsAlbumTintEffects(false, false))
    }

    @Test
    fun acceptTint_magazineExplicitSourceIgnoresTrackKeyMismatch() {
        assertTrue(
            MagazinePageLyricVisualPolicy.shouldAcceptExtractedTint(
                magazinePageHost = true,
                hasExplicitSourceAlbum = true,
                expectedKey = "a",
                cachedTrackKey = "b",
            ),
        )
        assertFalse(
            MagazinePageLyricVisualPolicy.shouldAcceptExtractedTint(
                magazinePageHost = false,
                hasExplicitSourceAlbum = true,
                expectedKey = "a",
                cachedTrackKey = "b",
            ),
        )
        assertTrue(
            MagazinePageLyricVisualPolicy.shouldAcceptExtractedTint(
                magazinePageHost = false,
                hasExplicitSourceAlbum = false,
                expectedKey = "a",
                cachedTrackKey = "a",
            ),
        )
    }

    @Test
    fun preserveTopMargin_onlyMagazine() {
        assertTrue(MagazinePageLyricVisualPolicy.shouldPreservePinnedTopMargin(true))
        assertFalse(MagazinePageLyricVisualPolicy.shouldPreservePinnedTopMargin(false))
    }

    @Test
    fun miSans_onMagazineEvenWhenNotImmersive() {
        assertTrue(MagazinePageLyricVisualPolicy.shouldUseMiSansTypeface(true, false))
        assertTrue(MagazinePageLyricVisualPolicy.shouldUseMiSansTypeface(false, true))
        assertFalse(MagazinePageLyricVisualPolicy.shouldUseMiSansTypeface(false, false))
    }

    @Test
    fun wallpaperTint_deferredToActivityOnMagazine() {
        assertTrue(MagazinePageLyricVisualPolicy.shouldDeferWallpaperTintExtractToActivity(true))
        assertFalse(MagazinePageLyricVisualPolicy.shouldDeferWallpaperTintExtractToActivity(false))
    }

    @Test
    fun magazineContrast_prefersSharedContrast() {
        val contrast = 0xFFCCCCCC.toInt()
        val fog = 0xFFAA44AA.toInt()
        val sampled = 0xFF222222.toInt()
        val def = 0xFF28282C.toInt()
        assertEquals(
            contrast,
            MagazinePageLyricVisualPolicy.magazineContrastBackground(contrast, fog, sampled, def),
        )
        assertEquals(
            sampled,
            MagazinePageLyricVisualPolicy.magazineContrastBackground(null, fog, sampled, def),
        )
        assertEquals(
            fog,
            MagazinePageLyricVisualPolicy.magazineContrastBackground(null, fog, null, def),
        )
        assertEquals(
            def,
            MagazinePageLyricVisualPolicy.magazineContrastBackground(null, null, null, def),
        )
    }
}
