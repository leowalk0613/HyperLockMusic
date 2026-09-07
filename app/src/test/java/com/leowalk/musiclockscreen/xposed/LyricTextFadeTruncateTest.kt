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
    fun needsEndFadeForClippedLayout_whenTextRemains() {
        assertTrue(
            LyricTextFadeTruncate.needsEndFadeForClippedLayout(
                unrestrictedLineCount = 2,
                clippedLineCount = 2,
                maxLines = 2,
                clippedTextEndOffset = 8,
                fullTextLength = 20,
            )
        )
    }

    @Test
    fun needsEndFadeForClippedLayout_falseWhenFullyShown() {
        assertFalse(
            LyricTextFadeTruncate.needsEndFadeForClippedLayout(
                unrestrictedLineCount = 2,
                clippedLineCount = 2,
                maxLines = 3,
                clippedTextEndOffset = 10,
                fullTextLength = 10,
            )
        )
    }

    @Test
    fun lastCodePointStart_forBmpChar() {
        assertEquals(2, LyricTextFadeTruncate.lastCodePointStart("abc", 0, 3))
    }

    @Test
    fun fadeStartX_coversLastGlyph() {
        assertEquals(150f, LyricTextFadeTruncate.fadeStartX(0f, 200f, 50f), 0.01f)
    }
}
