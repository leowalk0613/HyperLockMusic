package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWallpaperBlurControllerTest {

    @Test
    fun shouldApply_whenMusicLockscreenActive() {
        assertTrue(
            SystemWallpaperBlurController.shouldApply(
                musicLockscreenActive = true,
                immersiveAlbum = false,
                onKeyguard = true,
            )
        )
        assertFalse(
            SystemWallpaperBlurController.shouldApply(
                musicLockscreenActive = false,
                immersiveAlbum = false,
                onKeyguard = true,
            )
        )
    }

    @Test
    fun shouldApply_falseWhenImmersiveAlbum() {
        assertFalse(
            SystemWallpaperBlurController.shouldApply(
                musicLockscreenActive = true,
                immersiveAlbum = true,
                onKeyguard = true,
            )
        )
    }

    @Test
    fun shouldApply_falseWhenNotOnKeyguard() {
        assertFalse(
            SystemWallpaperBlurController.shouldApply(
                musicLockscreenActive = true,
                immersiveAlbum = false,
                onKeyguard = false,
            )
        )
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
    fun bakeDarkOverlay_staysLight_maskCarriesDensity() {
        val baked = SystemWallpaperBlurController.bakeDarkOverlay(140)
        assertTrue(baked <= 60)
        assertTrue(baked > 0)
        assertTrue(baked < SystemWallpaperBlurController.maskDarkOverlayAlpha(140))
        assertEquals(0, SystemWallpaperBlurController.bakeDarkOverlay(0))
    }

    @Test
    fun maskDarkOverlayAlpha_scalesWithSlider() {
        val mask = SystemWallpaperBlurController.maskDarkOverlayAlpha(140)
        assertTrue(mask <= 180)
        assertTrue(mask > 50)
    }
}

