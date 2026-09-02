package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveAlbumLayoutTest {

    @Test
    fun bakeFalse_nanCenters_doNotCountAsChanged() {
        assertFalse(
            immersiveAlbumLayoutChanged(
                bakeBefore = false,
                bakeAfter = false,
                centerBefore = Float.NaN,
                centerAfter = Float.NaN
            )
        )
    }

    @Test
    fun bakeToggle_countsAsChanged() {
        assertTrue(
            immersiveAlbumLayoutChanged(
                bakeBefore = false,
                bakeAfter = true,
                centerBefore = Float.NaN,
                centerAfter = 0.5f
            )
        )
        assertTrue(
            immersiveAlbumLayoutChanged(
                bakeBefore = true,
                bakeAfter = false,
                centerBefore = 0.5f,
                centerAfter = Float.NaN
            )
        )
    }

    @Test
    fun bakeTrue_centerChange_counts() {
        assertTrue(
            immersiveAlbumLayoutChanged(
                bakeBefore = true,
                bakeAfter = true,
                centerBefore = 0.4f,
                centerAfter = 0.6f
            )
        )
        assertFalse(
            immersiveAlbumLayoutChanged(
                bakeBefore = true,
                bakeAfter = true,
                centerBefore = 0.5f,
                centerAfter = 0.5f
            )
        )
    }

    @Test
    fun bakeTrue_edgeGradientToggle_counts() {
        assertTrue(
            immersiveAlbumLayoutChanged(
                bakeBefore = true,
                bakeAfter = true,
                centerBefore = 0.5f,
                centerAfter = 0.5f,
                edgeGradientBefore = true,
                edgeGradientAfter = false,
            )
        )
    }
}
