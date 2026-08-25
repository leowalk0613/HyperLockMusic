package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.widget.ImageView
import io.github.libxposed.api.XposedModule

/**
 * 媒体卡片 bindMediaData hook：专辑图缓存、切歌时静默更新壁纸。
 * 音乐锁屏开关仅通过媒体控件 action0 按钮触发。
 */
class MediaAlbumClickHook {

    private val tag = "MusicLockScreen_AlbumClick"
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
                        if (ctx != null && mediaData != null) {
                            AlbumArtResolver.refreshFromBind(ctx, mediaData, metadata)
                        }

                        // 媒体控件被划掉时，若仍在锁屏且音乐锁屏开启则恢复壁纸
                        if (albumView != null && albumView.getTag(0x7f000003) == null) {
                            albumView.setTag(0x7f000003, true)
                            albumView.addOnAttachStateChangeListener(
                                object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) {}
                                    override fun onViewDetachedFromWindow(v: View) {
                                        if (WallpaperController.isShowing() &&
                                            HookUtils.isOnKeyguard(v.context)
                                        ) {
                                            logI("albumView detached on keyguard, restoring wallpaper")
                                            WallpaperController.restoreOriginalWallpaper(v.context)
                                        }
                                    }
                                }
                            )
                            logI("albumView detach listener set")
                        }

                        // 只有专辑图更新时才更新壁纸
                        val isArtUpdate = artWorkUpdateField?.getBoolean(thisObj) ?: false
                        val isNewSong = newSongUpdateField?.getBoolean(thisObj) ?: false

                        if (WallpaperController.isShowing() && (isArtUpdate || isNewSong)) {
                            logI("Artwork updated, refreshing wallpaper")
                            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onWallpaperAlbumPending()
                            // 延迟 300ms 等专辑图动画完成
                            albumView?.postDelayed({
                                try {
                                    val ctx = albumView.context
                                    if (!HookUtils.isOnKeyguard(ctx)) {
                                        logI("delayed wallpaper update skipped: not on keyguard")
                                        return@postDelayed
                                    }
                                    val pkg = HookUtils.packageFromMediaData(mediaData)
                                        ?: HookUtils.currentMediaPackage(ctx)
                                    if (!HookUtils.isAllowedMusicApp(ctx, pkg)) {
                                        logI("delayed wallpaper update blocked: $pkg not in whitelist")
                                        WallpaperController.restoreOriginalWallpaper(ctx.applicationContext)
                                        return@postDelayed
                                    }
                                    val albumImageView = albumImageViewField.get(holder) as? ImageView
                                    val drawable = albumImageView?.drawable
                                    if (drawable != null) {
                                        WallpaperController.setMusicWallpaper(ctx, drawable, true)
                                    }
                                } catch (e: Throwable) {
                                    logE("delayed wallpaper update error", e)
                                }
                            }, 300)
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
