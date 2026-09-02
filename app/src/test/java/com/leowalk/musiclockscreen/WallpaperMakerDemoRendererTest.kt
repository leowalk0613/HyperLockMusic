package com.leowalk.musiclockscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperMakerDemoRendererTest {

    @Test
    fun albumTopPx_centersCoverOnCanvas() {
        val cover = 777
        val top = WallpaperMakerDemoRenderer.albumTopPx(
            canvasHeight = 2400,
            coverSide = cover,
            centerYPercent = 38f,
        )
        assertEquals(2400 * 0.38f - cover / 2f, top, 0.01f)
    }

    @Test
    fun demoCanvas_usesFixedPhoneAspectRatio() {
        assertEquals(1080, WallpaperMakerDemoRenderer.DEMO_WIDTH)
        assertEquals(2400, WallpaperMakerDemoRenderer.DEMO_HEIGHT)
    }
}
