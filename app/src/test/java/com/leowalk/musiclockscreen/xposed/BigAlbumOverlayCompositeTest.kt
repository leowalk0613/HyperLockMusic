package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BigAlbumOverlayCompositeTest {

    private fun baseSpec(albumId: Int = 1) = BigAlbumOverlayComposite.Spec(
        layoutW = 1100,
        layoutH = 1100,
        contentSizePx = 1080,
        cornerRadiusPx = 48f,
        shadowBlurPx = 40f,
        shadowOffsetX = 8f,
        shadowOffsetY = 20f,
        padLeft = 40,
        padTop = 40,
        albumIdentity = albumId,
        albumWidth = 1185,
        albumHeight = 1185,
    )

    @Test
    fun cacheKey_stableForSameSpec() {
        val spec = baseSpec()
        assertEquals(
            BigAlbumOverlayComposite.cacheKey(spec),
            BigAlbumOverlayComposite.cacheKey(spec.copy())
        )
    }

    @Test
    fun cacheKey_changesWhenAlbumOrLayoutChanges() {
        val base = baseSpec()
        val key = BigAlbumOverlayComposite.cacheKey(base)
        assertNotEquals(key, BigAlbumOverlayComposite.cacheKey(base.copy(albumIdentity = 2)))
        assertNotEquals(key, BigAlbumOverlayComposite.cacheKey(base.copy(contentSizePx = 900)))
        assertNotEquals(key, BigAlbumOverlayComposite.cacheKey(base.copy(cornerRadiusPx = 32f)))
    }

    @Test
    fun shouldRebuild_onlyWhenKeyDiffers() {
        val spec = baseSpec()
        val key = BigAlbumOverlayComposite.cacheKey(spec)
        assertFalse(BigAlbumOverlayComposite.shouldRebuild(key, spec))
        assertTrue(BigAlbumOverlayComposite.shouldRebuild(0L, spec))
        assertTrue(
            BigAlbumOverlayComposite.shouldRebuild(
                key,
                spec.copy(albumIdentity = 99)
            )
        )
    }

    private fun assertEquals(a: Long, b: Long) {
        org.junit.Assert.assertEquals(a, b)
    }
}
