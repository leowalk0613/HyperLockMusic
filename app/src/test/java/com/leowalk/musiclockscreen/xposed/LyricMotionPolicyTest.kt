package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMotionPolicyTest {

    @Test
    fun stackScroll_usesSoftUiSlide() {
        assertTrue(LyricMotionPolicy.STACK_SCROLL_MS >= 240L)
        assertTrue(LyricMotionPolicy.STACK_SCROLL_MS <= 420L)
        assertEquals(
            AmllSpringMotion.settleMs(AmllSpringMotion.UI_SLIDE),
            LyricMotionPolicy.STACK_SCROLL_MS,
        )
    }

    @Test
    fun lineEnter_longerThanExit_totalUnder600ms() {
        assertTrue(LyricMotionPolicy.LINE_ENTER_MS > LyricMotionPolicy.LINE_EXIT_MS)
        val sequential = LyricMotionPolicy.LINE_EXIT_MS + LyricMotionPolicy.LINE_ENTER_MS
        assertTrue("sequential=$sequential", sequential <= 560L)
    }

    @Test
    fun slotAndSurface_shorterThanOrEqualStack() {
        assertTrue(LyricMotionPolicy.SLOT_CROSSFADE_MS <= LyricMotionPolicy.STACK_SCROLL_MS)
        assertTrue(LyricMotionPolicy.LYRIC_SURFACE_EXIT_MS <= LyricMotionPolicy.STACK_SCROLL_MS)
    }

    @Test
    fun lineSlidePx_scalesWithDensity() {
        assertEquals(24f, LyricMotionPolicy.lineSlidePx(1f), 0.01f)
        assertEquals(72f, LyricMotionPolicy.lineSlidePx(3f), 0.01f)
    }

    @Test
    fun springSlide_settlesToOne_noOvershoot() {
        val ease = LyricMotionPolicy.springSlide()
        assertEquals(0f, ease.getInterpolation(0f), 0.01f)
        assertEquals(1f, ease.getInterpolation(1f), 0.01f)
        assertTrue(ease.getInterpolation(0.4f) > 0.45f)
        assertTrue(ease.getInterpolation(0.6f) <= 1f)
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
