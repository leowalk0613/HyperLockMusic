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
    fun needsEndFadeForClippedLayout_whenLinesDropped() {
        assertTrue(
            LyricTextFadeTruncate.needsEndFadeForClippedLayout(
                unrestrictedLineCount = 4,
                clippedLineCount = 2,
                maxLines = 2,
                lastLineWidthPx = 50f,
                contentWidthPx = 200f,
            )
        )
    }

    @Test
    fun needsEndFadeForClippedLayout_falseWhenFullyVisible() {
        assertFalse(
            LyricTextFadeTruncate.needsEndFadeForClippedLayout(
                unrestrictedLineCount = 2,
                clippedLineCount = 2,
                maxLines = 3,
                lastLineWidthPx = 80f,
                contentWidthPx = 200f,
            )
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
        assertTrue(inset <= 40f * 0.45f + 0.01f)
    }
}
