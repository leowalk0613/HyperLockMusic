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
    fun resolveTriplet_keepsExpandedSecondary() {
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
    fun bottomGeometry_currentBottom_stableWhenCurrentGrows() {
        val short = ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = 10f, gapPx = 8f,
            prevHeightPx = 40f, currentHeightPx = 40f,
            secondaryHeightPx = 20f, nextHeightPx = 40f,
        )
        val tall = ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = 10f, gapPx = 8f,
            prevHeightPx = 40f, currentHeightPx = 80f,
            secondaryHeightPx = 20f, nextHeightPx = 40f,
        )
        // 贴同一视口底时：当前主行底边相对视口底的距离应不变
        val viewH = tall.heightPx + 50f
        val shortOrigin = ImmersiveLyricStackPolicy.contentOriginY(viewH, short.heightPx)
        val tallOrigin = ImmersiveLyricStackPolicy.contentOriginY(viewH, tall.heightPx)
        val shortCurrentBottomOnView = shortOrigin + short.currentBottom
        val tallCurrentBottomOnView = tallOrigin + tall.currentBottom
        assertEquals(shortCurrentBottomOnView, tallCurrentBottomOnView, 0.01f)
        // 变高只往上长
        assertTrue(tall.currentTop < short.currentTop + (tall.heightPx - short.heightPx))
    }

    @Test
    fun scrollOffset_enterFromBelow_alreadyExpanded() {
        assertEquals(80f, ImmersiveLyricStackPolicy.scrollOffsetPx(0f, 80f), 0.01f)
        assertEquals(40f, ImmersiveLyricStackPolicy.scrollOffsetPx(0.5f, 80f), 0.01f)
        assertEquals(0f, ImmersiveLyricStackPolicy.scrollOffsetPx(1f, 80f), 0.01f)
    }

    @Test
    fun promotionStep_includesExpandedSecondaryBlock() {
        assertEquals(58f, ImmersiveLyricStackPolicy.promotionStepPx(50f, 0f, 8f), 0.01f)
        assertEquals(86f, ImmersiveLyricStackPolicy.promotionStepPx(50f, 20f, 8f), 0.01f)
    }

    @Test
    fun contentOrigin_padsTopOnly() {
        assertEquals(30f, ImmersiveLyricStackPolicy.contentOriginY(100f, 70f), 0.01f)
        assertEquals(0f, ImmersiveLyricStackPolicy.contentOriginY(70f, 70f), 0.01f)
    }

    @Test
    fun promotionDuration_staysShortForLockscreen() {
        // 锁屏 + MiBlur：长动画更容易掉帧
        assertTrue(ImmersiveLyricStackPolicy.PROMOTION_MS <= 240L)
        assertTrue(ImmersiveLyricStackPolicy.PROMOTION_MS >= 160L)
    }
}
