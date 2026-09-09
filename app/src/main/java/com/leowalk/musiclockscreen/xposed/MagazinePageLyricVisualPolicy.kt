package com.leowalk.musiclockscreen.xposed

/**
 * 画报页歌词视觉门闩：字体染色 / MiBlur 与锁屏同路径，但不依赖 SystemUI keyguard 状态。
 */
internal object MagazinePageLyricVisualPolicy {

    /**
     * 画报 Activity 遮挡锁屏时 [android.app.KeyguardManager] 常判非 keyguard，
     * 仍需专辑取色与透色效果。
     */
    fun allowsAlbumTintEffects(magazinePageHost: Boolean, onKeyguard: Boolean): Boolean =
        magazinePageHost || onKeyguard

    /**
     * 壁纸烘焙后的取色应由 Activity 统一广播给底栏 + 歌词；
     * 歌词侧勿再并行 [LockscreenLyricView.onWallpaperAlbumReady]，否则两路 extract
     * 完成顺序不同时易出现「歌名深字 / 歌词浅字」分裂。
     */
    fun shouldDeferWallpaperTintExtractToActivity(magazinePageHost: Boolean): Boolean =
        magazinePageHost

    /**
     * 画报对比度参考色：优先与底栏共用的亮度代表色（非 accent），再采样 / fogTint。
     */
    fun magazineContrastBackground(
        sharedContrast: Int?,
        fogTint: Int?,
        sampledBehindLyrics: Int?,
        defaultColor: Int,
    ): Int = sharedContrast ?: sampledBehindLyrics ?: fogTint ?: defaultColor

    /**
     * 画报页传入当次烘培壁纸副本时，勿用 Session 缓存 trackKey 否决取色
     *（app 进程 AlbumArtResolver 键常与 Activity lastArtKey 不一致）。
     */
    fun shouldAcceptExtractedTint(
        magazinePageHost: Boolean,
        hasExplicitSourceAlbum: Boolean,
        expectedKey: String?,
        cachedTrackKey: String?,
    ): Boolean {
        if (magazinePageHost && hasExplicitSourceAlbum) return true
        if (expectedKey == null) return true
        return expectedKey == cachedTrackKey
    }

    /** 样式刷新时保留钉底 topMargin（画报无 MediaFollow 回流）。 */
    fun shouldPreservePinnedTopMargin(magazinePageHost: Boolean): Boolean = magazinePageHost

    /** 沉浸或画报页统一走 MiSans，贴近锁屏字形。 */
    fun shouldUseMiSansTypeface(
        magazinePageHost: Boolean,
        immersiveLyric: Boolean,
    ): Boolean = magazinePageHost || immersiveLyric
}
