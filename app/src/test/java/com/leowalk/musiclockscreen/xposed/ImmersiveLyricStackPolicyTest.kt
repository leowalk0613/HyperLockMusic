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
        assertFalse(
            ImmersiveLyricStackPolicy.shouldUseStack(
                immersiveLyric = true,
                stackEnabled = false,
                screenInteractive = true,
            )
        )
        assertFalse(
            ImmersiveLyricStackPolicy.shouldUseStack(
                immersiveLyric = true,
                stackEnabled = true,
                screenInteractive = false,
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

        val swapped = ImmersiveLyricStackPolicy.resolveTriplet(lines, 1, swapEnabled = true)
        assertEquals("a", swapped.prev)
        assertEquals("b", swapped.current)
        assertEquals("B", swapped.currentSecondary)
        assertEquals("c", swapped.next)
    }

    @Test
    fun resolveTriplet_noSecondaryWithoutTranslation() {
        val lines = listOf(ImmersiveLyricStackPolicy.LineText("Only"))
        val t = ImmersiveLyricStackPolicy.resolveTriplet(lines, 0, swapEnabled = true)
        assertEquals("", t.prev)
        assertEquals("Only", t.current)
        assertEquals("", t.currentSecondary)
        assertEquals("", t.next)
    }

    @Test
    fun prevGap_matchesSharedLineGap() {
        // 上一句↔当前 与 翻译↔下一句 共用同一 gap（如 lineGapPx）
        val gap = 12f
        val currentTop = 200f
        val prevH = 40f
        val prevTop = ImmersiveLyricStackPolicy.prevTopPx(currentTop, prevH, gap)
        assertEquals(gap, currentTop - (prevTop + prevH), 0.001f)
    }

    @Test
    fun scrollStep_includesLineAndGap() {
        val fontH = 40f
        val gap = 12f
        val step = ImmersiveLyricStackPolicy.scrollStepPx(fontH, gap)
        assertEquals(fontH + gap, step, 0.001f)
    }

    @Test
    fun neighborAlpha_moreTransparentThanFull() {
        assertTrue(ImmersiveLyricStackPolicy.NEIGHBOR_ALPHA < 0.5f)
        assertTrue(ImmersiveLyricStackPolicy.NEIGHBOR_ALPHA > 0f)
    }
}
