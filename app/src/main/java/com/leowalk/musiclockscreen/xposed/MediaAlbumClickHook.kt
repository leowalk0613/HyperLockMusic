package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.widget.ImageView
import io.github.libxposed.api.XposedModule

/**
 * 媒体卡片 bindMediaData hook：专辑图缓存、切歌时静默更新壁纸。
 * 音乐锁屏开关仅通过媒体控件 action0 按钮触发。
 */
class MediaAlbumClickHook {

    private val tag = "HyperLockMusic_AlbumClick"
    private var module: XposedModule? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module

        // 设置日志回调
        WallpaperController.logCallback = { priority, tag2, msg, e ->
            if (e != null) {
                module.log(priority, tag2, msg, e)
            } else {
                module.log(priority, tag2, msg)
            }
        }
        HyperOsWallpaperBridge.logCallback = WallpaperController.logCallback
        AlbumArtResolver.logCallback = WallpaperController.logCallback
        NetEaseAlbumArtSource.logCallback = WallpaperController.logCallback
        NetEaseSongIdResolver.logCallback = WallpaperController.logCallback

        try {
            logI("install start")

            val vcClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                false,
                classLoader
            )
            logI("Found MiuiMediaViewControllerImpl: ${vcClass.name}")

            val mediaDataClass = Class.forName(
                "com.android.systemui.media.controls.shared.model.MediaData",
                false,
                classLoader
            )
            logI("Found MediaData: ${mediaDataClass.name}")

            val bindMethod = vcClass.getDeclaredMethod("bindMediaData", mediaDataClass)
            logI("Found bindMediaData method")

            val viewHolderClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
                false,
                classLoader
            )
            val albumViewField = viewHolderClass.getDeclaredField("albumView")
            albumViewField.isAccessible = true
            val albumImageViewField = viewHolderClass.getDeclaredField("albumImageView")
            albumImageViewField.isAccessible = true

            val holderField = vcClass.getDeclaredField("holder")
            holderField.isAccessible = true

            val mediaMetadataField = try {
                vcClass.getDeclaredField("mediaMataData").apply { isAccessible = true }
            } catch (e: Throwable) {
                logE("mediaMataData field not found", e)
                null
            }

            // isArtWorkUpdate 字段
            val artWorkUpdateField = try {
                vcClass.getDeclaredField("isArtWorkUpdate").apply { isAccessible = true }
            } catch (e: Throwable) {
                logE("isArtWorkUpdate field not found", e)
                null
            }

            // isNewSongUpdate 字段
            val newSongUpdateField = try {
                vcClass.getDeclaredField("isNewSongUpdate").apply { isAccessible = true }
            } catch (e: Throwable) {
                logE("isNewSongUpdate field not found", e)
                null
            }

            // hook bindMediaData 方法返回后
            module.hook(bindMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val thisObj = chain.thisObject
                    val mediaData = chain.args.getOrNull(0)
                    val holder = holderField.get(thisObj)

                    val metadata = mediaMetadataField?.get(thisObj) as? android.media.MediaMetadata

                    if (holder != null) {
                        val albumView = albumViewField.get(holder) as? View
                        val ctx = albumView?.context
                        var trackChanged = false
                        if (ctx != null && mediaData != null) {
                            trackChanged = AlbumArtResolver.refreshFromBind(ctx, mediaData, metadata)
                        }

                        // 媒体控件被划掉时，若仍在锁屏且音乐锁屏开启则恢复壁纸
                        if (albumView != null && albumView.getTag(0x7f000003) == null) {
                            albumView.setTag(0x7f000003, true)
                            albumView.addOnAttachStateChangeListener(
                                object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) {}
                                    override fun onViewDetachedFromWindow(v: View) {
                                        if (WallpaperController.isShowing() &&
                                            HookUtils.canApplyLockWallpaper(v.context)
                                        ) {
                                            logI("albumView detached on keyguard, restoring wallpaper")
                                            WallpaperController.restoreOriginalWallpaper(v.context)
                                        }
                                    }
                                }
                            )
                            logI("albumView detach listener set")
                        }

                        val isArtUpdate = artWorkUpdateField?.getBoolean(thisObj) ?: false
                        val isNewSong = newSongUpdateField?.getBoolean(thisObj) ?: false
                        val needRefresh = isArtUpdate || isNewSong || trackChanged

                        if (WallpaperController.isShowing() && needRefresh) {
                            logI(
                                "media bind refresh: art=$isArtUpdate newSong=$isNewSong " +
                                    "trackChanged=$trackChanged"
                            )
                            // 即时刷新：不依赖专辑 ImageView 动画；封面优先走 AlbumArtResolver
                            albumView?.post {
                                try {
                                    refreshMusicLockscreenFromBind(
                                        albumView.context,
                                        mediaData,
                                        holder,
                                        albumImageViewField,
                                        mediaMetadataField,
                                        thisObj
                                    )
                                } catch (e: Throwable) {
                                    logE("instant wallpaper update error", e)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logE("after bindMediaData error", e)
                }
                result
            }

            logI("MediaAlbumClickHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    /**
     * 切歌 / 换封面后即时刷新：专辑 overlay、模糊壁纸、歌词与取色。
     * 解锁态无法写壁纸时标记 stale，并先更新内存中的专辑与歌词，待回锁屏再补壁纸。
     */
    private fun refreshMusicLockscreenFromBind(
        ctx: android.content.Context,
        mediaData: Any?,
        holder: Any,
        albumImageViewField: java.lang.reflect.Field,
        mediaMetadataField: java.lang.reflect.Field?,
        controller: Any
    ) {
        val pkg = HookUtils.packageFromMediaData(mediaData)
            ?: HookUtils.currentMediaPackage(ctx)
        if (!HookUtils.isAllowedMusicApp(ctx, pkg)) {
            logI("wallpaper update blocked: $pkg not in whitelist")
            if (HookUtils.canApplyLockWallpaper(ctx)) {
                WallpaperController.restoreOriginalWallpaper(ctx.applicationContext)
            }
            return
        }

        val albumImageView = albumImageViewField.get(holder) as? ImageView
        val drawable = albumImageView?.drawable
        val bindMeta = mediaMetadataField?.get(controller) as? android.media.MediaMetadata

        // 先推 overlay / 歌词，壁纸异步构建时画面已切到新歌
        if (drawable != null) {
            MusicLockscreenManager.updateAlbumArt(drawable)
        } else {
            AlbumArtResolver.getCached()?.let { MusicLockscreenManager.updateAlbumBitmap(it) }
        }
        (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onTrackMayHaveChanged()

        if (!HookUtils.canApplyLockWallpaper(ctx)) {
            logI("wallpaper update deferred: screen off or not on keyguard")
            WallpaperController.markWallpaperStale()
            return
        }

        WallpaperController.setMusicWallpaper(ctx, drawable, true, bindMeta)
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            module?.log(android.util.Log.ERROR, tag, msg, e)
        } else {
            module?.log(android.util.Log.ERROR, tag, msg)
        }
    }
}
