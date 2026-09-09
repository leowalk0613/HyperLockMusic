package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata

/**
 * 官方高清封面拉取（网易云 / QQ / 小米音乐曲库）。
 * 锁屏 [WallpaperController] 与画报 [MagazineMusicActivity] 共用；
 * 模糊底仍用会话封面，高清只替换前景 sharpAlbum。
 */
internal object NetworkAlbumArtFetcher {

    /** 与 SystemUI MediaData 一样带 packageName 字段，供各 Resolver 反射读取。 */
    class PackageStub(@JvmField val packageName: String)

    fun shouldAttemptEnhance(enabled: Boolean, referenceMinSide: Int, alreadyHighResSide: Int = 1080): Boolean =
        enabled && referenceMinSide > 0 && referenceMinSide < alreadyHighResSide

    fun fetchVerifiedHighRes(
        context: Context,
        reference: Bitmap,
        metadata: MediaMetadata?,
        packageName: String?,
        trackKey: String?,
    ): Bitmap? {
        val mediaData = packageName?.takeIf { it.isNotBlank() }?.let { PackageStub(it) }
        val pkg = packageName
        when {
            trackKey?.startsWith(QqMusicSongIdResolver.TRACK_PREFIX) == true ||
                QqMusicSongIdResolver.isQqCatalogPackage(pkg) -> {
                return QqMusicAlbumArtSource.fetchVerifiedHighRes(
                    context, reference, metadata, mediaData, trackKey,
                )
            }
            trackKey?.startsWith("netease:") == true ||
                pkg == "com.netease.cloudmusic" -> {
                return NetEaseAlbumArtSource.fetchVerifiedHighRes(
                    context, reference, metadata, mediaData, trackKey,
                )
            }
        }
        val bareId = trackKey
            ?.takeIf { it.startsWith("id:") }
            ?.removePrefix("id:")
            ?.toLongOrNull()
        if (bareId != null) {
            QqMusicAlbumArtSource.fetchVerifiedHighRes(
                context, reference, metadata, mediaData, QqMusicSongIdResolver.trackKey(bareId),
            )?.let { return it }
            NetEaseAlbumArtSource.fetchVerifiedHighRes(
                context, reference, metadata, mediaData, NetEaseSongIdResolver.trackKey(bareId),
            )?.let { return it }
        }
        return NetEaseAlbumArtSource.fetchVerifiedHighRes(
            context, reference, metadata, mediaData, trackKey,
        )
    }
}
