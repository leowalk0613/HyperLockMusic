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
    fun fixedGap_prevBottomToCurrentTop_constant() {
        val fontH = 40f
        val gap = ImmersiveLyricStackPolicy.fixedGapPx(fontH)
        // 当前 1 行 vs 2 行高时，上一句底边到当前顶边的空隙应相同
        val currentTop1 = 200f
        val currentTop2 = 200f // 顶边对齐比较时顶边相同则空隙相同
        val prevH = 40f
        val prevTop1 = ImmersiveLyricStackPolicy.prevTopPx(currentTop1, prevH, fontH)
        val prevTop2 = ImmersiveLyricStackPolicy.prevTopPx(currentTop2, prevH, fontH)
        assertEquals(gap, currentTop1 - (prevTop1 + prevH), 0.001f)
        assertEquals(gap, currentTop2 - (prevTop2 + prevH), 0.001f)
        assertEquals(prevTop1, prevTop2, 0.001f)
    }

    @Test
    fun scrollStep_includesLineAndGap() {
        val fontH = 40f
        val step = ImmersiveLyricStackPolicy.scrollStepPx(fontH)
        assertEquals(fontH + ImmersiveLyricStackPolicy.fixedGapPx(fontH), step, 0.001f)
    }

    @Test
    fun neighborAlpha_moreTransparentThanFull() {
        assertTrue(ImmersiveLyricStackPolicy.NEIGHBOR_ALPHA < 0.5f)
        assertTrue(ImmersiveLyricStackPolicy.NEIGHBOR_ALPHA > 0f)
    }
}
