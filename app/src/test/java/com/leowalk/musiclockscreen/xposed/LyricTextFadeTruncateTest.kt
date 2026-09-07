package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTextFadeTruncateTest {

    @Test
    fun needsEndFade_whenTextWiderThanBox() {
        assertTrue(LyricTextFadeTruncate.needsEndFade(200f, 100f))
        assertFalse(LyricTextFadeTruncate.needsEndFade(80f, 100f))
    }

    @Test
    fun layoutHasEllipsis_whenAnyLineTruncated() {
        val counts = intArrayOf(0, 3, 0)
        assertTrue(
            LyricTextFadeTruncate.layoutHasEllipsis(counts.size) { counts[it] }
        )
        assertFalse(
            LyricTextFadeTruncate.layoutHasEllipsis(2) { 0 }
        )
    }

    @Test
    fun fadeWidth_scalesWithTextSize() {
        val a = LyricTextFadeTruncate.fadeWidthPx(40f)
        val b = LyricTextFadeTruncate.fadeWidthPx(20f)
        assertTrue(a > b)
        assertEquals(40f * LyricTextFadeTruncate.FADE_EM, a, 0.01f)
    }

    @Test
    fun fadeStartInset_cappedByContentWidth() {
        val inset = LyricTextFadeTruncate.fadeStartInsetPx(100f, 40f)
        assertTrue(inset <= 40f * 0.5f + 0.01f)
    }
}
