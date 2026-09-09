package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageLyricPolicyTest {

    @Test
    fun showLyric_requiresBothSwitches() {
        assertTrue(MagazinePageLyricPolicy.shouldShowLyric(true, true))
        assertFalse(MagazinePageLyricPolicy.shouldShowLyric(true, false))
        assertFalse(MagazinePageLyricPolicy.shouldShowLyric(false, true))
    }

    @Test
    fun findLineIndex_binarySearch() {
        val lines = listOf(
            MagazinePageLyricPolicy.TimedLine(0, "a"),
            MagazinePageLyricPolicy.TimedLine(1000, "b"),
            MagazinePageLyricPolicy.TimedLine(2000, "c"),
        )
        assertEquals(-1, MagazinePageLyricPolicy.findLineIndex(lines, -1))
        assertEquals(0, MagazinePageLyricPolicy.findLineIndex(lines, 0))
        assertEquals(0, MagazinePageLyricPolicy.findLineIndex(lines, 999))
        assertEquals(1, MagazinePageLyricPolicy.findLineIndex(lines, 1000))
        assertEquals(2, MagazinePageLyricPolicy.findLineIndex(lines, 5000))
    }

    @Test
    fun resolveDisplayIndex_clampsPreludeToFirst() {
        val lines = listOf(MagazinePageLyricPolicy.TimedLine(500, "a"))
        assertEquals(0, MagazinePageLyricPolicy.resolveDisplayIndex(lines, 0))
        assertEquals(0, MagazinePageLyricPolicy.resolveDisplayIndex(lines, 800))
    }
}
