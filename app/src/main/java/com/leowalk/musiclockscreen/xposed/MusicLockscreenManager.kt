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

    // 当前模糊壁纸 bitmap（用于歌词毛玻璃条同源背景）
    var blurredWallpaperBitmap: Bitmap? = null
        private set

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

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
        logI("music lockscreen shown")
    }

    /**
     * 直接隐藏（无动画）
     */
    fun hide() {
        isShowing = false
        MediaFollowController.onMusicLockscreenHidden()
        hideAlbumOverlay()
        (lyricView as? LockscreenLyricView)?.resetForMusicLockscreenOff()
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
            MediaFollowController.onMusicLockscreenHidden()
            hideAlbumOverlay()
        }
        logI("showing state updated: $showing")
    }

    fun showAlbumOverlay() {
        val album = bigAlbumView ?: return
        if (!isShowing) {
            album.hideForMusicLockscreenOff()
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
