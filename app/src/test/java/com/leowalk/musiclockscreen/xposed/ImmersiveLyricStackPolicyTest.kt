package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveLyricStackPolicyTest {

    @Test
    fun shouldUseStack_requiresAllGates() {
        assertTrue(
            ImmersiveLyricStackPolicy.shouldUseStack(
                immersiveLyric = true,
                stackEnabled = true,
                screenInteractive = true,
            )
        )
        assertFalse(
            ImmersiveLyricStackPolicy.shouldUseStack(
                immersiveLyric = false,
                stackEnabled = true,
                screenInteractive = true,
            )
        )
    }

    @Test
    fun resolveTriplet_keepsSecondaryTranslation() {
        val lines = listOf(
            ImmersiveLyricStackPolicy.LineText("A", "a"),
            ImmersiveLyricStackPolicy.LineText("B", "b"),
            ImmersiveLyricStackPolicy.LineText("C", "c"),
        )
        val raw = ImmersiveLyricStackPolicy.resolveTriplet(lines, 1, swapEnabled = false)
        assertEquals("A", raw.prev)
        assertEquals("B", raw.current)
        assertEquals("b", raw.currentSecondary)
        assertEquals("C", raw.next)
    }

    @Test
    fun bottomGeometry_expandZero_isCurrentOnly() {
        val g = ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = 10f,
            gapPx = 8f,
            prevHeightPx = 40f,
            currentHeightPx = 50f,
            secondaryHeightPx = 20f,
            nextHeightPx = 40f,
            expand = 0f,
        )
        assertEquals(10f * 2 + 50f, g.heightPx, 0.01f)
        assertFalse(g.showPrev)
        assertFalse(g.showSecondary)
        assertFalse(g.showNext)
        // 当前行贴底（在下内边距之上）
        assertEquals(g.heightPx - 10f - 50f, g.currentTop, 0.01f)
    }

    @Test
    fun bottomGeometry_expandFull_growsUpward() {
        val g0 = ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = 10f, gapPx = 8f,
            prevHeightPx = 40f, currentHeightPx = 50f,
            secondaryHeightPx = 20f, nextHeightPx = 40f,
            expand = 0f,
        )
        val g1 = ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = 10f, gapPx = 8f,
            prevHeightPx = 40f, currentHeightPx = 50f,
            secondaryHeightPx = 20f, nextHeightPx = 40f,
            expand = 1f,
        )
        assertTrue(g1.heightPx > g0.heightPx)
        assertTrue(g1.showPrev && g1.showSecondary && g1.showNext)
        // 底边对齐：下一句贴在内容底之上
        assertEquals(g1.heightPx - 10f - 40f, g1.nextTop, 0.01f)
        assertTrue(g1.currentTop < g0.currentTop + (g1.heightPx - g0.heightPx))
    }

    @Test
    fun focusThenExpand_progressSplit() {
        assertEquals(0f, ImmersiveLyricStackPolicy.focusProgress(0f), 0.001f)
        assertEquals(1f, ImmersiveLyricStackPolicy.focusProgress(ImmersiveLyricStackPolicy.FOCUS_FRACTION), 0.001f)
        assertEquals(0f, ImmersiveLyricStackPolicy.expandProgress(ImmersiveLyricStackPolicy.FOCUS_FRACTION), 0.001f)
        assertEquals(1f, ImmersiveLyricStackPolicy.expandProgress(1f), 0.001f)
        assertTrue(ImmersiveLyricStackPolicy.expandProgress(0.8f) in 0f..1f)
    }

    @Test
    fun currentSlide_startsBelow_endsAtRest() {
        val step = 52f
        assertEquals(step, ImmersiveLyricStackPolicy.currentSlideOffsetPx(0f, step), 0.01f)
        assertEquals(0f, ImmersiveLyricStackPolicy.currentSlideOffsetPx(1f, step), 0.01f)
    }

    @Test
    fun scrollStep_isMainLinePlusGap_only() {
        assertEquals(52f, ImmersiveLyricStackPolicy.scrollStepPx(40f, 12f), 0.001f)
    }
}
