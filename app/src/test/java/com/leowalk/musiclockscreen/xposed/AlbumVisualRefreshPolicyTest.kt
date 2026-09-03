package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumVisualRefreshPolicyTest {

    @Test
    fun artRetry_whenWallpaperLagging_rebuildsWallpaper() {
        val action = AlbumVisualRefreshPolicy.decideArtRetry(
            trackKey = "netease:2",
            wallpaperTrackKey = "netease:1",
            hasCachedArt = true,
            fogReady = false,
        )
        assertFalse(action.skipWallpaperRebuild)
        assertTrue(action.refreshAlbumOverlay)
        assertTrue(action.refreshFogTint)
    }

    @Test
    fun artRetry_whenWallpaperCaughtUpButOverlayMissing_refreshesVisualsOnly() {
        val action = AlbumVisualRefreshPolicy.decideArtRetry(
            trackKey = "netease:2",
            wallpaperTrackKey = "netease:2",
            hasCachedArt = true,
            fogReady = false,
        )
        assertTrue(action.skipWallpaperRebuild)
        assertTrue(action.refreshAlbumOverlay)
        assertTrue(action.refreshFogTint)
    }

    @Test
    fun artRetry_whenCaughtUpAndFogReady_skipsFogButMayRefreshAlbum() {
        val action = AlbumVisualRefreshPolicy.decideArtRetry(
            trackKey = "a",
            wallpaperTrackKey = "a",
            hasCachedArt = true,
            fogReady = true,
        )
        assertTrue(action.skipWallpaperRebuild)
        assertTrue(action.refreshAlbumOverlay)
        assertFalse(action.refreshFogTint)
    }

    @Test
    fun coalesceRecover_whenFogMissing() {
        assertTrue(
            AlbumVisualRefreshPolicy.shouldRecoverVisualsOnCoalesce(
                hasCachedArt = true,
                fogReady = false,
                albumOverlayEmpty = false,
            )
        )
    }

    @Test
    fun coalesceRecover_whenOverlayEmpty() {
        assertTrue(
            AlbumVisualRefreshPolicy.shouldRecoverVisualsOnCoalesce(
                hasCachedArt = true,
                fogReady = true,
                albumOverlayEmpty = true,
            )
        )
    }

    @Test
    fun coalesceRecover_skipsWhenVisualsOk() {
        assertFalse(
            AlbumVisualRefreshPolicy.shouldRecoverVisualsOnCoalesce(
                hasCachedArt = true,
                fogReady = true,
                albumOverlayEmpty = false,
            )
        )
    }

    @Test
    fun decideArtRetry_stableShape() {
        val a = AlbumVisualRefreshPolicy.decideArtRetry(null, null, false, false)
        assertEquals(false, a.skipWallpaperRebuild)
        assertEquals(false, a.refreshAlbumOverlay)
    }
}
