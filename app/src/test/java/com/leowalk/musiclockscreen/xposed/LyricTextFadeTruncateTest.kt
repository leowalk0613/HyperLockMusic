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
    fun visibleTextEndOffset_clipsToMaxLines() {
        val ends = intArrayOf(5, 10, 15)
        assertEquals(
            10,
            LyricTextFadeTruncate.visibleTextEndOffset(
                fullTextLength = 15,
                unrestrictedLineCount = 3,
                maxLines = 2,
            ) { line -> ends[line] },
        )
    }

    @Test
    fun visibleTextEndOffset_whenFitsInFewerLines() {
        val ends = intArrayOf(8)
        assertEquals(
            8,
            LyricTextFadeTruncate.visibleTextEndOffset(
                fullTextLength = 8,
                unrestrictedLineCount = 1,
                maxLines = 2,
            ) { line -> ends[line] },
        )
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
    fun needsEndFadeForLineWidth_whenOverflow() {
        assertTrue(LyricTextFadeTruncate.needsEndFadeForLineWidth(320f, 300f))
        assertFalse(LyricTextFadeTruncate.needsEndFadeForLineWidth(300f, 300f))
    }

    @Test
    fun lastCodePointStart_forBmpChar() {
        assertEquals(2, LyricTextFadeTruncate.lastCodePointStart("abc", 0, 3))
    }

    @Test
    fun trimTrailingWhitespaceEnd_skipsSpacesAndNewline() {
        assertEquals(3, LyricTextFadeTruncate.trimTrailingWhitespaceEnd("abc  \n", 0, 6))
        assertEquals(5, LyricTextFadeTruncate.trimTrailingWhitespaceEnd("hello", 0, 5))
    }

    @Test
    fun resolveFadeWidthPx_expandsPastThinLastGlyph() {
        val text = "Hello"
        val widths = mapOf(
            (4 to 5) to 8f,
            (3 to 5) to 16f,
            (2 to 5) to 28f,
        )
        val fadeW = LyricTextFadeTruncate.resolveFadeWidthPx(
            text = text,
            lineStart = 0,
            lineEnd = 5,
            textSizePx = 20f,
        ) { s, e -> widths[s to e] ?: ((e - s) * 10f) }
        // 单个窄字母不够，往回扩到至少 1.25em
        assertTrue(fadeW >= 20f * LyricTextFadeTruncate.MIN_FADE_EM - 0.01f)
        assertEquals(28f, fadeW, 0.01f)
    }

    @Test
    fun resolveFadeWidthPx_ignoresTrailingWhitespace() {
        val text = "word  "
        val fadeW = LyricTextFadeTruncate.resolveFadeWidthPx(
            text = text,
            lineStart = 0,
            lineEnd = 6,
            textSizePx = 10f,
        ) { s, e ->
            if (e <= 4) (e - s) * 12f else if (s >= 4) 1f else (4 - s) * 12f
        }
        assertTrue(fadeW >= 10f * LyricTextFadeTruncate.MIN_FADE_EM - 0.01f)
    }

    @Test
    fun endFadeGeometry_ltr_clipsToLayoutWidth() {
        val geo = LyricTextFadeTruncate.endFadeGeometry(
            lineLeft = 0f,
            lineRight = 400f,
            layoutWidth = 300f,
            fadeWidthPx = 40f,
            rtl = false,
        )
        assertEquals(0f, geo.layerLeft, 0.01f)
        assertEquals(300f, geo.layerRight, 0.01f)
        assertEquals(260f, geo.fadeOpaqueX, 0.01f)
        assertEquals(300f, geo.fadeTransparentX, 0.01f)
    }

    @Test
    fun endFadeGeometry_rtl_fadesAtLeadingEdge() {
        val geo = LyricTextFadeTruncate.endFadeGeometry(
            lineLeft = 0f,
            lineRight = 300f,
            layoutWidth = 300f,
            fadeWidthPx = 40f,
            rtl = true,
        )
        assertEquals(0f, geo.layerLeft, 0.01f)
        assertEquals(300f, geo.layerRight, 0.01f)
        assertEquals(40f, geo.fadeOpaqueX, 0.01f)
        assertEquals(0f, geo.fadeTransparentX, 0.01f)
    }

    @Test
    fun fadeStartX_coversLastGlyph() {
        assertEquals(150f, LyricTextFadeTruncate.fadeStartX(0f, 200f, 50f), 0.01f)
    }
}
