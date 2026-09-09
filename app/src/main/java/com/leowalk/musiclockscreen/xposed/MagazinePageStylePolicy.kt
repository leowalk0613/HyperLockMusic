package com.leowalk.musiclockscreen.xposed

/**
 * 画报右划页样式策略（与锁屏 [MagazineModePolicy] 分离）。
 * 锁屏画报模式不改媒体/通知/勿扰、不烘焙锁屏大专辑；样式只作用于模块 Activity。
 */
internal object MagazinePageStylePolicy {

    const val STYLE_BIG_ALBUM = MagazineModePolicy.CHROME_BIG_ALBUM
    const val STYLE_IMMERSIVE = MagazineModePolicy.CHROME_IMMERSIVE

    fun resolvePageStyle(raw: String?): String =
        if (raw == STYLE_IMMERSIVE) STYLE_IMMERSIVE else STYLE_BIG_ALBUM

    fun shouldShowForegroundAlbum(pageStyle: String): Boolean =
        resolvePageStyle(pageStyle) == STYLE_BIG_ALBUM

    fun shouldBakeImmersive(pageStyle: String): Boolean =
        resolvePageStyle(pageStyle) == STYLE_IMMERSIVE

    /**
     * 大专辑底边 = 屏高 × [anchorYPercent]%；返回内容区 top（不含阴影）。
     */
    fun bigAlbumTopPx(
        screenHeight: Int,
        albumSizePx: Int,
        anchorYPercent: Float,
    ): Int {
        val bottom = (screenHeight * (anchorYPercent / 100f)).toInt()
        return (bottom - albumSizePx).coerceAtLeast(0)
    }

    fun albumSizePx(screenWidth: Int, sizePercent: Float): Int =
        (screenWidth * (sizePercent / 100f)).toInt().coerceAtLeast(1)

    /**
     * 将「底边占屏高 %」换成 [BlurUtils.blurWithBigAlbum] 的竖直偏移（相对垂直居中，单位 dp@360 宽）。
     */
    fun albumOffsetYDpFromAnchor(
        screenWidth: Int,
        screenHeight: Int,
        albumSizePercent: Float,
        anchorYPercent: Float,
    ): Float {
        val albumSize = albumSizePx(screenWidth, albumSizePercent)
        val albumTop = bigAlbumTopPx(screenHeight, albumSize, anchorYPercent).toFloat()
        val centeredTop = (screenHeight - albumSize) / 2f
        val offsetPx = albumTop - centeredTop
        val density = screenWidth / 360f
        return if (density <= 0f) offsetPx else offsetPx / density
    }
}
