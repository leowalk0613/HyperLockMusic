package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWallpaperBlurControllerTest {

    @Test
    fun shouldApply_whenMusicLockscreenActive() {
        assertTrue(SystemWallpaperBlurController.shouldApply(true, immersiveAlbum = false))
        assertFalse(SystemWallpaperBlurController.shouldApply(false, immersiveAlbum = false))
    }

    @Test
    fun shouldApply_falseWhenImmersiveAlbum() {
        assertFalse(SystemWallpaperBlurController.shouldApply(true, immersiveAlbum = true))
    }

    @Test
    fun mapSliderToWallpaperBlurRadius_ends() {
        assertEquals(0, SystemWallpaperBlurController.mapSliderToWallpaperBlurRadius(10f))
        assertEquals(100, SystemWallpaperBlurController.mapSliderToWallpaperBlurRadius(200f))
    }

    @Test
    fun bakeBlurRadius_alwaysLight() {
        val light = SystemWallpaperBlurController.bakeBlurRadius(80f)
        assertTrue(light < 20f)
        assertTrue(light >= 3f)
    }

    @Test
    fun bakeDarkOverlay_lighterThanSlider() {
        val baked = SystemWallpaperBlurController.bakeDarkOverlay(140)
        assertTrue(baked < 140)
        assertTrue(baked <= 60)
        assertEquals(0, SystemWallpaperBlurController.bakeDarkOverlay(0))
    }

    @Test
    fun maskDarkOverlayAlpha_strongerThanBake() {
        val slider = 140
        val bake = SystemWallpaperBlurController.bakeDarkOverlay(slider)
        val mask = SystemWallpaperBlurController.maskDarkOverlayAlpha(slider)
        assertTrue(mask > bake)
        assertTrue(mask <= 180)
    }
}
