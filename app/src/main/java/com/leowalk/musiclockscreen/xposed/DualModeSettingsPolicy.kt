package com.leowalk.musiclockscreen.xposed

/**
 * 普通锁屏 vs 画报页：设置键前缀与档案解析（纯函数）。
 */
internal object DualModeSettingsPolicy {

    const val MAGAZINE_PREFIX = "magazine_"

    fun prefKey(baseKey: String, magazineMode: Boolean): String =
        if (magazineMode) MAGAZINE_PREFIX + baseKey else baseKey

    /**
     * 画报键未写入过时，用普通档案当前值作初值，避免一切空默认。
     */
    fun resolveStoredOrFallback(
        magazineKeyPresent: Boolean,
        magazineValue: Float,
        normalValue: Float,
    ): Float = if (magazineKeyPresent) magazineValue else normalValue

    fun resolveStoredOrFallback(
        magazineKeyPresent: Boolean,
        magazineValue: Boolean,
        normalValue: Boolean,
    ): Boolean = if (magazineKeyPresent) magazineValue else normalValue

    fun resolveStoredOrFallback(
        magazineKeyPresent: Boolean,
        magazineValue: String,
        normalValue: String,
    ): String = if (magazineKeyPresent) magazineValue else normalValue

    /** 进入画报模式时不得改写普通歌词/专辑绑定键。 */
    fun shouldMutateNormalLyricOnEnterMagazine(): Boolean = false
}
