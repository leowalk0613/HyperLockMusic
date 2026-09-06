package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlurUtilsSoftColorTest {

    @Test
    fun softColorDownsample_preservesPortraitAspect() {
        val srcW = 1080
        val srcH = 2400
        val (sw, sh) = BlurUtils.softColorDownsampleSize(srcW, srcH, radius = 80f)
        assertTrue("must stay portrait", sw < sh)
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = sw.toFloat() / sh
        assertTrue(
            "aspect drift too large: src=$srcAspect dst=$dstAspect ($sw x $sh)",
            abs(srcAspect - dstAspect) / srcAspect < 0.05f,
        )
    }

    @Test
    fun softColorDownsample_preservesLandscapeAspect() {
        val srcW = 2400
        val srcH = 1080
        val (sw, sh) = BlurUtils.softColorDownsampleSize(srcW, srcH, radius = 80f)
        assertTrue("must stay landscape", sw > sh)
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = sw.toFloat() / sh
        assertTrue(
            "aspect drift too large: src=$srcAspect dst=$dstAspect ($sw x $sh)",
            abs(srcAspect - dstAspect) / srcAspect < 0.05f,
        )
    }

    @Test
    fun softColorDownsample_squareStaysSquare() {
        val (sw, sh) = BlurUtils.softColorDownsampleSize(1024, 1024, radius = 40f)
        assertEquals(sw, sh)
    }

    @Test
    fun softColorDownsample_keepsEnoughResolutionAgainstMosaic() {
        val (sw, sh) = BlurUtils.softColorDownsampleSize(1080, 2400, radius = 14f)
        assertTrue("max side too small → mosaic: $sw x $sh", maxOf(sw, sh) >= 160)
    }
}
