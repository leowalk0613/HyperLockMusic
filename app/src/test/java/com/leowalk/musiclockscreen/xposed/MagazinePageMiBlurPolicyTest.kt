package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageMiBlurPolicyTest {

    @Test
    fun prefersStablePassBlurMiBlur() {
        assertFalse(MagazinePageMiBlurPolicy.useImmediateAlbumTintPaint())
        // 底栏 TextView 仍开 Pass blur；歌词 Canvas 在 LockscreenLyricView 内禁用
        assertTrue(MagazinePageMiBlurPolicy.enablePassWindowBlur())
        assertTrue(MagazinePageMiBlurPolicy.sampleSiblingContent())
        assertFalse(MagazinePageMiBlurPolicy.hideUntilBlurReady())
        assertFalse(MagazinePageMiBlurPolicy.hideChromeUntilBlurReady())
        assertTrue(MagazinePageMiBlurPolicy.stableRetryCount() >= 5)
        assertEquals(0L, MagazinePageMiBlurPolicy.stableDelayAt(0))
    }

    @Test
    fun magazineContrast_prefersSampledWallpaper() {
        val sampled = 0xFF101010.toInt()
        val def = 0xFF28282C.toInt()
        assertEquals(
            sampled,
            MagazinePageLyricVisualPolicy.magazineContrastBackground(
                sharedContrast = 0xFFFFFFFF.toInt(),
                fogTint = 0xFFFF00FF.toInt(),
                sampledBehindLyrics = sampled,
                defaultColor = def,
            ),
        )
    }
}
