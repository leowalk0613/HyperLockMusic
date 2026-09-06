package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class MonetPaletteTest {

    @Test
    fun pickMostPopulous_prefersHighestCount() {
        val gray = 0xFF787878.toInt()
        val red = 0xFFDC2828.toInt()
        val accent = 0xFF00FF88.toInt()
        // 红出现最多，即使 accent 更艳
        assertEquals(
            red,
            MonetPalette.pickMostPopulous(
                mapOf(gray to 40, red to 80, accent to 5),
            ),
        )
    }

    @Test
    fun pickMostPopulous_empty_usesFallback() {
        assertEquals(0xFF6750A4.toInt(), MonetPalette.pickMostPopulous(emptyMap()))
    }
}
