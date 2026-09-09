package com.leowalk.musiclockscreen.xposed

/**
 * 专辑样式 ↔ 歌词样式默认绑定（普通锁屏与画报页共用规则）：
 * - 大专辑 → 沉浸歌词（可有雾底）
 * - 沉浸封面 → 普通歌词 + 隐藏雾底
 */
internal object AlbumLyricBindingPolicy {

    data class LyricDefaults(
        val immersiveLyric: Boolean,
        val lyricHideBackground: Boolean,
    )

    fun defaultsForImmersiveAlbum(immersiveAlbumOn: Boolean): LyricDefaults =
        if (immersiveAlbumOn) {
            LyricDefaults(immersiveLyric = false, lyricHideBackground = true)
        } else {
            LyricDefaults(immersiveLyric = true, lyricHideBackground = false)
        }
}
