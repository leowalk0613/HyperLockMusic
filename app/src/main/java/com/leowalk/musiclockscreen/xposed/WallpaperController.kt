package com.leowalk.musiclockscreen.xposed

import android.app.WallpaperManager
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import com.leowalk.musiclockscreen.xposed.wallpaper.SerialWallpaperApplier
import com.leowalk.musiclockscreen.xposed.wallpaper.TrackWallpaperCoordinator
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 壁纸管理器（HyperOS 4）
 *
 * 锁屏壁纸使用 WallpaperManager.setBitmap(FLAG_LOCK)，仅影响锁屏位。
 * 切歌路径经 [TrackWallpaperCoordinator] + [SerialWallpaperApplier]：过期 job 在写入前取消。
 */
object WallpaperController {

    private const val tag = "HyperLockMusic_Wallpaper"

    private var originalLockWallpaper: Bitmap? = null
    private var isMusicWallpaperSet = false
    private var isAnimating = false

    // 当前锁屏壁纸所基于的专辑封面与曲目（用于判断切歌后是否需要重建）
    private var lastWallpaperAlbumBitmap: Bitmap? = null
    private var lastWallpaperTrackKey: String? = null
    /** 当前曲目的系统封面（模糊背景 / 歌词 fog 同源） */
    private var lastSystemAlbumBitmap: Bitmap? = null
    /** 当前曲目网络高清专辑（仅前景大封面） */
    private var lastNetworkAlbumBitmap: Bitmap? = null
    /** 已用网络高清成功写入前景的曲目，避免同一曲重复拉取 */
    private var lastNetworkAlbumTrackKey: String? = null
    private var networkAlbumGeneration: Long = 0L
    /** 沉浸专辑合成态与当前配置不一致，需重建壁纸布局（曲目可不变） */
    private var wallpaperLayoutStale = false
    private var lastBakedImmersiveAlbum: Boolean? = null

    private val pipelineGate = ReentrantLock()
    private val pipeline = TrackWallpaperCoordinator()
    private val wallpaperApplier = SerialWallpaperApplier(
        shouldWrite = { jobId ->
            pipelineGate.withLock {
                if (jobId < 0L) pipeline.shouldWriteRestore(-jobId)
                else pipeline.shouldWriteApply(jobId)
            }
        }
    )

    /** 同曲双布局缓存：大专辑（仅模糊）与沉浸（烘焙封面）并行生成，互转时直接换 */
    private var dualLargeWallpaper: Bitmap? = null
    private var dualImmersiveWallpaper: Bitmap? = null
    private var dualCacheTrackKey: String? = null
    private var dualCacheCenterY: Float = Float.NaN
    private var dualCacheBlurRadius: Float = Float.NaN
    private var dualCacheDarkOverlay: Int = -1

    private fun appliedWallpaperTrackKey(): String? =
        pipelineGate.withLock { pipeline.appliedTrackKey() }

    private fun isTrackWallpaperInFlight(trackKey: String?): Boolean =
        pipelineGate.withLock { pipeline.isTrackInFlight(trackKey) }

    private val sessionPollHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionPollRunnable: Runnable? = null
    private var trackPollRunnable: Runnable? = null
    private var sessionWatchContext: Context? = null
    private var trackedMediaController: MediaController? = null
    private var mediaMetadataCallback: MediaController.Callback? = null
    private var mediaStateObserver: ContentObserver? = null

    private const val SESSION_POLL_INTERVAL_MS = 2000L
    /** AOD / 锁屏切歌：MediaSession 轮询兜底（Callback 为主） */
    private const val TRACK_POLL_INTERVAL_MS = 1200L

    /** setBitmap 提交后到开始淡出的停留时长，覆盖系统壁纸刷新/交叉渐变，避免淡出与画面切换重合而闪烁。 */
    private const val MASK_SETTLE_MS = 240L

    /** 退出恢复时遮罩停留时长（系统回切原壁纸往往更慢，需更长覆盖切换过程）。 */
    private const val MASK_SETTLE_EXIT_MS = 480L

    /** 遮罩淡出时长 */
    private const val MASK_FADE_MS = 220L

    /**
     * 大专辑↔沉浸布局切换：setBitmap 返回后仍需等待系统壁纸交叉渐变结束，
     * 期间继续用方形 overlay 挡住，再淡出/显示。
     */
    private const val LAYOUT_OVERLAY_SETTLE_MS = 520L

    /** restore 任务使用负 jobId，避免与音乐 job 冲突 */
    private fun restoreJobId(epoch: Long): Long = -epoch

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
                    val d = mask.resources.displayMetrics.density
                    mask.elevation = 64f * d
                    mask.translationZ = 64f * d
                    mask.bringToFront()
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
                        mask.background = HookUtils.fillDrawable(mask.context, bitmap)
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
                            // 遮罩收起后把歌词拉回最上层
                            MusicLockscreenManager.lyricView?.let { lyric ->
                                try {
                                    val d = lyric.resources.displayMetrics.density
                                    lyric.elevation = 48f * d
                                    lyric.translationZ = 48f * d
                                    lyric.bringToFront()
                                } catch (_: Throwable) {
                                }
                            }
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
            val (tw, th) = HookUtils.lockScreenWallpaperSize(context)
            val albumBmp = AlbumArtResolver.resolve(
                context,
                albumDrawable,
                metadata ?: readBestMetadata(context),
                ignoreCache,
                AlbumArtResolver.getBindMediaData()
            ) ?: return null
            BlurUtils.blurWithBigAlbum(
                blurSource = albumBmp,
                radius = ConfigReader.blurRadius(context),
                darkOverlayAlpha = ConfigReader.darkOverlay(context),
                showBigAlbum = false,
                targetWidth = tw,
                targetHeight = th
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
        // 切歌强制刷新不得被进场动画挡住，否则概率性整曲不更新
        if (isAnimating && !force) {
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
            val trackKeyHint = try {
                AlbumArtResolver.getCachedTrackKey()
            } catch (_: Throwable) {
                null
            }
            val mustIgnoreCache = wallpaperLayoutStale ||
                (trackKeyHint != null && trackKeyHint != lastWallpaperTrackKey) ||
                lastWallpaperTrackKey == null
            val wallpaperResult = buildBlurredBitmap(
                context,
                albumDrawable,
                bestMeta,
                ignoreCache = mustIgnoreCache,
                allowRemote = false
            )
            if (wallpaperResult == null) {
                logI("setMusicWallpaper: local art pending, enter + silent async")
                ConfigReader.setWallpaperActive(context, true)
                isMusicWallpaperSet = true
                startSessionWatch(context.applicationContext)
                try {
                    NumStateViewController.syncVisibility()
                } catch (e: Throwable) {
                    logE("sync num_state_view error", e)
                }
                LockscreenNotificationController.forceHideNormalNotifications()
                MusicLockscreenManager.setShowingState(true)
                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.ensureLyricsLoaded()
                updateMusicWallpaperSilently(context, albumDrawable, bestMeta, ignoreCache = true)
                return true
            }

            val jobId = pipelineGate.withLock {
                val job = pipeline.submitTrackIntent(wallpaperResult.trackKey).job
                    ?: return@withLock null
                pipeline.markBuilding(job.jobId)
                pipeline.markPreviewed(job.jobId)
                job.jobId
            } ?: return false

            MusicLockscreenManager.updateBlurredBitmap(wallpaperResult.wallpaper)
            notifyAlbumVisualsImmediate(wallpaperResult)

            val albumForNetwork = wallpaperResult.systemAlbum.copy(
                wallpaperResult.systemAlbum.config ?: Bitmap.Config.ARGB_8888,
                false
            )
            val trackForNetwork = wallpaperResult.trackKey
            applyLockBitmapAsync(
                context,
                wallpaperResult,
                jobId = jobId,
                maskBuilder = {
                    buildBackgroundOnly(context, albumDrawable, bestMeta, ignoreCache = true)
                },
                onSettled = {
                    scheduleNetworkAlbumEnhance(context, albumForNetwork, trackForNetwork, jobId)
                },
                notifyLyricOnSettle = false
            )

            ConfigReader.setWallpaperActive(context, true)
            isMusicWallpaperSet = true
            isAnimating = true
            startSessionWatch(context.applicationContext)

            try {
                NumStateViewController.syncVisibility()
            } catch (e: Throwable) {
                logE("sync num_state_view error", e)
            }

            LockscreenNotificationController.forceHideNormalNotifications()
            MusicLockscreenManager.setShowingState(true)

            val lyricView = MusicLockscreenManager.lyricView
            if (lyricView != null) {
                TransitionAnimator.cancelCurrent()
                lyricView.animate().cancel()
                lyricView.alpha = 1f
                lyricView.scaleX = 1f
                lyricView.scaleY = 1f
                lyricView.visibility = android.view.View.VISIBLE
                (lyricView as? LockscreenLyricView)?.let { lv ->
                    lv.ensureLyricsLoaded()
                    lv.refreshVisibility()
                }
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
        val targetKey = AlbumArtResolver.getCachedTrackKey()
        val submit = pipelineGate.withLock { pipeline.submitTrackIntent(targetKey) }
        if (!submit.startBuild || submit.job == null) {
            logI(
                "silent update coalesced: in-flight track=$targetKey " +
                    "applied=${appliedWallpaperTrackKey()}"
            )
            return true
        }
        val jobId = submit.job!!.jobId
        try {
            networkAlbumGeneration++
            lastNetworkAlbumTrackKey = null
            lastNetworkAlbumBitmap = null
            clearDualWallpaperCache()
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onWallpaperAlbumPending()
            val appCtx = context.applicationContext
            val metaRef = metadata ?: readBestMetadata(context)
            val drawableRef = albumDrawable
            val ignoreRef = ignoreCache
            pipelineGate.withLock { pipeline.markBuilding(jobId) }
            Thread {
                try {
                    if (!pipelineGate.withLock { pipeline.isJobCurrent(jobId) }) {
                        logI("silent build superseded before start job=$jobId")
                        return@Thread
                    }
                    if (!HookUtils.canApplyLockWallpaper(appCtx)) {
                        logI("silent update aborted: left keyguard before build")
                        markWallpaperStale()
                        pipelineGate.withLock { pipeline.markBuildFailed(jobId) }
                        return@Thread
                    }
                    var wallpaperResult = buildBlurredBitmap(
                        appCtx,
                        drawableRef,
                        metaRef,
                        ignoreRef,
                        allowRemote = false
                    )
                    if (wallpaperResult == null &&
                        pipelineGate.withLock { pipeline.isJobCurrent(jobId) }
                    ) {
                        wallpaperResult = buildBlurredBitmap(
                            appCtx,
                            drawableRef,
                            metaRef,
                            ignoreRef,
                            allowRemote = true
                        )
                    }
                    if (!pipelineGate.withLock { pipeline.isJobCurrent(jobId) }) {
                        logI("silent build superseded after build job=$jobId")
                        return@Thread
                    }
                    if (wallpaperResult == null) {
                        logE("silent update: build failed, keep lagging for poll/retry")
                        wallpaperLayoutStale = true
                        pipelineGate.withLock { pipeline.markBuildFailed(jobId) }
                        return@Thread
                    }
                    pipelineGate.withLock { pipeline.markPreviewed(jobId) }
                    mainHandler.post {
                        if (!pipelineGate.withLock { pipeline.isJobCurrent(jobId) }) return@post
                        MusicLockscreenManager.updateBlurredBitmap(wallpaperResult.wallpaper)
                        notifyAlbumVisualsImmediate(wallpaperResult)
                    }
                    val albumForNetwork = wallpaperResult.systemAlbum.copy(
                        wallpaperResult.systemAlbum.config ?: Bitmap.Config.ARGB_8888,
                        false
                    )
                    val trackForNetwork = wallpaperResult.trackKey
                    applyLockBitmapAsync(
                        appCtx,
                        wallpaperResult,
                        jobId = jobId,
                        maskBuilder = null,
                        onSettled = {
                            scheduleNetworkAlbumEnhance(
                                appCtx, albumForNetwork, trackForNetwork, jobId
                            )
                        },
                        notifyLyricOnSettle = false
                    )
                    logI(
                        "silent wallpaper update ok (system first), " +
                            "track=$trackForNetwork job=$jobId"
                    )
                } catch (e: Throwable) {
                    logE("updateMusicWallpaperSilently async error", e)
                    wallpaperLayoutStale = true
                    pipelineGate.withLock { pipeline.markBuildFailed(jobId) }
                }
            }.start()
            return true
        } catch (e: Throwable) {
            logE("updateMusicWallpaperSilently error", e)
            pipelineGate.withLock { pipeline.markBuildFailed(jobId) }
            return false
        }
    }

    private data class BlurredWallpaperResult(
        val wallpaper: Bitmap,
        val systemAlbum: Bitmap,
        val trackKey: String?
    )

    private data class DualWallpaperResult(
        val large: Bitmap,
        val immersive: Bitmap,
        val systemAlbum: Bitmap,
        val trackKey: String?,
        val centerY: Float,
        val blurRadius: Float,
        val darkOverlay: Int
    )

    private fun clearDualWallpaperCache() {
        val active = MusicLockscreenManager.blurredWallpaperBitmap
        dualLargeWallpaper?.takeIf { it !== active && !it.isRecycled }?.recycle()
        dualImmersiveWallpaper?.takeIf {
            it !== active && it !== dualLargeWallpaper && !it.isRecycled
        }?.recycle()
        dualLargeWallpaper = null
        dualImmersiveWallpaper = null
        dualCacheTrackKey = null
        dualCacheCenterY = Float.NaN
        dualCacheBlurRadius = Float.NaN
        dualCacheDarkOverlay = -1
    }

    private fun storeDualWallpaperCache(dual: DualWallpaperResult) {
        val prevLarge = dualLargeWallpaper
        val prevImmersive = dualImmersiveWallpaper
        val active = MusicLockscreenManager.blurredWallpaperBitmap
        dualLargeWallpaper = dual.large
        dualImmersiveWallpaper = dual.immersive
        dualCacheTrackKey = dual.trackKey
        dualCacheCenterY = dual.centerY
        dualCacheBlurRadius = dual.blurRadius
        dualCacheDarkOverlay = dual.darkOverlay
        if (prevLarge != null &&
            prevLarge !== dual.large &&
            prevLarge !== dual.immersive &&
            prevLarge !== active &&
            !prevLarge.isRecycled
        ) {
            prevLarge.recycle()
        }
        if (prevImmersive != null &&
            prevImmersive !== dual.large &&
            prevImmersive !== dual.immersive &&
            prevImmersive !== active &&
            !prevImmersive.isRecycled
        ) {
            prevImmersive.recycle()
        }
    }

    /** 取目标布局的已缓存壁纸（曲目与模糊/沉浸参数一致时命中）。 */
    private fun takeDualCachedWallpaper(
        context: Context,
        bakeImmersive: Boolean
    ): BlurredWallpaperResult? {
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        if (trackKey == null || trackKey != dualCacheTrackKey) return null
        val blur = ConfigReader.blurRadius(context)
        val dark = ConfigReader.darkOverlay(context)
        if (blur != dualCacheBlurRadius || dark != dualCacheDarkOverlay) return null
        if (bakeImmersive) {
            val centerY = ConfigReader.immersiveAlbumCenterY(context)
            if (centerY != dualCacheCenterY) return null
            val bmp = dualImmersiveWallpaper?.takeIf { !it.isRecycled } ?: return null
            val album = lastSystemAlbumBitmap?.takeIf { !it.isRecycled }
                ?: AlbumArtResolver.getCached()
                ?: return null
            return BlurredWallpaperResult(bmp, album, trackKey)
        }
        val bmp = dualLargeWallpaper?.takeIf { !it.isRecycled } ?: return null
        val album = lastSystemAlbumBitmap?.takeIf { !it.isRecycled }
            ?: AlbumArtResolver.getCached()
            ?: return null
        return BlurredWallpaperResult(bmp, album, trackKey)
    }

    /**
     * 同一张专辑并行生成「大专辑模糊底」与「沉浸烘焙」两套壁纸。
     */
    private fun buildDualWallpapers(
        context: Context,
        albumDrawable: Drawable?,
        metadata: android.media.MediaMetadata? = null,
        ignoreCache: Boolean = false,
        allowRemote: Boolean = true
    ): DualWallpaperResult? {
        val (tw, th) = HookUtils.lockScreenWallpaperSize(context)
        val bestMeta = metadata ?: readBestMetadata(context)
        val systemAlbum = AlbumArtResolver.resolve(
            context,
            albumDrawable,
            bestMeta,
            ignoreCache = ignoreCache,
            AlbumArtResolver.getBindMediaData(),
            allowRemote = allowRemote
        ) ?: run {
            logE("failed to resolve album bitmap")
            return null
        }
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        val sharpAlbum = if (trackKey != null && trackKey == lastNetworkAlbumTrackKey) {
            lastNetworkAlbumBitmap?.takeIf { !it.isRecycled }
        } else {
            null
        }
        val radius = ConfigReader.blurRadius(context)
        val dark = ConfigReader.darkOverlay(context)
        val centerY = ConfigReader.immersiveAlbumCenterY(context)
        val blurAlbum = sharpAlbum ?: systemAlbum
        val immersiveSharp = sharpAlbum ?: systemAlbum
        logI(
            "dual album system=${systemAlbum.width}x${systemAlbum.height}" +
                (sharpAlbum?.let { " network=${it.width}x${it.height}" } ?: "") +
                ", screen=${tw}x${th}, track=$trackKey"
        )

        var largeBmp: Bitmap? = null
        var immersiveBmp: Bitmap? = null
        var largeError: Throwable? = null
        var immersiveError: Throwable? = null
        val largeThread = Thread {
            try {
                largeBmp = BlurUtils.blurWithBigAlbum(
                    blurSource = blurAlbum,
                    radius = radius,
                    darkOverlayAlpha = dark,
                    showBigAlbum = false,
                    targetWidth = tw,
                    targetHeight = th,
                    albumSizePercent = ConfigReader.albumSize(context),
                    albumOffsetYDp = ConfigReader.albumOffsetY(context),
                    albumCornerDp = ConfigReader.albumCorner(context),
                    sharpAlbum = null
                )
            } catch (e: Throwable) {
                largeError = e
            }
        }
        val immersiveThread = Thread {
            try {
                immersiveBmp = BlurUtils.blurWithImmersiveAlbum(
                    blurSource = systemAlbum,
                    sharpAlbum = immersiveSharp,
                    radius = radius,
                    darkOverlayAlpha = dark,
                    targetWidth = tw,
                    targetHeight = th,
                    albumAnchorYPercent = 80f,
                    albumCenterYPercent = centerY,
                )
            } catch (e: Throwable) {
                immersiveError = e
            }
        }
        largeThread.start()
        immersiveThread.start()
        largeThread.join()
        immersiveThread.join()
        largeError?.let { logE("dual large build error", it) }
        immersiveError?.let { logE("dual immersive build error", it) }
        val large = largeBmp
        val immersive = immersiveBmp
        if (large == null || immersive == null) {
            large?.takeIf { !it.isRecycled }?.recycle()
            immersive?.takeIf { !it.isRecycled }?.recycle()
            return null
        }
        return DualWallpaperResult(
            large = large,
            immersive = immersive,
            systemAlbum = systemAlbum,
            trackKey = trackKey,
            centerY = centerY,
            blurRadius = radius,
            darkOverlay = dark
        )
    }

    private fun buildBlurredBitmap(
        context: Context,
        albumDrawable: Drawable?,
        metadata: android.media.MediaMetadata? = null,
        ignoreCache: Boolean = false,
        allowRemote: Boolean = true
    ): BlurredWallpaperResult? {
        val dual = buildDualWallpapers(
            context,
            albumDrawable,
            metadata,
            ignoreCache,
            allowRemote
        ) ?: return null
        storeDualWallpaperCache(dual)
        val bake = ConfigReader.shouldBakeImmersiveAlbumInWallpaper(context)
        val wallpaper = if (bake) dual.immersive else dual.large
        if (!bake) {
            val overlayAlbum = lastNetworkAlbumBitmap?.takeIf {
                !it.isRecycled && dual.trackKey != null && dual.trackKey == lastNetworkAlbumTrackKey
            } ?: dual.systemAlbum
            val overlayCopy = try {
                overlayAlbum.copy(overlayAlbum.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (_: Throwable) {
                null
            }
            val overlayTrack = dual.trackKey
            if (overlayCopy != null) {
                mainHandler.post {
                    val current = AlbumArtResolver.getCachedTrackKey()
                    if (overlayTrack != null && current != null && overlayTrack != current) {
                        if (!overlayCopy.isRecycled) overlayCopy.recycle()
                        return@post
                    }
                    MusicLockscreenManager.updateAlbumBitmap(overlayCopy)
                }
            }
        }
        return BlurredWallpaperResult(wallpaper, dual.systemAlbum, dual.trackKey)
    }

    /**
     * 系统封面已显示后：后台拉网易云高清，只替换前景大专辑；模糊背景仍用系统封面。
     */
    private fun scheduleNetworkAlbumEnhance(
        context: Context,
        album: Bitmap,
        trackKey: String?,
        jobId: Long
    ) {
        if (!ConfigReader.albumNetworkHd(context) || !ConfigReader.showBigAlbum(context)) {
            if (!album.isRecycled) album.recycle()
            return
        }
        if (!pipelineGate.withLock { pipeline.shouldScheduleEnhance(jobId, trackKey) }) {
            logI("album network enhance skipped: job not settled job=$jobId")
            if (!album.isRecycled) album.recycle()
            return
        }
        if (trackKey != null && trackKey == lastNetworkAlbumTrackKey) {
            logI("album network enhance skipped: already enhanced for $trackKey")
            if (!album.isRecycled) album.recycle()
            return
        }
        val minSide = minOf(album.width, album.height)
        if (minSide >= 1080) {
            lastNetworkAlbumTrackKey = trackKey
            logI("album network enhance skipped: already ${album.width}x${album.height}")
            if (!album.isRecycled) album.recycle()
            return
        }
        if (!pipelineGate.withLock { pipeline.beginEnhance(jobId) }) {
            if (!album.isRecycled) album.recycle()
            return
        }
        val gen = ++networkAlbumGeneration
        val appCtx = context.applicationContext
        val metadata = AlbumArtResolver.getBindMetadata()
        val mediaData = AlbumArtResolver.getBindMediaData()
        logI("album network enhance scheduled ${album.width}x${album.height} track=$trackKey")
        Thread {
            try {
                val enhanced = NetEaseAlbumArtSource.fetchVerifiedHighRes(
                    appCtx, album, metadata, mediaData, trackKey
                )
                if (enhanced == null || enhanced.isRecycled) {
                    logI("album network enhance: no verified high-res")
                    pipelineGate.withLock { pipeline.markEnhanceDone(jobId) }
                    return@Thread
                }
                if (gen != networkAlbumGeneration || !isMusicWallpaperSet ||
                    !pipelineGate.withLock { pipeline.shouldScheduleEnhance(jobId, trackKey) }
                ) {
                    if (!enhanced.isRecycled) enhanced.recycle()
                    logI("album network result discarded: stale job/generation")
                    pipelineGate.withLock { pipeline.markEnhanceDone(jobId) }
                    return@Thread
                }
                lastNetworkAlbumBitmap = enhanced
                mainHandler.post {
                    if (gen != networkAlbumGeneration || !isMusicWallpaperSet) return@post
                    if (!pipelineGate.withLock {
                            pipeline.shouldScheduleEnhance(jobId, trackKey)
                        }
                    ) {
                        return@post
                    }
                    lastNetworkAlbumTrackKey = trackKey
                    if (ConfigReader.shouldBakeImmersiveAlbumInWallpaper(appCtx)) {
                        rebuildWallpaperWithNetworkAlbum(appCtx, enhanced, trackKey, jobId)
                    } else {
                        MusicLockscreenManager.updateAlbumBitmap(enhanced)
                        MusicLockscreenManager.showAlbumOverlay()
                        logI(
                            "album network overlay applied ${enhanced.width}x${enhanced.height}, " +
                                "blur wallpaper unchanged, track=$trackKey"
                        )
                        pipelineGate.withLock { pipeline.markEnhanceDone(jobId) }
                    }
                }
            } catch (e: Throwable) {
                logE("scheduleNetworkAlbumEnhance error", e)
                pipelineGate.withLock { pipeline.markEnhanceDone(jobId) }
            } finally {
                if (!album.isRecycled) album.recycle()
            }
        }.start()
    }

    /**
     * 网络高清就绪后重建壁纸（保留 [lastNetworkAlbumBitmap]，避免被静默更新清掉）。
     */
    private fun rebuildWallpaperWithNetworkAlbum(
        context: Context,
        enhanced: Bitmap,
        trackKey: String?,
        parentJobId: Long
    ): Boolean {
        if (!isMusicWallpaperSet) return false
        if (!HookUtils.canApplyLockWallpaper(context)) {
            logI("network album rebuild skipped: screen off or not on keyguard")
            return false
        }
        if (!pipelineGate.withLock { pipeline.shouldScheduleEnhance(parentJobId, trackKey) }) {
            return false
        }
        try {
            lastNetworkAlbumBitmap = enhanced
            lastNetworkAlbumTrackKey = trackKey
            val wallpaperResult = buildBlurredBitmap(context, null, null, ignoreCache = false)
                ?: run {
                    logE("network album rebuild: build failed")
                    pipelineGate.withLock { pipeline.markEnhanceDone(parentJobId) }
                    return false
                }
            val layoutSubmit = pipelineGate.withLock {
                pipeline.submitLayoutApply(trackKey)
            }
            val jobId = layoutSubmit.job?.jobId ?: run {
                pipelineGate.withLock { pipeline.markEnhanceDone(parentJobId) }
                return false
            }
            applyLockBitmapAsync(
                context,
                wallpaperResult,
                jobId = jobId,
                maskBuilder = null,
                onSettled = {
                    pipelineGate.withLock { pipeline.markEnhanceDone(parentJobId) }
                }
            )
            MusicLockscreenManager.updateBlurredBitmap(wallpaperResult.wallpaper)
            logI(
                "album network enhance applied to wallpaper ${enhanced.width}x${enhanced.height}, " +
                    "track=$trackKey"
            )
            return true
        } catch (e: Throwable) {
            logE("rebuildWallpaperWithNetworkAlbum error", e)
            pipelineGate.withLock { pipeline.markEnhanceDone(parentJobId) }
            return false
        }
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
     * 串行异步设置锁屏壁纸。过期 job 在 setBitmap 前取消，禁止「写完再 supersede」。
     */
    private fun applyLockBitmapAsync(
        context: Context,
        result: BlurredWallpaperResult,
        jobId: Long,
        maskBuilder: (() -> Bitmap?)? = null,
        onSettled: (() -> Unit)? = null,
        notifyLyricOnSettle: Boolean = true,
        settleDelayMs: Long? = null
    ) {
        val appCtx = context.applicationContext
        val copy = Bitmap.createBitmap(result.wallpaper)
        val albumForLyric = if (notifyLyricOnSettle) {
            result.systemAlbum.copy(
                result.systemAlbum.config ?: Bitmap.Config.ARGB_8888,
                false
            )
        } else {
            null
        }
        val trackKey = result.trackKey
        val hadMask = maskBuilder != null
        pipelineGate.withLock { pipeline.markApplying(jobId) }

        wallpaperApplier.enqueue(
            jobId = jobId,
            write = {
                pipelineGate.withLock {
                    if (!pipeline.shouldWriteApply(jobId)) {
                        logI("applyLockBitmap cancelled before write: superseded job=$jobId")
                        return@withLock false
                    }
                    if (!HookUtils.canApplyLockWallpaper(appCtx)) {
                        logI("applyLockBitmap skipped: screen off or not on keyguard")
                        // 释放锁后再 mark stale 会嵌套；此处只返回 false，外层处理
                        return@withLock false
                    }
                    try {
                        val maskBmp = maskBuilder?.invoke()
                        if (maskBmp != null && !maskBmp.isRecycled) {
                            setMaskImage(maskBmp)
                        }
                        WallpaperManager.getInstance(appCtx)
                            .setBitmap(copy, null, true, WallpaperManager.FLAG_LOCK)
                        if (!pipeline.markApplyCommitted(jobId, trackKey)) {
                            logI("applyLockBitmap commit rejected after write job=$jobId")
                            return@withLock false
                        }
                        lastWallpaperAlbumBitmap = result.systemAlbum
                        lastSystemAlbumBitmap = result.systemAlbum
                        lastWallpaperTrackKey = trackKey
                        lastBakedImmersiveAlbum =
                            ConfigReader.shouldBakeImmersiveAlbumInWallpaper(appCtx)
                        wallpaperLayoutStale = false
                        true
                    } catch (e: Throwable) {
                        logE("applyLockBitmap error", e)
                        false
                    }
                }
            },
            onCommitted = {
                if (hadMask) hideTransitionMask()
                if (!copy.isRecycled) copy.recycle()
                val settleDelay = settleDelayMs ?: when {
                    hadMask -> MASK_SETTLE_MS + MASK_FADE_MS
                    notifyLyricOnSettle -> 0L
                    else -> 0L
                }
                mainHandler.postDelayed({
                    if (!pipelineGate.withLock { pipeline.isJobCurrent(jobId) }) {
                        albumForLyric?.takeIf { !it.isRecycled }?.recycle()
                        return@postDelayed
                    }
                    if (HookUtils.canApplyLockWallpaper(appCtx) ||
                        (isMusicWallpaperSet && HookUtils.isOnKeyguard(appCtx))
                    ) {
                        if (notifyLyricOnSettle && albumForLyric != null) {
                            MusicLockscreenManager.notifyWallpaperAppliedToLockScreen(
                                albumForLyric,
                                trackKey
                            )
                        } else {
                            albumForLyric?.takeIf { !it.isRecycled }?.recycle()
                        }
                        try {
                            onSettled?.invoke()
                        } catch (e: Throwable) {
                            logE("onSettled error", e)
                        }
                    } else {
                        albumForLyric?.takeIf { !it.isRecycled }?.recycle()
                        if (isMusicWallpaperSet) ensureLyricFogReady()
                    }
                }, settleDelay)
            },
            onCancelled = {
                if (!copy.isRecycled) copy.recycle()
                albumForLyric?.takeIf { !it.isRecycled }?.recycle()
                logI("applyLockBitmap cancelled job=$jobId track=$trackKey")
            },
            onError = { e ->
                if (!copy.isRecycled) copy.recycle()
                albumForLyric?.takeIf { !it.isRecycled }?.recycle()
                logE("applyLockBitmap queue error", e)
                markWallpaperStale()
            }
        )
    }

    /**
     * 壁纸写入前立即刷新专辑 overlay 与歌词取色（切歌即时感）。
     * 注意：不得在此处 commit appliedWallpaperTrackKey / lastWallpaperTrackKey，
     * 否则 setBitmap 失败或被取消时轮询会误判「已刷新」而永久卡住，直到重新进入音乐锁屏。
     */
    private fun notifyAlbumVisualsImmediate(result: BlurredWallpaperResult) {
        val albumCopy = try {
            result.systemAlbum.copy(
                result.systemAlbum.config ?: Bitmap.Config.ARGB_8888,
                false
            )
        } catch (_: Throwable) {
            null
        } ?: return
        lastSystemAlbumBitmap = result.systemAlbum
        lastWallpaperAlbumBitmap = result.systemAlbum
        MusicLockscreenManager.notifyWallpaperAppliedToLockScreen(albumCopy, result.trackKey)
    }

    private fun restoreWallpaperImmediately(context: Context) {
        try {
            val original = originalLockWallpaper ?: return
            val restoreEpoch = pipelineGate.withLock { pipeline.beginRestore() }
            val restoreId = restoreJobId(restoreEpoch)

            stopSessionWatch()
            isMusicWallpaperSet = false
            lastWallpaperAlbumBitmap = null
            lastWallpaperTrackKey = null
            lastSystemAlbumBitmap = null
            lastNetworkAlbumBitmap = null
            lastNetworkAlbumTrackKey = null
            networkAlbumGeneration++
            clearDualWallpaperCache()
            MusicLockscreenManager.updateBlurredBitmap(null)
            MusicLockscreenManager.setShowingState(false)
            MusicLockscreenManager.hideAlbumOverlay()
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.resetForMusicLockscreenOff()

            try {
                LockscreenNotificationController.releaseToSystemUi()
            } catch (e: Throwable) {
                logE("releaseToSystemUi error", e)
            }

            try {
                NumStateViewController.syncVisibility()
            } catch (e: Throwable) {
                logE("sync num_state_view error", e)
            }

            originalLockWallpaper = null
            LockWallpaperBackup.clear(context)
            ConfigReader.setWallpaperActive(context, false)

            logI("Original wallpaper restored")

            val restoreBmp = original
            val appCtx = context.applicationContext
            setMaskImage(restoreBmp)
            wallpaperApplier.enqueue(
                jobId = restoreId,
                write = {
                    pipelineGate.withLock {
                        if (!pipeline.shouldWriteRestore(restoreEpoch)) {
                            logI("restore setBitmap cancelled: superseded")
                            return@withLock false
                        }
                        try {
                            WallpaperManager.getInstance(appCtx)
                                .setBitmap(restoreBmp, null, true, WallpaperManager.FLAG_LOCK)
                            pipeline.markRestoreCommitted(restoreEpoch)
                            true
                        } catch (e: Throwable) {
                            logE("restore setBitmap error", e)
                            false
                        }
                    }
                },
                onCommitted = {
                    hideTransitionMask(MASK_SETTLE_EXIT_MS)
                },
                onCancelled = {
                    logI("restore apply cancelled epoch=$restoreEpoch")
                }
            )
        } catch (e: Throwable) {
            logE("restoreWallpaperImmediately error", e)
        }
    }

    fun isShowing(): Boolean = isMusicWallpaperSet

    fun isAnimating(): Boolean = isAnimating

    /** 当前已成功应用到锁屏壁纸的曲目 key（供 bind 重试判断是否已追上）。 */
    fun currentWallpaperTrackKey(): String? =
        appliedWallpaperTrackKey() ?: lastWallpaperTrackKey

    /** 读取活跃 MediaSession 的 metadata（AOD 下 bind 可能不来）。 */
    fun peekSessionMetadata(context: Context): android.media.MediaMetadata? = readMediaMetadata(context)

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
        // 用当前会话 metadata 刷新解析缓存，避免解锁 / AOD 期间切歌后仍用旧 trackKey
        try {
            val meta = readMediaMetadata(ctx) ?: readBestMetadata(ctx)
            AlbumArtResolver.refreshFromSessionMetadata(ctx, meta)
            AlbumArtResolver.resolve(
                ctx, null, meta, ignoreCache = false,
                AlbumArtResolver.getBindMediaData(),
                allowRemote = false
            )
        } catch (_: Throwable) {
        }
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        val bakeNow = ConfigReader.shouldBakeImmersiveAlbumInWallpaper(ctx)
        val appliedKey = appliedWallpaperTrackKey() ?: lastWallpaperTrackKey
        if (trackKey != null && trackKey == appliedKey &&
            !wallpaperLayoutStale && bakeNow == lastBakedImmersiveAlbum
        ) {
            ensureLyricFogReady()
            return false
        }
        if (trackKey != null && trackKey == appliedKey) {
            logI("refresh music wallpaper layout bake=$bakeNow")
            return rebuildWallpaperForLayout(ctx)
        }
        logI("refreshing music wallpaper with latest album art, track=$trackKey")
        if (!AlbumArtResolver.hasResolvedArt()) {
            MusicLockscreenManager.clearAlbumArt()
        }
        return updateMusicWallpaperSilently(ctx, null, null, ignoreCache = true)
    }

    /** 标记壁纸与当前曲目可能不一致，下次进锁屏强制刷新。 */
    fun markWallpaperStale() {
        pipelineGate.withLock { pipeline.markStale() }
        lastWallpaperTrackKey = null
        lastNetworkAlbumTrackKey = null
        networkAlbumGeneration++
        wallpaperLayoutStale = true
        clearDualWallpaperCache()
        logI("wallpaper marked stale")
    }

    /**
     * 沉浸专辑/歌词显隐切换：优先用切歌时并行缓存的另一布局，命中则即时换壁纸。
     */
    fun refreshWallpaperForAlbumVisibility(context: Context): Boolean {
        if (!isMusicWallpaperSet) return false
        val bakeNow = ConfigReader.shouldBakeImmersiveAlbumInWallpaper(context)
        // 烘焙态未变且已有对应壁纸：勿再 submitLayoutApply（会冲掉切歌 job / 反复 setBitmap）
        if (!wallpaperLayoutStale && bakeNow == lastBakedImmersiveAlbum) {
            logI("layout refresh skipped: bake unchanged=$bakeNow")
            return false
        }
        wallpaperLayoutStale = true
        logI("refresh wallpaper for immersive album visibility bake=$bakeNow")
        val appCtx = context.applicationContext
        if (bakeNow) {
            // 大专辑（封面可见）→沉浸：hold 方形封面盖住切换
            // 大专辑+沉浸歌词（封面已隐藏）→沉浸：保持隐藏，保留全屏模糊底，只换壁纸
            val squareVisible = MusicLockscreenManager.isSquareAlbumOverlayVisible() ||
                MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled
            MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled = squareVisible
            if (squareVisible) {
                MusicLockscreenManager.showAlbumOverlay()
            }
        } else {
            MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled = false
            MusicLockscreenManager.showAlbumOverlay()
        }
        val onLayoutSettled: () -> Unit = {
            mainHandler.post {
                MusicLockscreenManager.finishLayoutSwitchOverlay(bakeNow)
            }
        }
        // 双布局缓存命中：直接换，不再重算模糊
        val cached = takeDualCachedWallpaper(appCtx, bakeNow)
        if (cached != null) {
            logI("layout switch from dual cache bake=$bakeNow track=${cached.trackKey}")
            mainHandler.post {
                MusicLockscreenManager.updateBlurredBitmap(cached.wallpaper)
                if (!bakeNow) {
                    MusicLockscreenManager.updateAlbumBitmap(cached.systemAlbum)
                    MusicLockscreenManager.showAlbumOverlay()
                }
            }
            val layoutJobId = pipelineGate.withLock {
                pipeline.submitLayoutApply(cached.trackKey).job?.jobId
            } ?: return false
            applyLockBitmapAsync(
                appCtx,
                cached,
                jobId = layoutJobId,
                maskBuilder = null,
                onSettled = onLayoutSettled,
                notifyLyricOnSettle = false,
                settleDelayMs = LAYOUT_OVERLAY_SETTLE_MS
            )
            wallpaperLayoutStale = false
            return true
        }
        Thread {
            rebuildWallpaperForLayout(appCtx)
        }.start()
        return true
    }

    /** 曲目不变，仅切换壁纸布局（是否合成沉浸封面）。可在后台线程调用。 */
    private fun rebuildWallpaperForLayout(context: Context): Boolean {
        if (!isMusicWallpaperSet) return false
        if (!HookUtils.canApplyLockWallpaper(context)) {
            logI("layout rebuild skipped: screen off or not on keyguard")
            wallpaperLayoutStale = true
            return false
        }
        try {
            val bakeTarget = ConfigReader.shouldBakeImmersiveAlbumInWallpaper(context)
            if (bakeTarget) {
                mainHandler.post {
                    val squareVisible = MusicLockscreenManager.isSquareAlbumOverlayVisible() ||
                        MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled
                    MusicLockscreenManager.holdSquareAlbumUntilWallpaperSettled = squareVisible
                    if (squareVisible) {
                        MusicLockscreenManager.showAlbumOverlay()
                    }
                }
            }
            val wallpaperResult = buildBlurredBitmap(context, null, null, ignoreCache = false)
                ?: run {
                    logE("layout rebuild: build failed")
                    mainHandler.post {
                        MusicLockscreenManager.finishLayoutSwitchOverlay(bakeTarget)
                    }
                    return false
                }
            mainHandler.post {
                MusicLockscreenManager.updateBlurredBitmap(wallpaperResult.wallpaper)
                if (!bakeTarget) {
                    MusicLockscreenManager.showAlbumOverlay()
                }
            }
            val layoutJobId = pipelineGate.withLock {
                pipeline.submitLayoutApply(wallpaperResult.trackKey).job?.jobId
            } ?: return false
            applyLockBitmapAsync(
                context,
                wallpaperResult,
                jobId = layoutJobId,
                maskBuilder = null,
                onSettled = {
                    mainHandler.post {
                        MusicLockscreenManager.finishLayoutSwitchOverlay(bakeTarget)
                    }
                },
                notifyLyricOnSettle = false,
                settleDelayMs = LAYOUT_OVERLAY_SETTLE_MS
            )
            logI("layout rebuild ok bake=$bakeTarget")
            return true
        } catch (e: Throwable) {
            logE("rebuildWallpaperForLayout error", e)
            return false
        }
    }

    /** 若歌词雾状背景缺失，用最近成功应用的专辑补渲染。 */
    fun ensureLyricFogReady() {
        val lyric = MusicLockscreenManager.lyricView as? LockscreenLyricView ?: return
        if (lyric.isFogBackgroundReady()) return
        val album = lastSystemAlbumBitmap?.takeIf { !it.isRecycled }
            ?: lastWallpaperAlbumBitmap?.takeIf { !it.isRecycled }
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
        bindMediaMetadataCallback(context.applicationContext)
        startMediaStateObserver(context.applicationContext)

        val sessionRunnable = object : Runnable {
            override fun run() {
                if (!isMusicWallpaperSet) return
                val ctx = sessionWatchContext ?: return
                if (!hasActiveMediaSession(ctx)) {
                    logI("no active media session, exiting music lockscreen")
                    restoreOriginalWallpaper(ctx)
                    return
                }
                if (HookUtils.isOnKeyguard(ctx)) {
                    // 会话可能切换，定期重绑 Callback
                    rebindMediaMetadataCallbackIfNeeded(ctx)
                }
                sessionPollHandler.postDelayed(this, SESSION_POLL_INTERVAL_MS)
            }
        }
        sessionPollRunnable = sessionRunnable
        sessionPollHandler.postDelayed(sessionRunnable, SESSION_POLL_INTERVAL_MS)

        val trackRunnable = object : Runnable {
            override fun run() {
                if (!isMusicWallpaperSet) return
                val ctx = sessionWatchContext ?: return
                try {
                    pollTrackChangeAndRefresh(ctx)
                } catch (e: Throwable) {
                    logE("track poll error", e)
                }
                sessionPollHandler.postDelayed(this, TRACK_POLL_INTERVAL_MS)
            }
        }
        trackPollRunnable = trackRunnable
        sessionPollHandler.postDelayed(trackRunnable, TRACK_POLL_INTERVAL_MS)
    }

    private fun bindMediaMetadataCallback(context: Context) {
        unbindMediaMetadataCallback()
        val controller = preferredMediaController(context) ?: return
        trackedMediaController = controller
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                val ctx = sessionWatchContext ?: return
                sessionPollHandler.post {
                    try {
                        if (!isMusicWallpaperSet) return@post
                        onSessionMetadataChanged(ctx, metadata)
                    } catch (e: Throwable) {
                        logE("onMetadataChanged refresh error", e)
                    }
                }
            }
        }
        mediaMetadataCallback = cb
        try {
            controller.registerCallback(cb, sessionPollHandler)
            logI("media metadata callback registered pkg=${controller.packageName}")
            // 勿立刻 onSessionMetadataChanged：首次开启时 applied/pending 尚未对齐，
            // 会误判 lagging 并启动静默更新顶掉正在写入的首次壁纸。
        } catch (e: Throwable) {
            logE("register media callback failed", e)
            mediaMetadataCallback = null
            trackedMediaController = null
        }
    }

    private fun rebindMediaMetadataCallbackIfNeeded(context: Context) {
        val current = preferredMediaController(context) ?: return
        val tracked = trackedMediaController
        if (tracked != null && tracked === current) return
        if (tracked != null && tracked.packageName == current.packageName &&
            tracked.sessionToken == current.sessionToken
        ) {
            return
        }
        logI("media session changed, rebind callback")
        bindMediaMetadataCallback(context)
    }

    private fun unbindMediaMetadataCallback() {
        val ctrl = trackedMediaController
        val cb = mediaMetadataCallback
        if (ctrl != null && cb != null) {
            try {
                ctrl.unregisterCallback(cb)
            } catch (_: Throwable) {
            }
        }
        trackedMediaController = null
        mediaMetadataCallback = null
    }

    private fun preferredMediaController(context: Context): MediaController? {
        return try {
            val controllers = com.leowalk.musiclockscreen.MediaSessionAccess.getActiveControllers(context)
            controllers.firstOrNull { controller ->
                ConfigReader.isAllowedMusicApp(context, controller.packageName)
            } ?: controllers.firstOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private fun onSessionMetadataChanged(context: Context, metadata: MediaMetadata?) {
        if (!isMusicWallpaperSet) return
        if (!HookUtils.isOnKeyguard(context)) {
            markWallpaperStale()
            return
        }
        if (!HookUtils.isAllowedMusicApp(context)) return
        val trackChanged = AlbumArtResolver.refreshFromSessionMetadata(context, metadata)
        val cachedKey = AlbumArtResolver.getCachedTrackKey()
        val lagging = cachedKey != null && !isTrackWallpaperInFlight(cachedKey)
        if (!trackChanged && !lagging && !wallpaperLayoutStale) return
        logI(
            "session metadata refresh: changed=$trackChanged lagging=$lagging " +
                "stale=$wallpaperLayoutStale key=$cachedKey applied=${appliedWallpaperTrackKey()} " +
                "phase=${pipelineGate.withLock { pipeline.activeJob()?.phase }}"
        )
        val cachedArt = AlbumArtResolver.getCached()
        if (cachedArt != null) {
            MusicLockscreenManager.updateAlbumBitmap(cachedArt)
        } else if (trackChanged) {
            MusicLockscreenManager.clearAlbumArt()
        }
        // 只有真正切歌才清歌词；纯 lagging 补壁纸不得打断歌词
        if (trackChanged) {
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onTrackMayHaveChanged()
        }
        if (!HookUtils.canApplyLockWallpaper(context)) {
            markWallpaperStale()
            return
        }
        updateMusicWallpaperSilently(context, null, metadata, ignoreCache = true)
    }

    /**
     * 锁屏 / AOD 下用 MediaSession 检测切歌并刷新专辑与模糊壁纸（Callback 的兜底）。
     */
    private fun pollTrackChangeAndRefresh(context: Context) {
        if (!HookUtils.isOnKeyguard(context)) return
        if (!HookUtils.isAllowedMusicApp(context)) return
        val meta = readMediaMetadata(context) ?: return
        val trackChanged = AlbumArtResolver.refreshFromSessionMetadata(context, meta)
        val cachedKey = AlbumArtResolver.getCachedTrackKey()
        val wallpaperLagging = cachedKey != null && !isTrackWallpaperInFlight(cachedKey)
        if (!trackChanged && !wallpaperLagging && !wallpaperLayoutStale) return
        if (!HookUtils.canApplyLockWallpaper(context)) {
            markWallpaperStale()
            if (trackChanged) {
                (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onTrackMayHaveChanged()
            }
            return
        }
        logI(
            "track poll refresh: changed=$trackChanged lagging=$wallpaperLagging " +
                "stale=$wallpaperLayoutStale key=$cachedKey applied=${appliedWallpaperTrackKey()} " +
                "phase=${pipelineGate.withLock { pipeline.activeJob()?.phase }}"
        )
        val cachedArt = AlbumArtResolver.getCached()
        if (cachedArt != null) {
            MusicLockscreenManager.updateAlbumBitmap(cachedArt)
        } else if (trackChanged) {
            MusicLockscreenManager.clearAlbumArt()
        }
        if (trackChanged) {
            (MusicLockscreenManager.lyricView as? LockscreenLyricView)?.onTrackMayHaveChanged()
        }
        updateMusicWallpaperSilently(context, null, meta, ignoreCache = true)
    }

    private fun stopSessionWatch() {
        unbindMediaMetadataCallback()
        stopMediaStateObserver()
        sessionPollRunnable?.let { sessionPollHandler.removeCallbacks(it) }
        sessionPollRunnable = null
        trackPollRunnable?.let { sessionPollHandler.removeCallbacks(it) }
        trackPollRunnable = null
        sessionWatchContext = null
    }

    private fun startMediaStateObserver(context: Context) {
        stopMediaStateObserver()
        val uri = Uri.parse("content://com.leowalk.musiclockscreen.config/config")
        val observer = object : ContentObserver(sessionPollHandler) {
            override fun onChange(selfChange: Boolean) {
                if (!isMusicWallpaperSet) return
                val ctx = sessionWatchContext ?: return
                try {
                    ConfigReader.invalidate()
                    if (ConfigReader.mediaListenerReady(ctx) &&
                        !ConfigReader.mediaPlaybackActive(ctx)
                    ) {
                        logI("listener: media inactive, exiting music lockscreen")
                        restoreOriginalWallpaper(ctx)
                    }
                } catch (e: Throwable) {
                    logE("mediaStateObserver error", e)
                }
            }
        }
        mediaStateObserver = observer
        try {
            context.contentResolver.registerContentObserver(uri, true, observer)
        } catch (e: Throwable) {
            logE("register mediaStateObserver failed", e)
            mediaStateObserver = null
        }
    }

    private fun stopMediaStateObserver() {
        val obs = mediaStateObserver ?: return
        mediaStateObserver = null
        try {
            sessionWatchContext?.contentResolver?.unregisterContentObserver(obs)
        } catch (_: Throwable) {
        }
    }

    private fun hasActiveMediaSession(context: Context): Boolean {
        return try {
            // 通知使用权 Listener 已连接时，以其上报为准（杀 App 后更及时）
            if (ConfigReader.mediaListenerReady(context)) {
                return ConfigReader.mediaPlaybackActive(context)
            }
            val controllers = com.leowalk.musiclockscreen.MediaSessionAccess.getActiveControllers(context)
            controllers.any { controller ->
                if (!ConfigReader.isAllowedMusicApp(context, controller.packageName)) {
                    return@any false
                }
                val state = controller.playbackState?.state ?: return@any true
                state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_PAUSED ||
                    state == PlaybackState.STATE_BUFFERING ||
                    state == PlaybackState.STATE_CONNECTING
            }
        } catch (e: Throwable) {
            logE("hasActiveMediaSession error", e)
            // 读失败不要硬撑着不退出：保守返回 false 以便恢复原壁纸
            false
        }
    }

    private fun readMediaMetadata(context: Context): android.media.MediaMetadata? {
        return try {
            preferredMediaController(context)?.metadata
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        android.util.Log.i(tag, msg)
        logCallback?.invoke(android.util.Log.INFO, tag, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e(tag, msg, e)
        logCallback?.invoke(android.util.Log.ERROR, tag, msg, e)
    }
}
