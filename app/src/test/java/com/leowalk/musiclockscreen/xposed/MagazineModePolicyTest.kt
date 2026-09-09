package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazineModePolicyTest {

    @Test
    fun forceMagazine_requiresChromeAndShowing() {
        assertTrue(MagazineModePolicy.shouldForceMagazineWallpaper(true, true))
        assertFalse(MagazineModePolicy.shouldForceMagazineWallpaper(true, false))
        assertFalse(MagazineModePolicy.shouldForceMagazineWallpaper(false, true))
    }

    @Test
    fun squareAlbum_hiddenInMagazine() {
        assertFalse(
            MagazineModePolicy.shouldShowSquareAlbum(
                chromeMagazine = true,
                showBigAlbum = true,
                immersiveAlbum = false,
                lyricPriorityHidesAlbum = false,
            ),
        )
        assertTrue(
            MagazineModePolicy.shouldShowSquareAlbum(
                chromeMagazine = false,
                showBigAlbum = true,
                immersiveAlbum = false,
                lyricPriorityHidesAlbum = false,
            ),
        )
    }

    @Test
    fun bakeImmersive_disabledInMagazine() {
        assertFalse(
            MagazineModePolicy.shouldBakeImmersiveAlbum(
                chromeMagazine = true,
                showBigAlbum = true,
                immersiveAlbum = true,
            ),
        )
        assertTrue(
            MagazineModePolicy.shouldBakeImmersiveAlbum(
                chromeMagazine = false,
                showBigAlbum = true,
                immersiveAlbum = true,
            ),
        )
    }

    @Test
    fun lyricOverlay_whenMagazineAndLyricOn() {
        assertTrue(MagazineModePolicy.shouldUseLyricOverlay(true, true, true))
        assertFalse(MagazineModePolicy.shouldUseLyricOverlay(true, true, false))
        assertFalse(MagazineModePolicy.shouldUseLyricOverlay(false, true, true))
    }

    @Test
    fun suppressLeftSwipe_alwaysOff() {
        assertFalse(MagazineModePolicy.shouldSuppressMagazineLeftSwipe(true, true))
        assertFalse(MagazineModePolicy.shouldSuppressMagazineLeftSwipe(true, false))
        assertFalse(MagazineModePolicy.shouldSuppressMagazineLeftSwipe(false, true))
    }

    @Test
    fun resolveChrome_migratesFromImmersiveFlag() {
        assertEquals(
            MagazineModePolicy.CHROME_MAGAZINE,
            MagazineModePolicy.resolveChrome(MagazineModePolicy.CHROME_MAGAZINE, false),
        )
        assertEquals(
            MagazineModePolicy.CHROME_IMMERSIVE,
            MagazineModePolicy.resolveChrome("", true),
        )
        assertEquals(
            MagazineModePolicy.CHROME_BIG_ALBUM,
            MagazineModePolicy.resolveChrome("", false),
        )
    }

    @Test
    fun buildExJson_marksTitleCustomized() {
        val raw = MagazineModePolicy.buildMagazineExJson(source = "Artist")
        assertTrue(raw.contains("\"title_customized\":1") || raw.contains("\"title_customized\": 1"))
        assertTrue(raw.contains("Artist"))
        assertTrue(raw.contains("音乐") || raw.contains("\\u97f3\\u4e50"))
    }
}
