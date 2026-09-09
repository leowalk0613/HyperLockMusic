package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAlbumArtFetcherTest {

    @Test
    fun shouldAttemptEnhance_requiresEnabledAndSmallSource() {
        assertTrue(NetworkAlbumArtFetcher.shouldAttemptEnhance(true, 300))
        assertTrue(NetworkAlbumArtFetcher.shouldAttemptEnhance(true, 1079))
        assertFalse(NetworkAlbumArtFetcher.shouldAttemptEnhance(true, 1080))
        assertFalse(NetworkAlbumArtFetcher.shouldAttemptEnhance(true, 1500))
        assertFalse(NetworkAlbumArtFetcher.shouldAttemptEnhance(false, 300))
        assertFalse(NetworkAlbumArtFetcher.shouldAttemptEnhance(true, 0))
    }
}
