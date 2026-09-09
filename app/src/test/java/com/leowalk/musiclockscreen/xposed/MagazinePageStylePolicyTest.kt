package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazinePageStylePolicyTest {

    @Test
    fun resolvePageStyle_defaultsToBigAlbum() {
        assertEquals(
            MagazinePageStylePolicy.STYLE_BIG_ALBUM,
            MagazinePageStylePolicy.resolvePageStyle(null),
        )
        assertEquals(
            MagazinePageStylePolicy.STYLE_BIG_ALBUM,
            MagazinePageStylePolicy.resolvePageStyle("magazine"),
        )
        assertEquals(
            MagazinePageStylePolicy.STYLE_IMMERSIVE,
            MagazinePageStylePolicy.resolvePageStyle("immersive"),
        )
    }

    @Test
    fun foregroundAlbum_onlyForBigAlbumStyle() {
        assertTrue(MagazinePageStylePolicy.shouldShowForegroundAlbum("big_album"))
        assertFalse(MagazinePageStylePolicy.shouldShowForegroundAlbum("immersive"))
        assertTrue(MagazinePageStylePolicy.shouldBakeImmersive("immersive"))
        assertFalse(MagazinePageStylePolicy.shouldBakeImmersive("big_album"))
    }

    @Test
    fun bigAlbumTop_usesAnchorAsBottomEdge() {
        // 屏高 2000，专辑 400，底边 70% → bottom=1400 → top=1000
        assertEquals(1000, MagazinePageStylePolicy.bigAlbumTopPx(2000, 400, 70f))
        assertEquals(540, MagazinePageStylePolicy.albumSizePx(1080, 50f))
    }

    @Test
    fun offsetFromAnchor_matchesCenteredBaseline() {
        val tw = 1080
        val th = 2400
        val sizePct = 50f
        val albumSize = MagazinePageStylePolicy.albumSizePx(tw, sizePct)
        val centeredTop = (th - albumSize) / 2f
        // 底边刚好让 top = centeredTop → offset ≈ 0
        val anchorForCenter = ((centeredTop + albumSize) / th) * 100f
        val offset = MagazinePageStylePolicy.albumOffsetYDpFromAnchor(
            tw, th, sizePct, anchorForCenter,
        )
        assertTrue(kotlin.math.abs(offset) < 1f)
    }
}
