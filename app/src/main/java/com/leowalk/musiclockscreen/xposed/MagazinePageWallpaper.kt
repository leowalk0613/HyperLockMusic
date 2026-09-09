package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap

/**
 * 画报 Activity 壁纸烘焙：复用 [BlurUtils]，与锁屏 SystemUI 注入链路无关。
 */
internal object MagazinePageWallpaper {

    data class Params(
        val pageStyle: String,
        val blurRadius: Float,
        val darkOverlayAlpha: Int,
        val albumSizePercent: Float,
        val albumAnchorYPercent: Float,
        val albumCornerDp: Float,
        val immersiveCenterYPercent: Float,
        val edgeGradientEnabled: Boolean,
        val width: Int,
        val height: Int,
        /** 大专辑页是否叠前景方形封面；沉浸歌词占槽时为 false。 */
        val showBigAlbum: Boolean = true,
    )

    fun render(
        blurSource: Bitmap,
        sharpAlbum: Bitmap?,
        params: Params,
    ): Bitmap {
        val tw = params.width.coerceAtLeast(1)
        val th = params.height.coerceAtLeast(1)
        val style = MagazinePageStylePolicy.resolvePageStyle(params.pageStyle)
        return if (MagazinePageStylePolicy.shouldBakeImmersive(style)) {
            BlurUtils.blurWithImmersiveAlbum(
                blurSource = blurSource,
                sharpAlbum = sharpAlbum?.takeIf { !it.isRecycled } ?: blurSource,
                radius = params.blurRadius,
                darkOverlayAlpha = params.darkOverlayAlpha,
                targetWidth = tw,
                targetHeight = th,
                albumAnchorYPercent = params.albumAnchorYPercent,
                albumCenterYPercent = params.immersiveCenterYPercent,
                edgeGradientEnabled = params.edgeGradientEnabled,
            )
        } else {
            val offsetDp = MagazinePageStylePolicy.albumOffsetYDpFromAnchor(
                screenWidth = tw,
                screenHeight = th,
                albumSizePercent = params.albumSizePercent,
                anchorYPercent = params.albumAnchorYPercent,
            )
            BlurUtils.blurWithBigAlbum(
                blurSource = blurSource,
                radius = params.blurRadius,
                darkOverlayAlpha = params.darkOverlayAlpha,
                showBigAlbum = params.showBigAlbum,
                targetWidth = tw,
                targetHeight = th,
                albumSizePercent = params.albumSizePercent,
                albumOffsetYDp = offsetDp,
                albumCornerDp = params.albumCornerDp,
                sharpAlbum = sharpAlbum,
            )
        }
    }
}
