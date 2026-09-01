package com.leowalk.musiclockscreen.xposed

/**
 * 大专辑 overlay 离屏合成规格与缓存键（纯逻辑，便于单测）。
 * 封面 / 尺寸 / 圆角 / 阴影变化时才重建 bitmap，息屏动画期间只 blit 缓存。
 */
internal object BigAlbumOverlayComposite {

    data class Spec(
        val layoutW: Int,
        val layoutH: Int,
        val contentSizePx: Int,
        val cornerRadiusPx: Float,
        val shadowBlurPx: Float,
        val shadowOffsetX: Float,
        val shadowOffsetY: Float,
        val padLeft: Int,
        val padTop: Int,
        val albumIdentity: Int,
        val albumWidth: Int,
        val albumHeight: Int,
    )

    fun cacheKey(spec: Spec): Long {
        var h = 17L
        h = 31 * h + spec.layoutW
        h = 31 * h + spec.layoutH
        h = 31 * h + spec.contentSizePx
        h = 31 * h + spec.cornerRadiusPx.toBits()
        h = 31 * h + spec.shadowBlurPx.toBits()
        h = 31 * h + spec.shadowOffsetX.toBits()
        h = 31 * h + spec.shadowOffsetY.toBits()
        h = 31 * h + spec.padLeft
        h = 31 * h + spec.padTop
        h = 31 * h + spec.albumIdentity
        h = 31 * h + spec.albumWidth
        h = 31 * h + spec.albumHeight
        return h
    }

    fun shouldRebuild(cachedKey: Long, spec: Spec): Boolean {
        if (cachedKey == 0L) return true
        return cachedKey != cacheKey(spec)
    }
}
