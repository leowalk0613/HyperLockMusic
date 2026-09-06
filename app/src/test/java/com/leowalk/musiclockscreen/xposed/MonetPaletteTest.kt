package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class MonetPaletteTest {

    @Test
    fun pickMostChromatic_prefersSaturated() {
        val gray = 0xFF787878.toInt()
        val red = 0xFFDC2828.toInt()
        assertEquals(red, MonetPalette.pickMostChromatic(gray, red, null))
    }

    @Test
    fun pickMostChromatic_allNull_usesFallback() {
        assertEquals(0xFF6750A4.toInt(), MonetPalette.pickMostChromatic(null, null))
    }
}
