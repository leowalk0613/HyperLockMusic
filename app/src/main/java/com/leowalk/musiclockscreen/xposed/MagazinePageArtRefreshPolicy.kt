package com.leowalk.musiclockscreen.xposed

/**
 * 画报页封面刷新闸门：避免会话 bitmap 每次 generationId 变化导致
 * 「低清重烘 → 高清再盖」循环闪烁。
 */
internal object MagazinePageArtRefreshPolicy {

    fun buildStableArtKey(
        trackKey: String?,
        packageName: String?,
        title: String?,
        artist: String?,
        artWidth: Int,
        artHeight: Int,
        style: String,
        wantHd: Boolean,
        bakeFingerprint: String,
    ): String {
        val track = trackKey?.takeIf { it.isNotBlank() }
            ?: "t:${title.orEmpty()}|a:${artist.orEmpty()}|p:${packageName.orEmpty()}"
        return "$track|${artWidth}x${artHeight}|$style|$wantHd|$bakeFingerprint"
    }

    /**
     * @param hdAppliedForKey 当前 [stableArtKey] 是否已成功铺上官方高清
     * @param enhanceInFlight 高清请求进行中
     */
    fun shouldStartBake(
        stableArtKey: String,
        lastArtKey: String,
        wantHd: Boolean,
        hdAppliedForKey: Boolean,
        enhanceInFlight: Boolean,
    ): Boolean {
        if (stableArtKey == lastArtKey) {
            // 同曲同参数：低清已画过；高清已完成或进行中都不再重开整段烘焙
            if (!wantHd) return false
            if (hdAppliedForKey || enhanceInFlight) return false
            return false
        }
        return true
    }

    fun shouldFetchHd(
        wantHd: Boolean,
        referenceMinSide: Int,
        hdAppliedForKey: Boolean,
        enhanceInFlight: Boolean,
    ): Boolean {
        if (hdAppliedForKey || enhanceInFlight) return false
        return NetworkAlbumArtFetcher.shouldAttemptEnhance(wantHd, referenceMinSide)
    }
}
