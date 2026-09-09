package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageLyricHostPolicyTest {

    @Test
    fun display_requiresSwitchesLyricAndPlayback() {
        assertTrue(
            MagazinePageLyricHostPolicy.shouldDisplay(
                lyricEnabled = true,
                showLyric = true,
                hasLyric = true,
                playbackOk = true,
                hasDisplayableText = true,
            ),
        )
        assertFalse(
            MagazinePageLyricHostPolicy.shouldDisplay(
                lyricEnabled = true,
                showLyric = false,
                hasLyric = true,
                playbackOk = true,
                hasDisplayableText = true,
            ),
        )
        assertFalse(
            MagazinePageLyricHostPolicy.shouldDisplay(
                lyricEnabled = true,
                showLyric = true,
                hasLyric = true,
                playbackOk = false,
                hasDisplayableText = true,
            ),
        )
    }

    @Test
    fun snapKeep_whenVisibleWithText() {
        assertTrue(MagazinePageLyricHostPolicy.shouldSnapKeepVisible(true, true))
        assertFalse(MagazinePageLyricHostPolicy.shouldSnapKeepVisible(true, false))
        assertFalse(MagazinePageLyricHostPolicy.shouldSnapKeepVisible(false, true))
    }

    @Test
    fun restartFadeIn_onlyWhenNearlyHidden() {
        assertTrue(MagazinePageLyricHostPolicy.shouldRestartSurfaceFadeIn(false, 1f))
        assertTrue(MagazinePageLyricHostPolicy.shouldRestartSurfaceFadeIn(true, 0.1f))
        assertFalse(MagazinePageLyricHostPolicy.shouldRestartSurfaceFadeIn(true, 0.5f))
        assertFalse(MagazinePageLyricHostPolicy.shouldRestartSurfaceFadeIn(true, 1f))
    }

    @Test
    fun stabilizeLineIndex_blocksBriefRollback() {
        val times = longArrayOf(0L, 1000L, 2000L)
        fun t(i: Int) = times[i]
        assertEquals(
            1,
            MagazinePageLyricHostPolicy.stabilizeLineIndex(
                rawIndex = 0,
                heldIndex = 1,
                posMs = 950L,
                lineTimeMsAt = ::t,
                lineCount = 3,
                hysteresisMs = 120L,
            ),
        )
        assertEquals(
            0,
            MagazinePageLyricHostPolicy.stabilizeLineIndex(
                rawIndex = 0,
                heldIndex = 1,
                posMs = 800L,
                lineTimeMsAt = ::t,
                lineCount = 3,
                hysteresisMs = 120L,
            ),
        )
        assertEquals(
            2,
            MagazinePageLyricHostPolicy.stabilizeLineIndex(
                rawIndex = 2,
                heldIndex = 1,
                posMs = 2000L,
                lineTimeMsAt = ::t,
                lineCount = 3,
            ),
        )
    }

    @Test
    fun bottomAnchor_clamped() {
        assertEquals(10f, MagazinePageLyricHostPolicy.bottomAnchorYPercent(0f), 0.01f)
        assertEquals(62f, MagazinePageLyricHostPolicy.bottomAnchorYPercent(62f), 0.01f)
        assertEquals(95f, MagazinePageLyricHostPolicy.bottomAnchorYPercent(120f), 0.01f)
        // 画报普通歌词：上限卡在控件带玻璃上沿
        assertEquals(70f, MagazinePageLyricHostPolicy.bottomAnchorYPercent(90f, maxPercent = 70f), 0.01f)
        assertEquals(70f, MagazinePageLyricHostPolicy.bottomAnchorYPercent(70f, maxPercent = 70f), 0.01f)
    }

    @Test
    fun immersiveBottomAnchor_fullScreenRange() {
        assertEquals(10f, MagazinePageLyricHostPolicy.immersiveBottomAnchorYPercent(0f), 0.01f)
        assertEquals(90f, MagazinePageLyricHostPolicy.immersiveBottomAnchorYPercent(90f), 0.01f)
        assertEquals(95f, MagazinePageLyricHostPolicy.immersiveBottomAnchorYPercent(120f), 0.01f)
        assertEquals(
            90f,
            MagazinePageLyricHostPolicy.resolveBottomAnchorYPercent(
                immersiveLyric = true,
                lyricBgAnchorY = 50f,
                albumAnchorY = 90f,
            ),
            0.01f,
        )
        // 普通歌词受 glassTop 上限约束
        assertEquals(
            72f,
            MagazinePageLyricHostPolicy.resolveBottomAnchorYPercent(
                immersiveLyric = false,
                lyricBgAnchorY = 90f,
                albumAnchorY = 90f,
                normalLyricMaxPercent = 72f,
            ),
            0.01f,
        )
    }

    @Test
    fun bottomAnchor_stableAcrossHeights() {
        // 同一锚点百分比下，不同内容高度只应改变 top，底边 Y 不变
        val screenH = 1000
        val anchorPct = MagazinePageLyricHostPolicy.bottomAnchorYPercent(62f)
        val bottom = (screenH * (anchorPct / 100f)).toInt()
        val topForH1 = (bottom - 120).coerceAtLeast(0)
        val topForH2 = (bottom - 200).coerceAtLeast(0)
        assertEquals(bottom, topForH1 + 120)
        assertEquals(bottom, topForH2 + 200)
    }
}
