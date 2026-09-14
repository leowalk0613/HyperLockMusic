package com.leowalk.musiclockscreen.xposed

/**
 * 画报页坚持真·MiBlur（透壁纸 member blend）——仅底栏 TextView。
 * 歌词是自定义 Canvas View：Pass/Member blend 会吞掉字形，故歌词走实色。
 */
internal object MagazinePageMiBlurPolicy {

    /** 不再用专辑混色顶替；要锁屏同款白字透色。 */
    fun useImmediateAlbumTintPaint(): Boolean = false

    /** 底栏可开；歌词侧 [LockscreenLyricView] 自行禁用 Pass blur。 */
    fun enablePassWindowBlur(): Boolean = true

    fun sampleSiblingContent(): Boolean = true

    /** 套上前可透明；歌词及时上屏优先，改为 false。 */
    fun hideUntilBlurReady(): Boolean = false

    /**
     * 底栏歌曲信息：禁止整栏 alpha=0 等 MiBlur。
     * 否则 onLayout/取色会反复打断重试，歌名会「有时不显示」，且与歌词露出不同步。
     */
    fun hideChromeUntilBlurReady(): Boolean = false

    /** 稳定重试：含几帧 + 短延迟，直到成功或耗尽。 */
    val STABLE_RETRY_DELAYS_MS: LongArray =
        longArrayOf(0L, 16L, 33L, 50L, 100L, 200L, 350L, 600L, 1000L)

    fun stableRetryCount(): Int = STABLE_RETRY_DELAYS_MS.size

    fun stableDelayAt(index: Int): Long =
        STABLE_RETRY_DELAYS_MS.getOrElse(index) { STABLE_RETRY_DELAYS_MS.last() }
}
