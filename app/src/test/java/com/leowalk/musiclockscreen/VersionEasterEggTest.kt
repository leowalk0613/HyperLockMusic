package com.leowalk.musiclockscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionEasterEggTest {

    @Test
    fun triggers_afterFiveTapsWithinWindow() {
        var count = 0
        var last = 0L
        var triggered = false
        repeat(4) { i ->
            val (c, t) = VersionEasterEgg.onTap(count, last, (i + 1) * 100L)
            count = c
            last = (i + 1) * 100L
            triggered = t
            assertFalse(triggered)
        }
        val result = VersionEasterEgg.onTap(count, last, 500L)
        assertTrue(result.second)
        assertTrue(result.first == 0)
    }

    @Test
    fun resetsWhenGapTooLong() {
        val (count, triggered) = VersionEasterEgg.onTap(4, 0L, 3_000L)
        assertFalse(triggered)
        assertTrue(count == 1)
    }
}
