package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTitleLineLayoutPolicyTest {

    @Test
    fun shouldShowSubtitle_onlyWhenNonBlank() {
        assertFalse(MediaTitleLineLayoutPolicy.shouldShowSubtitle(""))
        assertFalse(MediaTitleLineLayoutPolicy.shouldShowSubtitle("   "))
        assertTrue(MediaTitleLineLayoutPolicy.shouldShowSubtitle("Live"))
    }

    @Test
    fun resolveSubPx_smallerThanArtist() {
        // 36 * 10/18 = 20；歌手 24 → cap 20；再减 1dp(density=1) → 19
        assertEquals(19, MediaTitleLineLayoutPolicy.resolveSubPx(36f, 24f, density = 1f))
        assertEquals(19, MediaTitleLineLayoutPolicy.resolveSubPx(36f, null, density = 1f))
        assertTrue(MediaTitleLineLayoutPolicy.resolveSubPx(36f, 24f, density = 1f) < 24)
    }

    @Test
    fun subStyle_smallerAndLighterThanArtist() {
        assertEquals(10f / 18f, MediaTitleLineLayoutPolicy.SUB_SIZE_RATIO, 0.0001f)
        assertEquals(160, MediaTitleLineLayoutPolicy.SUB_ALPHA)
        assertTrue(MediaTitleLineLayoutPolicy.SUB_ALPHA < 204)
    }

    @Test
    fun spacing_titleSubtitleUsesNegativeMarginToTighten() {
        // 字体自带空隙，正 margin 几乎无效；收窄靠负 topMargin
        assertTrue(MediaTitleLineLayoutPolicy.SUB_TOP_MARGIN_DP < 0f)
        assertEquals(-4f, MediaTitleLineLayoutPolicy.SUB_TOP_MARGIN_DP, 0.01f)
        assertEquals(0.7f, MediaTitleLineLayoutPolicy.ARTIST_PULL_RATIO, 0.01f)
    }
}
