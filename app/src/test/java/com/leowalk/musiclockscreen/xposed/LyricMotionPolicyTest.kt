package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMotionPolicyTest {

    @Test
    fun stackScroll_longerSoftWindow() {
        assertTrue(LyricMotionPolicy.STACK_SCROLL_MS >= 400L)
    }

    @Test
    fun lineEnter_longerThanExit() {
        assertTrue(LyricMotionPolicy.LINE_ENTER_MS > LyricMotionPolicy.LINE_EXIT_MS)
    }

    @Test
    fun lineSlidePx_scalesWithDensity() {
        assertEquals(28f, LyricMotionPolicy.lineSlidePx(1f), 0.01f)
        assertEquals(84f, LyricMotionPolicy.lineSlidePx(3f), 0.01f)
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
    fun easeInOut_softMid() {
        val ease = LyricMotionPolicy.easeInOut()
        assertEquals(0f, ease.getInterpolation(0f), 0.01f)
        assertEquals(1f, ease.getInterpolation(1f), 0.01f)
        assertEquals(0.5f, ease.getInterpolation(0.5f), 0.01f)
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
