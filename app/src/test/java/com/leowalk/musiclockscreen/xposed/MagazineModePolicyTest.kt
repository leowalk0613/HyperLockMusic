package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagazineModePolicyTest {

    @Test
    fun forceMagazine_never_toProtectLockscreenMatchedAod() {
        // 强制 gallery 会让息屏走自定义/万象 AOD，画报模式也不再强制
        assertFalse(MagazineModePolicy.shouldForceMagazineWallpaper(true, true))
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
    fun redirectLeftSwipe_requiresMagazineAndMusic() {
        assertTrue(MagazineModePolicy.shouldRedirectMagazineLeftSwipe(true, true))
        assertFalse(MagazineModePolicy.shouldRedirectMagazineLeftSwipe(true, false))
        assertFalse(MagazineModePolicy.shouldRedirectMagazineLeftSwipe(false, true))
        assertFalse(MagazineModePolicy.shouldRedirectMagazineLeftSwipe(false, false))
    }

    @Test
    fun usableMusicPlaybackState_rejectsStoppedAndNull() {
        assertTrue(
            MagazineModePolicy.isUsableMusicPlaybackState(
                android.media.session.PlaybackState.STATE_PLAYING,
            ),
        )
        assertTrue(
            MagazineModePolicy.isUsableMusicPlaybackState(
                android.media.session.PlaybackState.STATE_PAUSED,
            ),
        )
        assertFalse(MagazineModePolicy.isUsableMusicPlaybackState(null))
        assertFalse(
            MagazineModePolicy.isUsableMusicPlaybackState(
                android.media.session.PlaybackState.STATE_STOPPED,
            ),
        )
        assertFalse(
            MagazineModePolicy.isUsableMusicPlaybackState(
                android.media.session.PlaybackState.STATE_NONE,
            ),
        )
    }

    @Test
    fun magazineMusicActivity_componentStable() {
        assertEquals(
            "com.leowalk.musiclockscreen",
            MagazineModePolicy.MODULE_PACKAGE,
        )
        assertEquals(
            "com.leowalk.musiclockscreen.MagazineMusicActivity",
            MagazineModePolicy.MAGAZINE_MUSIC_ACTIVITY,
        )
    }

    @Test
    fun magazineSkipsNormalChromeInterventions() {
        assertTrue(MagazineModePolicy.shouldSkipNormalMusicChromeInterventions(true))
        assertFalse(MagazineModePolicy.shouldSkipNormalMusicChromeInterventions(false))
        assertFalse(
            MagazineModePolicy.shouldHideNotificationsInMusicLockscreen(
                chromeMagazine = true,
                musicWallpaperShowing = true,
            ),
        )
        assertTrue(
            MagazineModePolicy.shouldHideNotificationsInMusicLockscreen(
                chromeMagazine = false,
                musicWallpaperShowing = true,
            ),
        )
        assertFalse(
            MagazineModePolicy.shouldHideNumStateForMusicLockscreen(
                chromeMagazine = true,
                musicWallpaperShowing = true,
            ),
        )
        assertTrue(
            MagazineModePolicy.shouldHideNumStateForMusicLockscreen(
                chromeMagazine = false,
                musicWallpaperShowing = true,
            ),
        )
        assertFalse(MagazineModePolicy.shouldRewriteMediaControlSlots(true))
        assertTrue(MagazineModePolicy.shouldRewriteMediaControlSlots(false))
    }

    @Test
    fun remoteAnimation_onlyOfficialEmag() {
        assertTrue(
            MagazineModePolicy.isMagazineRemoteAnimationPackage(
                MagazineModePolicy.OFFICIAL_MAGAZINE_PACKAGE,
                chromeMagazine = false,
            ),
        )
        // 模块页故意不参与，避免灭屏进画报 FullAOD
        assertFalse(
            MagazineModePolicy.isMagazineRemoteAnimationPackage(
                MagazineModePolicy.MODULE_PACKAGE,
                chromeMagazine = true,
            ),
        )
        assertFalse(
            MagazineModePolicy.isMagazineRemoteAnimationPackage(
                MagazineModePolicy.MODULE_PACKAGE,
                chromeMagazine = false,
            ),
        )
    }

    @Test
    fun spoofEmagInstalled_onlyInMagazineMode() {
        assertTrue(
            MagazineModePolicy.shouldSpoofMagazinePackageInstalled(
                chromeMagazine = true,
                queriedPackage = MagazineModePolicy.OFFICIAL_MAGAZINE_PACKAGE,
            ),
        )
        assertFalse(
            MagazineModePolicy.shouldSpoofMagazinePackageInstalled(
                chromeMagazine = false,
                queriedPackage = MagazineModePolicy.OFFICIAL_MAGAZINE_PACKAGE,
            ),
        )
        assertFalse(
            MagazineModePolicy.shouldSpoofMagazinePackageInstalled(
                chromeMagazine = true,
                queriedPackage = "com.other.app",
            ),
        )
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
