package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageAlbumSlotPolicyTest {

    @Test
    fun immersiveLyric_hidesBigAlbumWhenPriority() {
        assertFalse(
            MagazinePageAlbumSlotPolicy.shouldBakeBigAlbumForeground(
                pageStyle = MagazinePageStylePolicy.STYLE_BIG_ALBUM,
                immersiveLyric = true,
                hideAlbumForLyricPriority = true,
            ),
        )
        assertTrue(
            MagazinePageAlbumSlotPolicy.shouldBakeBigAlbumForeground(
                pageStyle = MagazinePageStylePolicy.STYLE_BIG_ALBUM,
                immersiveLyric = true,
                hideAlbumForLyricPriority = false,
            ),
        )
    }

    @Test
    fun normalLyric_keepsBigAlbum() {
        // 普通歌词不占专辑槽，即使 priority 标志为 true 仍烘焙大专辑
        assertTrue(
            MagazinePageAlbumSlotPolicy.shouldBakeBigAlbumForeground(
                pageStyle = MagazinePageStylePolicy.STYLE_BIG_ALBUM,
                immersiveLyric = false,
                hideAlbumForLyricPriority = true,
            ),
        )
    }

    @Test
    fun immersivePageStyle_neverBakesBigAlbum() {
        assertFalse(
            MagazinePageAlbumSlotPolicy.shouldBakeBigAlbumForeground(
                pageStyle = MagazinePageStylePolicy.STYLE_IMMERSIVE,
                immersiveLyric = false,
                hideAlbumForLyricPriority = false,
            ),
        )
    }

    @Test
    fun noLyric_restoresBigAlbumOnBigAlbumPage() {
        assertTrue(
            MagazinePageAlbumSlotPolicy.shouldRestoreBigAlbumWhenNoLyric(
                pageStyle = MagazinePageStylePolicy.STYLE_BIG_ALBUM,
                hideAlbumForLyricPriority = false,
            ),
        )
        assertFalse(
            MagazinePageAlbumSlotPolicy.shouldRestoreBigAlbumWhenNoLyric(
                pageStyle = MagazinePageStylePolicy.STYLE_BIG_ALBUM,
                hideAlbumForLyricPriority = true,
            ),
        )
        assertFalse(
            MagazinePageAlbumSlotPolicy.shouldRestoreBigAlbumWhenNoLyric(
                pageStyle = MagazinePageStylePolicy.STYLE_IMMERSIVE,
                hideAlbumForLyricPriority = false,
            ),
        )
    }
}
