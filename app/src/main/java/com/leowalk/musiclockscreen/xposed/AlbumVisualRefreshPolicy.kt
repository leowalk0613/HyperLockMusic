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
     * 当前封面指纹相对已应用壁纸是否滞后。
     * current=0 表示尚无可用封面，不算滞后（避免无图空转重建）。
     */
    fun isArtFingerprintLagging(appliedFingerprint: Long, currentFingerprint: Long): Boolean {
        return currentFingerprint != 0L && currentFingerprint != appliedFingerprint
    }

    /**
     * @param trackKey 当前解析曲目
     * @param wallpaperTrackKey 已应用/在记的壁纸曲目
     * @param hasCachedArt 是否已有可用封面 bitmap
     * @param fogReady 歌词取色是否已就绪
     * @param appliedArtFingerprint 已写入锁屏壁纸所用封面指纹
     * @param currentArtFingerprint 当前缓存封面指纹
     * @param hasNetworkHdForTrack 当前曲目已铺上官方高清前景（勿再用系统低清盖回去）
     */
    fun decideArtRetry(
        trackKey: String?,
        wallpaperTrackKey: String?,
        hasCachedArt: Boolean,
        fogReady: Boolean,
        appliedArtFingerprint: Long = 0L,
        currentArtFingerprint: Long = 0L,
        hasNetworkHdForTrack: Boolean = false,
    ): ArtRetryAction {
        // 官方高清已就绪时，会话里系统 bitmap 指纹抖动不再触发整段重建
        val artLagging = isArtFingerprintLagging(appliedArtFingerprint, currentArtFingerprint) &&
            !hasNetworkHdForTrack
        val wallpaperCaughtUp = !trackKey.isNullOrBlank() &&
            trackKey == wallpaperTrackKey &&
            !artLagging
        val pushSystemOverlay = hasCachedArt && !hasNetworkHdForTrack
        if (!wallpaperCaughtUp) {
            return ArtRetryAction(
                skipWallpaperRebuild = false,
                refreshAlbumOverlay = pushSystemOverlay,
                refreshFogTint = hasCachedArt && !fogReady,
            )
        }
        return ArtRetryAction(
            skipWallpaperRebuild = true,
            refreshAlbumOverlay = pushSystemOverlay,
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

    /** 已有官方高清时，禁止用系统缓存封面把 overlay 盖回低清。 */
    fun shouldReplaceOverlayWithSystemArt(hasNetworkHdForTrack: Boolean): Boolean =
        !hasNetworkHdForTrack
}
