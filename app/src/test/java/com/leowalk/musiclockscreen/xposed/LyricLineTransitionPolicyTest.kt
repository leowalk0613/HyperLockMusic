package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricLineTransitionPolicyTest {

    @Test
    fun normalize_defaultsToFade() {
        assertEquals(LyricLineTransitionPolicy.FADE, LyricLineTransitionPolicy.normalize(null))
        assertEquals(LyricLineTransitionPolicy.FADE, LyricLineTransitionPolicy.normalize(""))
        assertEquals(LyricLineTransitionPolicy.FADE, LyricLineTransitionPolicy.normalize("nope"))
        assertEquals(LyricLineTransitionPolicy.SLIDE_LEFT, LyricLineTransitionPolicy.normalize("slide_left"))
    }

    @Test
    fun shouldAnimate_onlyWhenScreenInteractive() {
        assertTrue(LyricLineTransitionPolicy.shouldAnimate(true))
        assertFalse(LyricLineTransitionPolicy.shouldAnimate(false))
    }

    @Test
    fun exitTransform_slideLeft() {
        val t = LyricLineTransitionPolicy.exitTransform(
            LyricLineTransitionPolicy.SLIDE_LEFT, 1f, 40f,
        )
        assertEquals(0f, t.alpha, 0.001f)
        assertEquals(-40f, t.tx, 0.001f)
        assertEquals(0f, t.ty, 0.001f)
    }

    @Test
    fun enterTransform_slideLeft_fromRight() {
        val t = LyricLineTransitionPolicy.enterTransform(
            LyricLineTransitionPolicy.SLIDE_LEFT, 0f, 40f,
        )
        assertEquals(0f, t.alpha, 0.001f)
        assertEquals(40f, t.tx, 0.001f)
    }

    @Test
    fun fade_keepsOffsetZero() {
        val out = LyricLineTransitionPolicy.exitTransform(LyricLineTransitionPolicy.FADE, 0.5f, 40f)
        assertEquals(0.5f, out.alpha, 0.001f)
        assertEquals(0f, out.tx, 0.001f)
        val inn = LyricLineTransitionPolicy.enterTransform(LyricLineTransitionPolicy.FADE, 0.5f, 40f)
        assertEquals(0.5f, inn.alpha, 0.001f)
        assertEquals(0f, inn.tx, 0.001f)
    }

    @Test
    fun smoothstep_softMidpoint() {
        assertEquals(0f, LyricLineTransitionPolicy.smoothstep(0f), 0.001f)
        assertEquals(1f, LyricLineTransitionPolicy.smoothstep(1f), 0.001f)
        assertEquals(0.5f, LyricLineTransitionPolicy.smoothstep(0.5f), 0.001f)
    }

    @Test
    fun slideExit_alphaFinishesAheadOfTravel() {
        val mid = LyricLineTransitionPolicy.exitTransform(
            LyricLineTransitionPolicy.SLIDE_LEFT, 0.5f, 40f,
        )
        // mid 位移约一半，透明度应更低（先淡）
        assertEquals(-20f, mid.tx, 0.01f)
        assertTrue(mid.alpha < 0.5f)
    }
}
