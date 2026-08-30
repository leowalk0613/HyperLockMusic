package com.leowalk.musiclockscreen.xposed

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View

/**
 * 音乐锁屏管理器（单例）
 *
 * 协调壁纸切换、通知隐藏、大专辑 overlay、歌词显示等。
 * 大专辑仅在音乐锁屏激活时以 overlay 显示，不画进壁纸。
 */
object MusicLockscreenManager {

    private val tag = "HyperLockMusic_Mgr"

    /** 音乐锁屏是否处于显示状态（逻辑状态，与 View 实际可见性可能有动画延迟） */
    var isShowing: Boolean = false
        private set

    // 大专辑封面 overlay（仅音乐锁屏可见）
    var bigAlbumView: BigAlbumOverlayView? = null

    // 歌词 overlay
    var lyricView: LockscreenLyricView? = null

    /** 壁纸切换过渡用的黑遮罩（挂在 keyguard 背景层之上，纯黑铺满盖住壁纸切换过程） */
    var transitionMaskView: View? = null

    /**
     * 大专辑 → 沉浸：壁纸尚未烘焙完成前，强制保持方形 overlay 可见，
     * 避免提前按 shouldShowSquareAlbum=false 隐藏后露出系统/桌面壁纸。
     */
    @Volatile
    var holdSquareAlbumUntilWallpaperSettled: Boolean = false

    // 当前模糊壁纸 bitmap（用于歌词毛玻璃条同源背景）
    var blurredWallpaperBitmap: Bitmap? = null
        private set

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    /**
     * 布局切换（尤其大专辑→沉浸）在 setBitmap 完成后调用：
     * 若切换前方形封面本就可见，则等壁纸稳定后淡出；若本就因沉浸歌词隐藏，则保持隐藏，勿误盖模糊背景。
     */
    fun finishLayoutSwitchOverlay(bakeImmersive: Boolean) {
        holdSquareAlbumUntilWallpaperSettled = false
        val album = bigAlbumView ?: return
        try {
            if (bakeImmersive || !ConfigReader.shouldShowSquareAlbum(album.context)) {
                album.animate().cancel()
                // 本就不可见（沉浸歌词占位）：直接保持 GONE，避免又闪出方形盖住模糊底
                if (album.visibility != View.VISIBLE) {
                    album.visibility = View.GONE
                    album.alpha = 1f
                    MediaFollowController.requestReflow()
                    return
                }
                album.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .withEndAction {
                        try {
                            album.visibility = View.GONE
                            album.alpha = 1f
                            MediaFollowController.requestReflow()
                        } catch (_: Throwable) {
                        }
                    }
                    .start()
            } else {
                album.animate().cancel()
                album.alpha = 1f
                showAlbumOverlay()
                MediaFollowController.requestReflow()
            }
        } catch (e: Throwable) {
            logE("finishLayoutSwitchOverlay error", e)
            showAlbumOverlay()
        }
    }

    /** 方形大专辑 overlay 当前是否可见（用于布局切换时决定要不要 hold）。 */
    fun isSquareAlbumOverlayVisible(): Boolean {
        val album = bigAlbumView ?: return false
        return album.visibility == View.VISIBLE && album.alpha > 0.01f
    }

    fun toggle() {
        if (isShowing) hide() else show()
    }

    /**
     * 直接显示（无动画）
     */
    fun show() {
        isShowing = true
        showAlbumOverlay()
        lyricView?.refreshVisibility()
        MediaFollowController.onMusicLockscreenShown()
        KeepScreenController.sync()
        logI("music lockscreen shown")
    }

    /**
     * 直接隐藏（无动画）
     */
    fun hide() {
        isShowing = false
        holdSquareAlbumUntilWallpaperSettled = false
        MediaFollowController.onMusicLockscreenHidden()
        hideAlbumOverlay()
        (lyricView as? LockscreenLyricView)?.resetForMusicLockscreenOff()
        KeepScreenController.sync()
        logI("music lockscreen hidden")
    }

    /**
     * 内部使用：仅更新显示状态，并同步专辑 overlay 可见性。
     */
    internal fun setShowingState(showing: Boolean) {
        isShowing = showing
        if (showing) {
            showAlbumOverlay()
            MediaFollowController.onMusicLockscreenShown()
        } else {
            holdSquareAlbumUntilWallpaperSettled = false
            MediaFollowController.onMusicLockscreenHidden()
            hideAlbumOverlay()
        }
        KeepScreenController.sync()
        logI("showing state updated: $showing")
    }

    /** 沉浸歌词是否正在占用专辑区块（有歌词可显示时）。 */
    fun isImmersiveLyricDisplayActive(): Boolean {
        return (lyricView as? LockscreenLyricView)?.isImmersiveLyricDisplayActive() == true
    }

    fun showAlbumOverlay() {
        val album = bigAlbumView ?: return
        if (!isShowing || !LockscreenNotificationController.shouldShowKeyguardOverlays()) {
            album.visibility = View.GONE
            return
        }
        album.showForMusicLockscreen()
    }

    fun hideAlbumOverlay() {
        bigAlbumView?.hideForMusicLockscreenOff()
    }

    /** 解锁离开锁屏时临时隐藏，不清除专辑图 */
    fun pauseAlbumOverlay() {
        bigAlbumView?.visibility = View.GONE
    }

    fun resumeAlbumOverlay() {
        if (isShowing) showAlbumOverlay()
    }

    /**
     * 立即隐藏过渡遮罩（取消动画、直接不可见）。
     * 用于离开锁屏/通知中心展开等场景，避免遮罩残留在非锁屏界面。
     */
    fun hideTransitionMaskImmediately() {
        val mask = transitionMaskView ?: return
        try {
            mask.handler?.post {
                try {
                    mask.animate().cancel()
                    mask.alpha = 0f
                    mask.visibility = View.INVISIBLE
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * 更新模糊壁纸 bitmap（供其他模块引用）；歌词雾状背景在壁纸应用后再渲染。
     */
    fun updateBlurredBitmap(bitmap: Bitmap?) {
        blurredWallpaperBitmap = bitmap
    }

    /** 锁屏壁纸 setBitmap 已提交且画面稳定后调用，触发歌词雾状背景渲染。 */
    fun notifyWallpaperAppliedToLockScreen(albumBitmap: Bitmap? = null, trackKey: String? = null) {
        (lyricView as? LockscreenLyricView)?.onWallpaperAlbumReady(albumBitmap, trackKey)
        if (isShowing && albumBitmap != null && !albumBitmap.isRecycled) {
            updateAlbumBitmap(albumBitmap)
            showAlbumOverlay()
        }
    }

    /**
     * 更新专辑图（仅写入 overlay；非音乐锁屏时不显示）
     */
    fun updateAlbumArt(drawable: Drawable?) {
        try {
            bigAlbumView?.setAlbumArt(drawable)
            if (isShowing) showAlbumOverlay()
        } catch (e: Throwable) {
            logE("updateAlbumArt error", e)
        }
    }

    fun updateAlbumBitmap(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bigAlbumView?.setAlbumBitmap(bitmap)
            }
            if (isShowing) showAlbumOverlay()
        } catch (e: Throwable) {
            logE("updateAlbumBitmap error", e)
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
