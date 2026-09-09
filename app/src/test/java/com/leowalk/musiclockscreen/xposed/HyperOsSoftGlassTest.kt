package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperOsSoftGlassTest {

    @Test
    fun applyResult_requiresBothLegacyAndGlass() {
        assertFalse(HyperOsSoftGlass.ApplyResult(legacyOk = true, glassOk = false, glassVia = null).success)
        assertFalse(HyperOsSoftGlass.ApplyResult(legacyOk = false, glassOk = true, glassVia = "x").success)
        assertTrue(HyperOsSoftGlass.ApplyResult(legacyOk = true, glassOk = true, glassVia = "MiGlassCompat").success)
    }

    @Test
    fun spec_defaultsMatchHyperChangerSoftGlass() {
        val s = HyperOsSoftGlass.Spec()
        assertEquals(10, s.opacityPercent)
        assertEquals(80, s.backdropBlurRadius)
        assertEquals(36, s.glassBlurRadius)
        assertEquals(0.14f, s.luminance, 0.001f)
        assertEquals(0xFFFFFFFF.toInt(), s.tintColor)
    }
}
