package com.leowalk.musiclockscreen.xposed

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * 壁纸管理器（HyperOS 4）
 *
 * 锁屏壁纸使用 WallpaperManager.setBitmap(FLAG_LOCK)，仅影响锁屏位。
 */
object WallpaperController {

    private const val tag = "MusicLockScreen_Wallpaper"

    private var originalLockWallpaper: Bitmap? = null
    private var isMusicWallpaperSet = false
    private var isAnimating = false

    // 当前锁屏壁纸所基于的专辑封面与曲目（用于判断切歌后是否需要重建）
    private var lastWallpaperAlbumBitmap: Bitmap? = null
    private var lastWallpaperTrackKey: String? = null

    private val sessionPollHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionPollRunnable: Runnable? = null
    private var sessionWatchContext: Context? = null

    private const val SESSION_POLL_INTERVAL_MS = 2000L

    /** setBitmap 提交后到开始淡出的停留时长，覆盖系统壁纸刷新/交叉渐变，避免淡出与画面切换重合而闪烁。 */
    private const val MASK_SETTLE_MS = 240L

    /** 退出恢复时遮罩停留时长（系统回切原壁纸往往更慢，需更长覆盖切换过程）。 */
    private const val MASK_SETTLE_EXIT_MS = 480L

    /** 遮罩淡出时长 */
    private const val MASK_FADE_MS = 220L

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    /** 铺满不透明盖住壁纸切换过程（toggle 时瞬间调用）。 */
    private fun showTransitionMask() {
        val mask = MusicLockscreenManager.transitionMaskView ?: return
        try {
            if (mask.handler == null) return
            mask.handler.post {
                try {
                    // 仅锁屏状态下显示遮罩，避免解锁后误显示
                    if (!HookUtils.isOnKeyguard(mask.context)) {
                        mask.alpha = 0f
                        mask.visibility = View.INVISIBLE
                        return@post
                    }
                    mask.alpha = 1f
                    mask.visibility = View.VISIBLE
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * 设置遮罩显示的图片作为过渡内容：
     * 进入时用「去掉专辑与阴影的音乐壁纸背景」，退出时用「原锁屏壁纸」。
     * 这样遮罩最终内容与切换完成后的壁纸匹配，淡出时无跳变、不闪烁。
     * 传 null 则回退为纯黑。
     */
    private fun setMaskImage(bitmap: Bitmap?) {
        val mask = MusicLockscreenManager.transitionMaskView ?: return
        try {
            if (mask.handler == null) return
            mask.handler.post {
                try {
                    // 已离开锁屏则不显示遮罩（防止解锁后残留）
                    if (!HookUtils.isOnKeyguard(mask.context)) {
                        mask.alpha = 0f
                        mask.visibility = View.INVISIBLE
                        return@post
                    }
                    if (bitmap == null || bitmap.isRecycled) {
                        mask.setBackgroundColor(Color.BLACK)
                    } else {
                        mask.setBackground(BitmapDrawable(mask.context.resources, bitmap))
                    }
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * 遮罩淡出并隐藏。
     * 先保持不透明停留 [settleMs]，让系统完成本次壁纸刷新、画面稳定后再淡出，
     * 从而覆盖「整个变化过程」而不与画面切换重合。
     * 由后台 setBitmap 完成后触发。
     */
    private fun hideTransitionMask(settleMs: Long = MASK_SETTLE_MS) {
        val mask = MusicLockscreenManager.transitionMaskView ?: return
        try {
            if (mask.handler == null) return
            mask.handler.postDelayed({
                try {
                    // 停留期间已离开锁屏则直接隐藏，不再淡出
                    if (!HookUtils.isOnKeyguard(mask.context)) {
                        mask.animate().cancel()
                        mask.alpha = 0f
                        mask.visibility = View.INVISIBLE
                        return@postDelayed
                    }
                    mask.animate()
                        .alpha(0f)
                        .setDuration(MASK_FADE_MS)
                        .withEndAction {
                            mask.visibility = View.INVISIBLE
                        }
                        .start()
                } catch (_: Throwable) {
                }
            }, settleMs)
        } catch (_: Throwable) {
        }
    }

    private fun readBestMetadata(context: Context): android.media.MediaMetadata? {
        return AlbumArtResolver.getBindMetadata() ?: readMediaMetadata(context)
    }

    /** 构建「去掉专辑与阴影」的音乐壁纸背景图，用作进入时的遮罩过渡内容。 */
    private fun buildBackgroundOnly(
        context: Context,
        albumDrawable: Drawable?,
        metadata: android.media.MediaMetadata? = null,
        ignoreCache: Boolean = false
    ): Bitmap? {
        return try {
            val dm = context.resources.displayMetrics
            val albumBmp = AlbumArtResolver.resolve(
                context,
                albumDrawable,
                metadata ?: readBestMetadata(context),
                ignoreCache,
                AlbumArtResolver.getBindMediaData()
            ) ?: return null
            BlurUtils.blurWithBigAlbum(
                albumBmp,
                ConfigReader.blurRadius(context),
                ConfigReader.darkOverlay(context),
                false,
                dm.widthPixels,
                dm.heightPixels
            )
        } catch (e: Throwable) {
            logE("buildBackgroundOnly error", e)
            null
        }
    }

    fun toggle(context: Context, albumDrawable: Drawable?): Boolean {
        if (!HookUtils.isOnKeyguard(context)) {
            logI("toggle skipped: not on keyguard")
            return isMusicWallpaperSet
        }
        val residualAfterRestart = !isMusicWallpaperSet && ConfigReader.isWallpaperActive(context)
        return if (isMusicWallpaperSet || (residualAfterRestart && LockWallpaperBackup.isBackupActive(context))) {
            // 退出/恢复：成功后音乐壁纸不再展示
            showTransitionMask()
            restoreOriginalWallpaper(context)
            false
        } else {
            if (!HookUtils.isAllowedMusicApp(context)) {
                logI("toggle skipped: media not in whitelist")
                return false
            }
            showTransitionMask()
            setMusicWallpaper(context, albumDrawable)
            true
        }
    }

    fun setMusicWallpaper(context: Context, albumDrawable: Drawable?): Boolean {
        return setMusicWallpaper(context, albumDrawable, false, null)
    }

    fun setMusicWallpaper(
        context: Context,
        albumDrawable: Drawable?,
        force: Boolean,
        metadata: android.media.MediaMetadata? = null
    ): Boolean {
        val bestMeta = metadata ?: readBestMetadata(context)
        if (albumDrawable == null && bestMeta == null && AlbumArtResolver.getCached() == null) {
            return false
        }
        if (!HookUtils.isOnKeyguard(context)) {
            logI("setMusicWallpaper skipped: not on keyguard")
            return false
        }
        if (!HookUtils.isAllowedMusicApp(context)) {
            logI("setMusicWallpaper skipped: media not in whitelist")
            return false
        }
        if (isMusicWallpaperSet && !force) return true
        if (isAnimating) {
            logI("animation in progress, skip setMusicWallpaper")
            return false
        }

        // 切歌 / 换封面：静默更新（忽略旧缓存，用当前曲目新图）
        if (isMusicWallpaperSet && force) {
            return updateMusicWallpaperSilently(context, albumDrawable, bestMeta, ignoreCache = true)
        }

        try {
            saveOriginalWallpaper(context)
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onWallpaperAlbumPending()
            // 重新进入时忽略旧缓存，避免退出再打开仍显示上一首封面
            val wallpaperResult = buildBlurredBitmap(
                context,
                albumDrawable,
                bestMeta,
                ignoreCache = true
            ) ?: return false

            applyLockBitmapAsync(context, wallpaperResult) {
                buildBackgroundOnly(context, albumDrawable, bestMeta, ignoreCache = true)
            }

            // 已写入音乐壁纸，此后锁屏壁纸即为音乐壁纸（激活态），标记持久化以便重启后识别残留
            ConfigReader.setWallpaperActive(context, true)
            isMusicWallpaperSet = true
            isAnimating = true
            startSessionWatch(context.applicationContext)

            MusicLockscreenManager.updateBlurredBitmap(wallpaperResult.wallpaper)

            try {
                NumStateViewController.hide()
            } catch (e: Throwable) {
                logE("hide num_state_view error", e)
            }

            LockscreenNotificationController.forceHideNormalNotifications()
            MusicLockscreenManager.setShowingState(true)

            val lyricView = MusicLockscreenManager.lyricView
            if (lyricView != null) {
                TransitionAnimator.cancelCurrent()
                lyricView.animate().cancel()
                lyricView.alpha = 1f
                lyricView.translationY = 0f
                lyricView.scaleX = 1f
                lyricView.scaleY = 1f
                lyricView.visibility = android.view.View.VISIBLE
                (lyricView as? LockscreenLyricView)?.refreshVisibility()
                isAnimating = false
                logI("lyric shown without enter animation")
            } else {
                isAnimating = false
                logI("lyricView is null, skip enter animation")
            }

            logI("Music wallpaper set")
            return true
        } catch (e: Throwable) {
            logE("setMusicWallpaper error", e)
            isAnimating = false
            return false
        }
    }

    /**
     * 自动恢复"重启残留"的原锁屏壁纸。每次锁屏绑定调用：
     * 若内存态未在展示音乐壁纸、但持久激活标记仍为 true（进程重启后内存丢失、锁屏壁纸残留
     * 音乐），则自动恢复用户设定的干净锁屏壁纸并清除标记——用户无需先手动点一次恢复。
     */
    fun autoRestoreIfResidual(context: Context?) {
        val ctx = context ?: return
        if (isMusicWallpaperSet) return
        if (!ConfigReader.isWallpaperActive(ctx)) return
        try {
            val restored = restoreOriginalWallpaper(ctx)
            logI("autoRestoreIfResidual: restored=$restored")
        } catch (_: Throwable) {
        }
    }

    /**
     * 在干净状态下缓存当前锁屏壁纸为"原壁纸"。每次锁屏视图绑定调用：
     * 若音乐壁纸未激活（未开启 / 已正常恢复 / 用户改过锁屏壁纸后），锁屏显示的就是用户当前的
     * 原壁纸，据此缓存可跟随用户改壁纸；绝不在音乐壁纸激活态（残留）下缓存。
     */
    fun cacheOriginalWallpaperIfClean(context: Context?) {
        val ctx = context ?: return
        if (ConfigReader.isWallpaperActive(ctx) || isMusicWallpaperSet) return
        try {
            val bmp = HyperOsWallpaperBridge.captureOriginalLockWallpaper(ctx)
            if (bmp != null && !bmp.isRecycled) {
                LockWallpaperBackup.save(ctx, bmp)
            }
        } catch (_: Throwable) {
        }
    }

    fun restoreOriginalWallpaper(context: Context): Boolean {
        // 重启后内存态丢失，但仍可能处于音乐壁纸激活/残留，此时应尝试恢复。
        val shouldRestore = isMusicWallpaperSet ||
            ConfigReader.isWallpaperActive(context) ||
            LockWallpaperBackup.isBackupActive(context)
        if (!shouldRestore) return false
        if (originalLockWallpaper == null) {
            originalLockWallpaper = LockWallpaperBackup.load(context)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        }
        // 兜底：备份缺失时，以 root 读系统持久锁屏壁纸文件（用户设定的干净源），
        // 避免在锁屏已是音乐壁纸/残留时读到污染图。
        if (originalLockWallpaper == null) {
            originalLockWallpaper = HyperOsWallpaperBridge.captureOriginalLockWallpaper(context)
                ?.copy(Bitmap.Config.ARGB_8888, false)
            originalLockWallpaper?.let { LockWallpaperBackup.save(context, it) }
            logI("restore: fallback to system lock wallpaper source")
        }
        if (originalLockWallpaper == null) {
            logE("No original wallpaper to restore")
            return false
        }

        try {
            TransitionAnimator.cancelCurrent()
            isAnimating = false
            restoreWallpaperImmediately(context)
            logI("Original wallpaper restored (immediate)")
            return true
        } catch (e: Throwable) {
            logE("restoreOriginalWallpaper error", e)
            isAnimating = false
            return false
        }
    }

    private fun updateMusicWallpaperSilently(
        context: Context,
        albumDrawable: Drawable?,
        metadata: android.media.MediaMetadata? = null,
        ignoreCache: Boolean = false
    ): Boolean {
        if (!HookUtils.canApplyLockWallpaper(context)) {
            logI("silent update skipped: screen off or not on keyguard")
            markWallpaperStale()
            return false
        }
        try {
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onWallpaperAlbumPending()
            val wallpaperResult = buildBlurredBitmap(
                context,
                albumDrawable,
                metadata ?: readBestMetadata(context),
                ignoreCache
            )
            if (wallpaperResult == null) {
                logE("silent update: build failed, restore lyric fog if possible")
                ensureLyricFogReady()
                return false
            }
            applyLockBitmapAsync(context, wallpaperResult)
            MusicLockscreenManager.updateBlurredBitmap(wallpaperResult.wallpaper)
            logI("silent wallpaper update ok")
            return true
        } catch (e: Throwable) {
            logE("updateMusicWallpaperSilently error", e)
            ensureLyricFogReady()
            return false
        }
    }

    private data class BlurredWallpaperResult(
        val wallpaper: Bitmap,
        val album: Bitmap,
        val trackKey: String?
    )

    private fun buildBlurredBitmap(
        context: Context,
        albumDrawable: Drawable?,
        metadata: android.media.MediaMetadata? = null,
        ignoreCache: Boolean = false
    ): BlurredWallpaperResult? {
        val dm = context.resources.displayMetrics
        val bestMeta = metadata ?: readBestMetadata(context)
        val albumBitmap = AlbumArtResolver.resolve(
            context,
            albumDrawable,
            bestMeta,
            ignoreCache,
            AlbumArtResolver.getBindMediaData()
        )
            ?: run {
                logE("failed to resolve album bitmap")
                return null
            }
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        // 仅在成功应用后再写入 lastWallpaper*，避免 setBitmap 跳过却标记为已是新曲
        logI("album ${albumBitmap.width}x${albumBitmap.height}, screen=${dm.widthPixels}x${dm.heightPixels}, track=$trackKey")

        val wallpaper = BlurUtils.blurWithBigAlbum(
            albumBitmap,
            ConfigReader.blurRadius(context),
            ConfigReader.darkOverlay(context),
            ConfigReader.showBigAlbum(context),
            targetWidth = dm.widthPixels,
            targetHeight = dm.heightPixels,
            albumSizePercent = ConfigReader.albumSize(context),
            albumOffsetYDp = ConfigReader.albumOffsetY(context),
            albumCornerDp = ConfigReader.albumCorner(context)
        )
        return BlurredWallpaperResult(wallpaper, albumBitmap, trackKey)
    }

    private fun saveOriginalWallpaper(context: Context) {
        if (originalLockWallpaper != null) return
        // 仅按实时内存态判断是否正处于音乐壁纸展示（此时 FLAG_LOCK 已是音乐壁纸，不能缓存）。
// 持久激活标记不参与此处判定：它可能因重启/恢复场景残留，误判会导致开启时也不缓存原壁纸。
// 开启（setMusicWallpaper）时该函数在写音乐壁纸之前调用，此刻 FLAG_LOCK 仍是用户原壁纸，
// 缓存它即可跟随用户改壁纸。
        if (isMusicWallpaperSet) {
            logI("skip capture original: music wallpaper currently set")
            return
        }
        try {
            // 干净状态：此刻 FLAG_LOCK 是用户的原锁屏壁纸，锁屏≠桌面时也准确。
            val bmp = HyperOsWallpaperBridge.captureOriginalLockWallpaper(context)
            if (bmp != null && !bmp.isRecycled) {
                originalLockWallpaper = bmp.copy(Bitmap.Config.ARGB_8888, false)
                logI("Original lock wallpaper saved: ${originalLockWallpaper?.width}x${originalLockWallpaper?.height}")
            } else {
                logI("cannot capture original lock wallpaper")
            }
            originalLockWallpaper?.let { LockWallpaperBackup.save(context, it) }
        } catch (e: Throwable) {
            logE("saveOriginalWallpaper error", e)
        }
    }

    /**
     * 后台异步设置锁屏壁纸。
     * 可选 [maskBuilder] 在 setBitmap 前构建「去掉专辑与阴影」的背景图作为遮罩过渡内容；
     * 全程遮罩保持不透明，待 setBitmap 提交并停留 [MASK_SETTLE_MS] 后再淡出，覆盖整个变化过程。
     */
    private fun applyLockBitmapAsync(
        context: Context,
        result: BlurredWallpaperResult,
        maskBuilder: (() -> Bitmap?)? = null
    ) {
        val appCtx = context.applicationContext
        val copy = Bitmap.createBitmap(result.wallpaper)
        val albumForLyric = result.album.copy(
            result.album.config ?: Bitmap.Config.ARGB_8888,
            false
        )
        val trackKey = result.trackKey
        val hadMask = maskBuilder != null
        Thread {
            var applied = false
            try {
                if (!HookUtils.canApplyLockWallpaper(appCtx)) {
                    logI("applyLockBitmap skipped: screen off or not on keyguard")
                    markWallpaperStale()
                    return@Thread
                }
                val maskBmp = maskBuilder?.invoke()
                if (maskBmp != null && !maskBmp.isRecycled) {
                    setMaskImage(maskBmp)
                }
                WallpaperManager.getInstance(appCtx)
                    .setBitmap(copy, null, true, WallpaperManager.FLAG_LOCK)
                applied = true
                lastWallpaperAlbumBitmap = result.album
                lastWallpaperTrackKey = trackKey
            } catch (e: Throwable) {
                logE("applyLockBitmap error", e)
                markWallpaperStale()
            } finally {
                hideTransitionMask()
                if (!copy.isRecycled) copy.recycle()
                val settleDelay = if (hadMask) MASK_SETTLE_MS + MASK_FADE_MS else 80L
                mainHandler.postDelayed({
                    if (applied && (HookUtils.canApplyLockWallpaper(appCtx) ||
                            (isMusicWallpaperSet && HookUtils.isOnKeyguard(appCtx)))
                    ) {
                        MusicLockscreenManager.notifyWallpaperAppliedToLockScreen(
                            albumForLyric,
                            trackKey
                        )
                    } else {
                        if (!albumForLyric.isRecycled) albumForLyric.recycle()
                        // 壁纸未写入成功时补一次雾状背景，避免 pending 后一直空白
                        if (isMusicWallpaperSet) ensureLyricFogReady()
                    }
                }, settleDelay)
            }
        }.start()
    }

    private fun restoreWallpaperImmediately(context: Context) {
        try {
            val original = originalLockWallpaper ?: return

            stopSessionWatch()
            isMusicWallpaperSet = false
            lastWallpaperAlbumBitmap = null
            lastWallpaperTrackKey = null
            MusicLockscreenManager.updateBlurredBitmap(null)
            MusicLockscreenManager.setShowingState(false)
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.resetForMusicLockscreenOff()

            try {
                LockscreenNotificationController.showAllNotifications()
            } catch (e: Throwable) {
                logE("showAllNotifications error", e)
            }

            try {
                NumStateViewController.show()
            } catch (e: Throwable) {
                logE("show num_state_view error", e)
            }

            originalLockWallpaper = null
            LockWallpaperBackup.clear(context)
            // 已恢复为用户自己的壁纸，清除激活标记，进入干净状态（此后可重新缓存用户壁纸）
            ConfigReader.setWallpaperActive(context, false)

            logI("Original wallpaper restored")

            // setBitmap 是全量编码落盘 + 系统刷新，耗时几百 ms~1s，放到后台线程避免阻塞按钮响应
            val restoreBmp = original
            val appCtx = context.applicationContext
            // 退出过渡：遮罩改用原锁屏壁纸作为过渡内容，与恢复后的壁纸匹配，淡出时不闪烁
            setMaskImage(restoreBmp)
            Thread {
                try {
                    WallpaperManager.getInstance(appCtx)
                        .setBitmap(restoreBmp, null, true, WallpaperManager.FLAG_LOCK)
                } catch (e: Throwable) {
                    logE("restore setBitmap error", e)
                } finally {
                    // setBitmap 返回即已提交系统刷新，退出恢复停留更久后再淡出，避免露出切换过程
                    hideTransitionMask(MASK_SETTLE_EXIT_MS)
                }
            }.start()
        } catch (e: Throwable) {
            logE("restoreWallpaperImmediately error", e)
        }
    }

    fun isShowing(): Boolean = isMusicWallpaperSet

    fun isAnimating(): Boolean = isAnimating

    /**
     * 重新上锁进入音乐锁屏时调用：
     * 若期间切过歌，用最新缓存的专辑封面重建锁屏壁纸；
     * 曲目未变则补渲染歌词雾状背景（防止上次 pending 后未 ready）。
     */
    fun refreshMusicWallpaper(context: Context? = null): Boolean {
        val ctx = context ?: sessionWatchContext ?: return false
        if (!isMusicWallpaperSet) return false
        if (!HookUtils.canApplyLockWallpaper(ctx)) {
            logI("refreshMusicWallpaper skipped: screen off or not on keyguard")
            ensureLyricFogReady()
            return false
        }
        // 用当前会话 metadata 刷新解析缓存，避免解锁期间切歌后仍用旧 trackKey
        try {
            val meta = readBestMetadata(ctx)
            AlbumArtResolver.resolve(ctx, null, meta, ignoreCache = false, AlbumArtResolver.getBindMediaData())
        } catch (_: Throwable) {
        }
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        if (trackKey != null && trackKey == lastWallpaperTrackKey) {
            ensureLyricFogReady()
            return false
        }
        logI("refreshing music wallpaper with latest album art, track=$trackKey")
        return updateMusicWallpaperSilently(ctx, null, null, ignoreCache = true)
    }

    /** 标记壁纸与当前曲目可能不一致，下次进锁屏强制刷新。 */
    fun markWallpaperStale() {
        lastWallpaperTrackKey = null
        logI("wallpaper marked stale")
    }

    /** 若歌词雾状背景缺失，用最近成功应用的专辑补渲染。 */
    fun ensureLyricFogReady() {
        val lyric = MusicLockscreenManager.lyricView as? LockscreenLyricView ?: return
        if (lyric.isFogBackgroundReady()) return
        val album = lastWallpaperAlbumBitmap?.takeIf { !it.isRecycled }
            ?: AlbumArtResolver.getCached()
            ?: return
        val copy = try {
            album.copy(album.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            return
        }
        lyric.onWallpaperAlbumReady(copy, lastWallpaperTrackKey ?: AlbumArtResolver.getCachedTrackKey())
    }

    private fun startSessionWatch(context: Context) {
        stopSessionWatch()
        sessionWatchContext = context.applicationContext
        val runnable = object : Runnable {
            override fun run() {
                if (!isMusicWallpaperSet) return
                val ctx = sessionWatchContext ?: return
                if (!HookUtils.isOnKeyguard(ctx)) {
                    sessionPollHandler.postDelayed(this, SESSION_POLL_INTERVAL_MS)
                    return
                }
                if (!hasActiveMediaSession(ctx)) {
                    logI("no active media session, exiting music lockscreen")
                    restoreOriginalWallpaper(ctx)
                    return
                }
                sessionPollHandler.postDelayed(this, SESSION_POLL_INTERVAL_MS)
            }
        }
        sessionPollRunnable = runnable
        sessionPollHandler.postDelayed(runnable, SESSION_POLL_INTERVAL_MS)
    }

    private fun stopSessionWatch() {
        sessionPollRunnable?.let { sessionPollHandler.removeCallbacks(it) }
        sessionPollRunnable = null
        sessionWatchContext = null
    }

    private fun hasActiveMediaSession(context: Context): Boolean {
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? android.media.session.MediaSessionManager ?: return true
            val controllers = mgr.getActiveSessions(null)
            controllers.any { controller ->
                if (!ConfigReader.isAllowedMusicApp(context, controller.packageName)) {
                    return@any false
                }
                val state = controller.playbackState?.state ?: return@any true
                state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_PAUSED ||
                    state == PlaybackState.STATE_BUFFERING
            }
        } catch (e: Throwable) {
            logE("hasActiveMediaSession error", e)
            true
        }
    }

    private fun readMediaMetadata(context: Context): android.media.MediaMetadata? {
        return try {
            val mgr = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE)
                as? android.media.session.MediaSessionManager ?: return null
            mgr.getActiveSessions(null).firstOrNull()?.metadata
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
