package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.media.session.PlaybackState

class MagazinePageChromePolicyTest {

    @Test
    fun chromeBand_floatsAboveBottomEighth() {
        assertEquals(1f / 8f, MagazinePageChromePolicy.CHROME_BOTTOM_OFFSET_FRACTION, 0.001f)
        assertEquals(125, MagazinePageChromePolicy.chromeBottomOffsetPx(1000))
        val h = MagazinePageChromePolicy.estimatedChromeContentHeightPx(3f, 3f)
        assertEquals(1000 - 125 - h, MagazinePageChromePolicy.chromeBandTopYPx(1000, 3f, 3f))
        assertEquals(h, MagazinePageChromePolicy.chromeBandHeightPx(1000, 3f, 3f))
        val maxPct = MagazinePageChromePolicy.lyricBottomAnchorMaxPercent(1000, 3f, 3f)
        assertEquals((1000 - 125 - h) * 100f / 1000f, maxPct, 0.01f)
    }

    @Test
    fun infoRow_maxWidthEightyPercentCentered() {
        assertEquals(0.80f, MagazinePageChromePolicy.INFO_ROW_MAX_WIDTH_FRACTION, 0.001f)
        assertEquals(800, MagazinePageChromePolicy.infoRowMaxWidthPx(1000))
        assertEquals(0, MagazinePageChromePolicy.infoRowMaxWidthPx(0))
    }

    @Test
    fun controlsRow_maxWidthEightyFivePercent() {
        assertEquals(0.85f, MagazinePageChromePolicy.CONTROLS_ROW_MAX_WIDTH_FRACTION, 0.001f)
        assertEquals(850, MagazinePageChromePolicy.controlsRowMaxWidthPx(1000))
        assertEquals(0, MagazinePageChromePolicy.controlsRowMaxWidthPx(0))
    }

    @Test
    fun transportButtons_fixedSizesAndGaps() {
        assertEquals(
            MagazinePageChromePolicy.CONTROL_BTN_SIZE_DP,
            MagazinePageChromePolicy.EDGE_BTN_SIZE_DP,
        )
        assertEquals(
            MagazinePageChromePolicy.CONTROL_BTN_SIZE_DP,
            MagazinePageChromePolicy.TRANSPORT_BTN_SIZE_DP,
        )
        // 播放略大
        assertTrue(
            MagazinePageChromePolicy.PLAY_BTN_SIZE_DP >
                MagazinePageChromePolicy.CONTROL_BTN_SIZE_DP,
        )
        assertTrue(MagazinePageChromePolicy.CONTROL_BTN_SIZE_DP in 28..36)
        assertTrue(MagazinePageChromePolicy.PLAY_BTN_SIZE_DP in 36..48)
        assertTrue(MagazinePageChromePolicy.CONTROL_GAP_MIN_DP in 8..20)
        assertTrue(MagazinePageChromePolicy.INFO_TO_CONTROLS_GAP_DP in 14..24)
        assertEquals(
            MagazinePageChromePolicy.CONTENT_PAD_DP,
            MagazinePageChromePolicy.CHROME_HORIZONTAL_PAD_DP,
        )
        assertEquals(
            MagazinePageChromePolicy.CONTENT_PAD_TOP_DP,
            MagazinePageChromePolicy.CONTENT_PAD_BOTTOM_DP,
        )
        assertEquals(
            MagazinePageChromePolicy.CONTENT_PAD_TOP_DP,
            MagazinePageChromePolicy.CHROME_HORIZONTAL_PAD_DP,
        )
        val rowMin =
            MagazinePageChromePolicy.CONTROL_BTN_SIZE_DP * 4 +
                MagazinePageChromePolicy.PLAY_BTN_SIZE_DP +
                MagazinePageChromePolicy.CONTROL_GAP_MIN_DP * 4
        assertTrue(rowMin <= 220)
    }

    @Test
    fun titleDisplay_modes() {
        val raw = "歌名（现场版）"
        val def = MagazinePageChromePolicy.resolveTitleDisplay(raw, "default")
        assertEquals(raw, def.first)
        assertFalse(def.third)

        val hide = MagazinePageChromePolicy.resolveTitleDisplay(raw, "hide")
        assertEquals("歌名", hide.first)
        assertFalse(hide.third)

        val line = MagazinePageChromePolicy.resolveTitleDisplay(raw, "line")
        assertEquals("歌名", line.first)
        assertEquals("现场版", line.second)
        assertTrue(line.third)

        val shrink = MagazinePageChromePolicy.resolveTitleDisplay(raw, "shrink")
        assertEquals("歌名", shrink.first)
        assertEquals("现场版", shrink.second)
        assertTrue(MagazinePageChromePolicy.shouldApplyInlineShrink("shrink", shrink.second))
        assertFalse(MagazinePageChromePolicy.shouldApplyInlineShrink("line", "现场版"))
    }

    @Test
    fun displayArtist_fallback() {
        assertEquals("未知艺人", MagazinePageChromePolicy.displayArtist(""))
        assertEquals("A", MagazinePageChromePolicy.displayArtist("A"))
    }

    @Test
    fun playingStates() {
        assertTrue(MagazinePageChromePolicy.isPlaying(PlaybackState.STATE_PLAYING))
        assertFalse(MagazinePageChromePolicy.isPlaying(PlaybackState.STATE_PAUSED))
    }

    @Test
    fun lyricToggleAndAlpha() {
        assertTrue(MagazinePageChromePolicy.nextShowLyric(false))
        assertEquals(1f, MagazinePageChromePolicy.lyricButtonAlpha(true, true), 0f)
    }

    @Test
    fun infoAlbumArt_fixedThreeLineSize() {
        val withoutFlag = MagazinePageChromePolicy.infoAlbumArtSizePx(3f, 3f, includeSubtitle = false)
        val withFlag = MagazinePageChromePolicy.infoAlbumArtSizePx(3f, 3f, includeSubtitle = true)
        // 固定三行高度，参数不影响结果
        assertEquals(withFlag, withoutFlag)
        val expected = (20f * 3f).toInt() + (13f * 3f).toInt() + (3 * 3) +
            (12f * 3f).toInt() + (2 * 3)
        assertEquals(expected, withFlag)
    }
}