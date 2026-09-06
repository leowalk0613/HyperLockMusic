package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWallpaperBlurControllerTest {

    @Test
    fun shouldApply_requiresSwitchAndMusicLockscreen() {
        assertTrue(SystemWallpaperBlurController.shouldApply(true, true))
        assertFalse(SystemWallpaperBlurController.shouldApply(false, true))
        assertFalse(SystemWallpaperBlurController.shouldApply(true, false))
    }

    @Test
    fun mapSliderToWallpaperBlurRadius_ends() {
        assertEquals(0, SystemWallpaperBlurController.mapSliderToWallpaperBlurRadius(10f))
        assertEquals(100, SystemWallpaperBlurController.mapSliderToWallpaperBlurRadius(200f))
    }

    @Test
    fun bakeBlurRadius_lightWhenSystemBlurOn() {
        val light = SystemWallpaperBlurController.bakeBlurRadius(80f, true)
        assertTrue(light < 20f)
        assertEquals(80f, SystemWallpaperBlurController.bakeBlurRadius(80f, false), 0.01f)
    }
}
