package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMotionPolicyTest {

    @Test
    fun stackScroll_usesAmllSettleWindow() {
        assertTrue(LyricMotionPolicy.STACK_SCROLL_MS >= 280L)
        assertTrue(LyricMotionPolicy.STACK_SCROLL_MS <= 900L)
        assertEquals(
            AmllSpringMotion.settleMs(AmllSpringMotion.POS_Y),
            LyricMotionPolicy.STACK_SCROLL_MS,
        )
    }

    @Test
    fun lineEnter_longerThanExit() {
        assertTrue(LyricMotionPolicy.LINE_ENTER_MS > LyricMotionPolicy.LINE_EXIT_MS)
    }

    @Test
    fun lineSlidePx_scalesWithDensity() {
        assertEquals(24f, LyricMotionPolicy.lineSlidePx(1f), 0.01f)
        assertEquals(72f, LyricMotionPolicy.lineSlidePx(3f), 0.01f)
    }

    @Test
    fun easeOut_endsSoft() {
        val ease = LyricMotionPolicy.easeOut()
        assertEquals(0f, ease.getInterpolation(0f), 0.01f)
        assertEquals(1f, ease.getInterpolation(1f), 0.01f)
        assertTrue(ease.getInterpolation(0.5f) > 0.7f)
    }

    @Test
    fun easeIn_startsSlow() {
        val ease = LyricMotionPolicy.easeIn()
        assertEquals(0f, ease.getInterpolation(0f), 0.01f)
        assertEquals(1f, ease.getInterpolation(1f), 0.01f)
        assertTrue(ease.getInterpolation(0.5f) < 0.4f)
    }

    @Test
    fun springSlide_settlesToOne() {
        val ease = LyricMotionPolicy.springSlide()
        assertEquals(0f, ease.getInterpolation(0f), 0.01f)
        assertEquals(1f, ease.getInterpolation(1f), 0.01f)
        assertTrue(ease.getInterpolation(0.4f) > 0.45f)
    }

    @Test
    fun slideModes_pickDistinctCurvesFromFade() {
        val slideMid = LyricMotionPolicy.forLineExit(LyricLineTransitionPolicy.SLIDE_LEFT)
            .getInterpolation(0.5f)
        val fadeMid = LyricMotionPolicy.forLineExit(LyricLineTransitionPolicy.FADE)
            .getInterpolation(0.5f)
        assertTrue(kotlin.math.abs(slideMid - fadeMid) > 0.05f)
    }
}
