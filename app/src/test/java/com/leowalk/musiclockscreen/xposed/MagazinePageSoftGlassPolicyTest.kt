package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageSoftGlassPolicyTest {

    @Test
    fun softGlass_defaultsMatchHyperChangerShortcut() {
        assertTrue(MagazinePageSoftGlassPolicy.enabled())
        assertEquals(10, MagazinePageSoftGlassPolicy.OPACITY_PERCENT)
        assertEquals(80, MagazinePageSoftGlassPolicy.BACKDROP_BLUR_RADIUS)
        assertEquals(36, MagazinePageSoftGlassPolicy.GLASS_BLUR_RADIUS)
        assertEquals(0.14f, MagazinePageSoftGlassPolicy.LUMINANCE, 0.001f)
        assertEquals(1, MagazinePageSoftGlassPolicy.BLUR_MODE)
        assertEquals(101, MagazinePageSoftGlassPolicy.BLEND_MODE)
        assertEquals(0x01FFFFFF, MagazinePageSoftGlassPolicy.seedDrawableColor())
    }

    @Test
    fun cornerRadius_scalesWithDensity() {
        assertEquals(56f, MagazinePageSoftGlassPolicy.cornerRadiusPx(2f), 0.01f)
    }

    @Test
    fun softGlass_hostedByActivityNotChrome() {
        assertTrue(MagazinePageSoftGlassPolicy.enabled())
        assertFalse(MagazinePageSoftGlassPolicy.chromeEmbedsGlassLayer())
    }

    @Test
    fun softGlass_bottomInsetTightToControls() {
        assertEquals(
            MagazinePageSoftGlassPolicy.INSET_HORIZONTAL_DP,
            MagazinePageSoftGlassPolicy.INSET_TOP_DP,
        )
        assertEquals(
            MagazinePageSoftGlassPolicy.INSET_TOP_DP,
            MagazinePageSoftGlassPolicy.INSET_BOTTOM_DP,
        )
    }
}
