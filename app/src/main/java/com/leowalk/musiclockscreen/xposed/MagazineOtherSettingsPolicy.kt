package com.leowalk.musiclockscreen.xposed

/**
 * 画报模式下「其他设置」可见项：只保留对右划自建页有用的项。
 */
internal object MagazineOtherSettingsPolicy {

    /** 壁纸模糊（画报页烘焙）。 */
    fun showBlurSection(magazineMode: Boolean): Boolean = true

    /** 简洁时钟：画报页自带状态栏时钟，隐藏。 */
    fun showMinimalClock(magazineMode: Boolean): Boolean = !magazineMode

    /** 禁用息屏壁纸缩放：仅普通音乐锁屏。 */
    fun showDisableWallpaperScale(magazineMode: Boolean): Boolean = !magazineMode

    /** 保持锁屏常亮：画报/普通都有用。 */
    fun showKeepLockScreenOn(magazineMode: Boolean): Boolean = true

    /** AOD 完整媒体控件：画报不改锁屏 AOD。 */
    fun showAodFullMedia(magazineMode: Boolean): Boolean = !magazineMode

    /** 歌名括号：画报底栏歌名也会用。 */
    fun showTitleBracket(magazineMode: Boolean): Boolean = true

    fun showLockscreenAssistSection(magazineMode: Boolean): Boolean =
        showMinimalClock(magazineMode) ||
            showDisableWallpaperScale(magazineMode) ||
            showKeepLockScreenOn(magazineMode)
}
