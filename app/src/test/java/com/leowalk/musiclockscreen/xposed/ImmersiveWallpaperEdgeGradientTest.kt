package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveWallpaperEdgeGradientTest {

    @Test
    fun highAlbum_shorterTopSpan_longerBottomSpan() {
        val h = 2400f
        val high = computeImmersiveEdgeGradientSpans(h, albumTop = 400f, albumBottom = 1200f)
        val low = computeImmersiveEdgeGradientSpans(h, albumTop = 900f, albumBottom = 1700f)
        assertTrue(high.topSpanPx < low.topSpanPx)
        assertTrue(high.bottomSpanPx > low.bottomSpanPx)
    }

    @Test
    fun spans_respectMinMaxFractions() {
        val h = 2000f
        val bottomHeavy = computeImmersiveEdgeGradientSpans(h, albumTop = 1900f, albumBottom = 1950f)
        assertEquals(h * 0.55f, bottomHeavy.topSpanPx, 0.01f)
        assertEquals(h * 0.14f, bottomHeavy.bottomSpanPx, 0.01f)

        val topHeavy = computeImmersiveEdgeGradientSpans(h, albumTop = 50f, albumBottom = 100f)
        assertEquals(h * 0.14f, topHeavy.topSpanPx, 0.01f)
        assertEquals(h * 0.55f, topHeavy.bottomSpanPx, 0.01f)
    }

    @Test
    fun peakAlpha_clamped() {
        assertEquals(100, computeImmersiveEdgeGradientPeakAlpha(0))
        assertEquals(230, computeImmersiveEdgeGradientPeakAlpha(400))
        assertEquals(174, computeImmersiveEdgeGradientPeakAlpha(140))
    }
}
