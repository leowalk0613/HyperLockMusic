package com.leowalk.musiclockscreen.xposed

/**
 * 专辑 overlay / 取色补刷策略（切歌空窗、同曲 coalesce、art retry 共用）。
 * 壁纸已追上曲目时仍可能缺 overlay 或 fog——不得整段跳过。
 */
internal object AlbumVisualRefreshPolicy {

    data class ArtRetryAction(
        /** 跳过重建锁屏壁纸 */
        val skipWallpaperRebuild: Boolean,
        /** 用缓存封面刷新 overlay */
        val refreshAlbumOverlay: Boolean,
        /** 补刷歌词取色 / 雾状背景 */
        val refreshFogTint: Boolean,
    )

    /**
     * @param trackKey 当前解析曲目
     * @param wallpaperTrackKey 已应用/在记的壁纸曲目
     * @param hasCachedArt 是否已有可用封面 bitmap
     * @param fogReady 歌词取色是否已就绪
     */
    fun decideArtRetry(
        trackKey: String?,
        wallpaperTrackKey: String?,
        hasCachedArt: Boolean,
        fogReady: Boolean,
    ): ArtRetryAction {
        val wallpaperCaughtUp = !trackKey.isNullOrBlank() &&
            trackKey == wallpaperTrackKey
        if (!wallpaperCaughtUp) {
            return ArtRetryAction(
                skipWallpaperRebuild = false,
                refreshAlbumOverlay = hasCachedArt,
                refreshFogTint = hasCachedArt && !fogReady,
            )
        }
        return ArtRetryAction(
            skipWallpaperRebuild = true,
            refreshAlbumOverlay = hasCachedArt,
            refreshFogTint = hasCachedArt && !fogReady,
        )
    }

    /** 同曲静默更新被 coalesce 时，是否仍需补刷视觉。 */
    fun shouldRecoverVisualsOnCoalesce(
        hasCachedArt: Boolean,
        fogReady: Boolean,
        albumOverlayEmpty: Boolean,
    ): Boolean {
        if (!hasCachedArt) return false
        if (!fogReady) return true
        if (albumOverlayEmpty) return true
        return false
    }
}
