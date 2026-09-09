package com.leowalk.musiclockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.leowalk.musiclockscreen.xposed.AlbumArtResolver
import com.leowalk.musiclockscreen.xposed.BlurUtils
import com.leowalk.musiclockscreen.xposed.HyperOsSoftGlass
import com.leowalk.musiclockscreen.xposed.MagazinePageAlbumSlotPolicy
import com.leowalk.musiclockscreen.xposed.MagazinePageArtRefreshPolicy
import com.leowalk.musiclockscreen.xposed.MagazinePageChromePolicy
import com.leowalk.musiclockscreen.xposed.MagazinePageChromeView
import com.leowalk.musiclockscreen.xposed.MagazinePageLyricVisualPolicy
import com.leowalk.musiclockscreen.xposed.MagazinePageSleepPolicy
import com.leowalk.musiclockscreen.xposed.MagazinePageSoftGlassPolicy
import com.leowalk.musiclockscreen.xposed.MagazinePageWallpaper
import com.leowalk.musiclockscreen.xposed.ModeSwitchPolicy
import com.leowalk.musiclockscreen.xposed.NetworkAlbumArtFetcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 画报左滑/右划入口自建页：壁纸烘焙 + 完整锁屏歌词（含切行/沉浸栈动画）。
 * 灭屏/解锁结束页面，交回系统锁屏 AOD（同官方 LeftLKS）。
 */
class MagazineMusicActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bakeExecutor = Executors.newSingleThreadExecutor()
    private val bakeGeneration = AtomicInteger(0)
    private val enhanceInFlight = AtomicBoolean(false)

    private var wallpaperView: ImageView? = null
    /** 与壁纸同级、压在底栏下：HyperChanger 快捷方式同款柔光玻璃采样层。 */
    private var softGlassView: ImageView? = null
    private var softGlassRetryGen = 0
    private var chromeView: MagazinePageChromeView? = null
    private var lyricView: com.leowalk.musiclockscreen.xposed.LockscreenLyricView? = null
    private val softGlassRetryDelaysMs = longArrayOf(0L, 16L, 48L, 120L, 280L, 600L, 1200L)
    private var lastArtKey: String = ""
    private var hdAppliedForKey: String? = null
    private var displayedWallpaper: Bitmap? = null
    private var mediaCallback: MediaController.Callback? = null
    private var boundController: MediaController? = null
    private var sleepReceiverRegistered = false
    private var modeSwitchReceiverRegistered = false
    /** 沉浸歌词占大专辑槽：壁纸不叠前景方形封面。 */
    private var hideBigAlbumForLyric = false

    private val sleepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!MagazinePageSleepPolicy.shouldFinishOnBroadcast(intent?.action)) return
            finishMagazinePage(notifySystemUiSleep = true)
        }
    }

    private val modeSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ModeSwitchPolicy.ACTION_EXIT_ACTIVE_PRESENTATION) return
            finishMagazinePage(notifySystemUiSleep = false)
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshAlbumArt()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 无可用音乐会话：立刻结束，不铺黑窗
        if (!hasUsableMusicSession()) {
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
            return
        }
        // 预热 MiSans，避免首帧掉回默认字体
        try {
            com.leowalk.musiclockscreen.xposed.MiSansTypefaces.bold()
            com.leowalk.musiclockscreen.xposed.MiSansTypefaces.medium()
        } catch (_: Throwable) {
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        applyFullscreenWindow()
        setContentView(buildUi())
        // 预热 SystemUI ClassLoader，便于底栏套真·MiGlassCompat
        try {
            HyperOsSoftGlass.systemUiClassLoader(this)
        } catch (_: Throwable) {
        }
        registerSleepReceiver()
        registerModeSwitchReceiver()
        refreshAlbumArt()
    }

    private fun finishMagazinePage(notifySystemUiSleep: Boolean) {
        if (isFinishing || isDestroyed) return
        if (notifySystemUiSleep) {
            // 先通知 SystemUI 清 occlude，再关页，避免 doze 时仍 occluded 杀掉 FullAOD
            try {
                sendBroadcast(
                    Intent(MagazinePageSleepPolicy.ACTION_MAGAZINE_PAGE_GOING_TO_SLEEP)
                        .setPackage("com.android.systemui"),
                )
            } catch (_: Throwable) {
            }
        }
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    private fun hasUsableMusicSession(): Boolean {
        return try {
            try {
                ModuleConfig.init(this)
            } catch (_: Throwable) {
            }
            MediaSessionAccess.getActiveControllers(this).any { controller ->
                ModuleConfig.isPackageAllowed(controller.packageName) &&
                    com.leowalk.musiclockscreen.xposed.MagazineModePolicy.isUsableMusicPlaybackState(
                        controller.playbackState?.state,
                    )
            }
        } catch (_: Throwable) {
            false
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreenWindow()
            chromeView?.requestStableMiBlur()
            lyricView?.requestMagazineMiBlurRefresh()
            scheduleMagazineSoftGlass()
        }
    }

    private fun applyFullscreenWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        // 画报独立「常亮」档案
        try {
            ModuleConfig.init(this)
        } catch (_: Throwable) {
        }
        if (ModuleConfig.magazineKeepLockScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.decorView.keepScreenOn = true
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.decorView.keepScreenOn = false
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applyMiuiTransparentSystemBars()

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // 画报页显示状态栏（时钟/电量），导航栏照常保留
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.show(WindowInsetsCompat.Type.navigationBars())
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    private fun applyMiuiTransparentSystemBars() {
        try {
            val layoutParamsClass = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            val statusTransparent = layoutParamsClass.getField("EXTRA_FLAG_STATUS_BAR_TRANSPARENT").getInt(null)
            val navTransparent = layoutParamsClass.getField("EXTRA_FLAG_NAVIGATION_BAR_TRANSPARENT").getInt(null)
            val setExtraFlags = window.javaClass.getMethod(
                "setExtraFlags",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            val flags = statusTransparent or navTransparent
            setExtraFlags.invoke(window, flags, flags)
        } catch (_: Throwable) {
        }
    }

    private fun registerSleepReceiver() {
        if (sleepReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(sleepReceiver, filter, RECEIVER_NOT_EXPORTED)
        sleepReceiverRegistered = true
    }

    private fun unregisterSleepReceiver() {
        if (!sleepReceiverRegistered) return
        try {
            unregisterReceiver(sleepReceiver)
        } catch (_: Throwable) {
        }
        sleepReceiverRegistered = false
    }

    private fun registerModeSwitchReceiver() {
        if (modeSwitchReceiverRegistered) return
        val filter = IntentFilter(ModeSwitchPolicy.ACTION_EXIT_ACTIVE_PRESENTATION)
        registerReceiver(modeSwitchReceiver, filter, RECEIVER_NOT_EXPORTED)
        modeSwitchReceiverRegistered = true
    }

    private fun unregisterModeSwitchReceiver() {
        if (!modeSwitchReceiverRegistered) return
        try {
            unregisterReceiver(modeSwitchReceiver)
        } catch (_: Throwable) {
        }
        modeSwitchReceiverRegistered = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lastArtKey = ""
        hdAppliedForKey = null
        enhanceInFlight.set(false)
        refreshAlbumArt()
    }

    override fun onResume() {
        super.onResume()
        applyFullscreenWindow()
        lyricView?.let {
            it.ensureLyricsLoaded()
        }
        scheduleMagazineSoftGlass()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshRunnable)
        unbindMediaCallback()
        super.onPause()
    }

    override fun onDestroy() {
        softGlassRetryGen++
        unregisterSleepReceiver()
        unregisterModeSwitchReceiver()
        chromeView?.setCallbacks(null)
        softGlassView?.let { HyperOsSoftGlass.clear(it) }
        softGlassView = null
        lyricView?.setMagazineChromeStyleSync(null)
        lyricView?.setMagazineSharedTintListener(null)
        lyricView?.clearMagazineWallpaperForTint()
        lyricView = null
        chromeView = null
        wallpaperView = null
        bakeExecutor.shutdownNow()
        unbindMediaCallback()
        displayedWallpaper?.takeIf { !it.isRecycled }?.recycle()
        displayedWallpaper = null
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply {
            // 透明底，便于 MiBlur 采样下层壁纸 ImageView
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(
            wallpaperView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val density = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val bottomOffset = MagazinePageChromePolicy.chromeBottomOffsetPx(screenH)
        val insetH = (MagazinePageSoftGlassPolicy.INSET_HORIZONTAL_DP * density).toInt()

        // 整块宿主：玻璃与内容同高 wrap，悬浮于距底 1/5（不贴底）
        val chromeHost = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        // 壁纸 → 柔光玻璃 → 内容（玻璃与 chrome 同框，按钮不会超出背景）
        val glass = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val r = MagazinePageSoftGlassPolicy.cornerRadiusPx(density)
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            setImageDrawable(
                GradientDrawable().apply {
                    setColor(Color.argb(1, 255, 255, 255))
                    cornerRadius = MagazinePageSoftGlassPolicy.cornerRadiusPx(density)
                },
            )
        }
        softGlassView = glass
        chromeHost.addView(
            glass,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val chrome = MagazinePageChromeView(this)
        chromeView = chrome
        chrome.setCallbacks(object : MagazinePageChromeView.Callbacks {
            override fun onSkipPrevious() {
                transport()?.skipToPrevious()
            }

            override fun onPlayPause() {
                val tc = transport() ?: return
                val playing = MagazinePageChromePolicy.isPlaying(
                    boundController?.playbackState?.state,
                )
                if (playing) tc.pause() else tc.play()
            }

            override fun onSkipNext() {
                transport()?.skipToNext()
            }

            override fun onToggleLyric() {
                toggleMagazineLyric()
            }

            override fun onExitMagazine() {
                if (isFinishing || isDestroyed) return
                finishAndRemoveTask()
                overridePendingTransition(0, 0)
            }
        })
        chromeHost.addView(
            chrome,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            chromeHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = bottomOffset
                leftMargin = insetH
                rightMargin = insetH
            },
        )

        val lyric = com.leowalk.musiclockscreen.xposed.LockscreenLyricView(this).apply {
            enableMagazinePageHost()
            setMagazineChromeStyleSync {
                chromeView?.syncStyleWithLyric()
            }
            setMagazineSharedTintListener { contrast, accent ->
                // 歌词侧 recovery / 独立取色：底栏跟同一对 contrast+accent
                chromeView?.setAlbumTint(accent)
                chromeView?.setContrastBackground(contrast)
                chromeView?.syncStyleWithLyric()
            }
            setMagazineAlbumSlotListener { hideAlbum ->
                onMagazineLyricAlbumSlotChanged(hideAlbum)
            }
        }
        lyricView = lyric
        root.addView(
            lyric,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },
        )
        root.post { scheduleMagazineSoftGlass() }
        return root
    }

    /** HyperChanger 同款两段柔光玻璃；挂在壁纸与底栏之间才能采到壁纸。 */
    private fun scheduleMagazineSoftGlass() {
        if (!MagazinePageSoftGlassPolicy.enabled()) return
        val glass = softGlassView ?: return
        val density = resources.displayMetrics.density
        val gen = ++softGlassRetryGen
        for (i in softGlassRetryDelaysMs.indices) {
            val delay = softGlassRetryDelaysMs[i]
            mainHandler.postDelayed({
                if (gen != softGlassRetryGen || isFinishing || isDestroyed) return@postDelayed
                if (glass.width <= 0 || glass.height <= 0) return@postDelayed
                val result = HyperOsSoftGlass.applyToImageView(
                    glass,
                    HyperOsSoftGlass.Spec(
                        opacityPercent = MagazinePageSoftGlassPolicy.OPACITY_PERCENT,
                        backdropBlurRadius = MagazinePageSoftGlassPolicy.BACKDROP_BLUR_RADIUS,
                        glassBlurRadius = MagazinePageSoftGlassPolicy.GLASS_BLUR_RADIUS,
                        luminance = MagazinePageSoftGlassPolicy.LUMINANCE,
                        tintColor = MagazinePageSoftGlassPolicy.TINT_COLOR,
                        sampleSiblingContent = true,
                        cornerRadiusPx = MagazinePageSoftGlassPolicy.cornerRadiusPx(density),
                    ),
                )
                glass.invalidateOutline()
                if (result.success && i == 0) {
                    // 合成器晚到再补一次
                    mainHandler.postDelayed({
                        if (gen == softGlassRetryGen && !isFinishing && !isDestroyed) {
                            HyperOsSoftGlass.applyToImageView(
                                glass,
                                HyperOsSoftGlass.Spec(
                                    opacityPercent = MagazinePageSoftGlassPolicy.OPACITY_PERCENT,
                                    backdropBlurRadius = MagazinePageSoftGlassPolicy.BACKDROP_BLUR_RADIUS,
                                    glassBlurRadius = MagazinePageSoftGlassPolicy.GLASS_BLUR_RADIUS,
                                    luminance = MagazinePageSoftGlassPolicy.LUMINANCE,
                                    tintColor = MagazinePageSoftGlassPolicy.TINT_COLOR,
                                    sampleSiblingContent = true,
                                    cornerRadiusPx = MagazinePageSoftGlassPolicy.cornerRadiusPx(density),
                                ),
                            )
                        }
                    }, 90L)
                }
            }, delay)
        }
    }

    private fun transport(): MediaController.TransportControls? =
        boundController?.transportControls
            ?: MediaSessionAccess.getActiveControllers(this).firstOrNull()?.transportControls

    private fun toggleMagazineLyric() {
        try {
            ModuleConfig.init(this)
        } catch (_: Throwable) {
        }
        if (!ModuleConfig.lyricEnabled) return
        val next = MagazinePageChromePolicy.nextShowLyric(ModuleConfig.magazineShowLyric)
        ModuleConfig.magazineShowLyric = next
        lyricView?.onShowLyricToggled(next)
        chromeView?.setLyricVisible(next, featureEnabled = true)
        // 开关歌词会改变占槽态，强制重烘焙壁纸
        forceRebakeWallpaperForSlot()
    }

    /** 歌词↔大专辑槽位变化：仅沉浸歌词占槽时对标锁屏 hideSquareAlbum。 */
    private fun onMagazineLyricAlbumSlotChanged(hideAlbum: Boolean) {
        if (hideBigAlbumForLyric == hideAlbum) return
        hideBigAlbumForLyric = hideAlbum
        forceRebakeWallpaperForSlot()
    }

    private fun forceRebakeWallpaperForSlot() {
        lastArtKey = ""
        val ctrl = boundController ?: return
        val meta = ctrl.metadata ?: return
        maybeBakeWallpaper(ctrl, meta)
    }

    private fun syncChromeFromController(ctrl: MediaController, meta: MediaMetadata? = ctrl.metadata) {
        try {
            ModuleConfig.init(this)
        } catch (_: Throwable) {
        }
        chromeView?.setTrackInfo(
            resolveTrackTitle(meta),
            meta?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            ModuleConfig.magazineTitleBracketMode,
        )
        chromeView?.setAlbumArt(
            meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        )
        chromeView?.setPlaying(MagazinePageChromePolicy.isPlaying(ctrl.playbackState?.state))
        chromeView?.setLyricVisible(
            ModuleConfig.magazineShowLyric,
            featureEnabled = ModuleConfig.lyricEnabled,
        )
        applyQuickAlbumTint(meta)
    }

    private fun resolveTrackTitle(meta: MediaMetadata?): String? {
        if (meta == null) return null
        return meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Session 封面立刻取色，不等壁纸烘焙完成。 */
    private fun applyQuickAlbumTint(meta: MediaMetadata?) {
        if (meta == null) return
        val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return
        if (bmp.isRecycled) return
        val sample = copyBitmap(bmp) ?: return
        bakeExecutor.execute {
            val pair = try {
                BlurUtils.extractLowerHalfTintColors(sample)
            } catch (_: Throwable) {
                null
            } finally {
                recycleQuietly(sample)
            }
            if (pair == null) return@execute
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                applySharedMagazineTint(pair.contrast, pair.accent)
            }
        }
    }

    /** 底栏 + 歌词共用 contrast（深浅）与 accent（染色）。 */
    private fun applySharedMagazineTint(contrast: Int, accent: Int) {
        chromeView?.setAlbumTint(accent)
        chromeView?.setContrastBackground(contrast)
        lyricView?.applyMagazineAlbumTint(accent, contrast)
        chromeView?.syncStyleWithLyric()
    }

    private fun refreshAlbumArt() {
        val ctrl = MediaSessionAccess.getActiveControllers(this).firstOrNull() ?: return
        bindMediaCallback(ctrl)
        val meta = ctrl.metadata
        syncChromeFromController(ctrl, meta)
        if (meta != null) maybeBakeWallpaper(ctrl, meta)
    }

    private fun bindMediaCallback(ctrl: MediaController) {
        if (boundController === ctrl) return
        unbindMediaCallback()
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                syncChromeFromController(ctrl, metadata)
                if (metadata == null) return
                val trackChanged = try {
                    AlbumArtResolver.refreshFromSessionMetadata(this@MagazineMusicActivity, metadata)
                } catch (_: Throwable) {
                    true
                }
                maybeBakeWallpaper(ctrl, metadata)
                if (trackChanged) {
                    lyricView?.onTrackMayHaveChanged()
                }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                chromeView?.setPlaying(MagazinePageChromePolicy.isPlaying(state?.state))
                // 暂停让出专辑槽 / 播放占槽：刷新歌词优先级并可能重烘焙
                lyricView?.notifyMagazinePlaybackMayAffectAlbumSlot()
            }
        }
        try {
            ctrl.registerCallback(cb, mainHandler)
            mediaCallback = cb
            boundController = ctrl
            syncChromeFromController(ctrl)
        } catch (_: Throwable) {
            mediaCallback = null
            boundController = null
        }
    }

    private fun unbindMediaCallback() {
        val ctrl = boundController
        val cb = mediaCallback
        if (ctrl != null && cb != null) {
            try {
                ctrl.unregisterCallback(cb)
            } catch (_: Throwable) {
            }
        }
        boundController = null
        mediaCallback = null
    }

    private fun maybeBakeWallpaper(ctrl: MediaController, meta: MediaMetadata) {
        val bmp: Bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return
        if (bmp.isRecycled) return

        val style = ModuleConfig.magazinePageChrome
        val wantHd = ModuleConfig.magazineAlbumNetworkHd
        val packageName = ctrl.packageName
        val trackKey = AlbumArtResolver.computeTrackKeyForPackage(this, meta, packageName)
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val showBigAlbum = MagazinePageAlbumSlotPolicy.shouldBakeBigAlbumForeground(
            pageStyle = style,
            immersiveLyric = ModuleConfig.magazineImmersiveLyric,
            hideAlbumForLyricPriority = hideBigAlbumForLyric,
        )
        val bakeFp = listOf(
            ModuleConfig.magazineBlurRadius,
            ModuleConfig.magazineDarkOverlay,
            ModuleConfig.magazineAlbumSize,
            ModuleConfig.magazineAlbumAnchorY,
            ModuleConfig.magazineAlbumCorner,
            ModuleConfig.magazineImmersiveAlbumCenterY,
            ModuleConfig.magazineImmersiveAlbumEdgeGradient,
            ModuleConfig.magazineImmersiveLyric,
            if (showBigAlbum) 1 else 0,
        ).joinToString(",")
        val stableKey = MagazinePageArtRefreshPolicy.buildStableArtKey(
            trackKey = trackKey,
            packageName = packageName,
            title = title,
            artist = artist,
            artWidth = bmp.width,
            artHeight = bmp.height,
            style = style,
            wantHd = wantHd,
            bakeFingerprint = bakeFp,
        )

        val hdDone = hdAppliedForKey == stableKey
        if (!MagazinePageArtRefreshPolicy.shouldStartBake(
                stableArtKey = stableKey,
                lastArtKey = lastArtKey,
                wantHd = wantHd,
                hdAppliedForKey = hdDone,
                enhanceInFlight = enhanceInFlight.get(),
            )
        ) {
            return
        }

        lastArtKey = stableKey
        if (hdAppliedForKey != null && hdAppliedForKey != stableKey) {
            hdAppliedForKey = null
        }

        val params = currentBakeParams(style)
        val gen = bakeGeneration.incrementAndGet()
        val sessionCopy = copyBitmap(bmp) ?: return

        bakeExecutor.execute {
            val quick = renderWallpaper(sessionCopy, sessionCopy, params)
            if (quick != null) {
                postWallpaper(gen, quick)
            }

            val needHd = MagazinePageArtRefreshPolicy.shouldFetchHd(
                wantHd = wantHd,
                referenceMinSide = minOf(sessionCopy.width, sessionCopy.height),
                hdAppliedForKey = hdAppliedForKey == stableKey,
                enhanceInFlight = false,
            )
            if (!needHd) {
                recycleQuietly(sessionCopy)
                return@execute
            }
            if (!enhanceInFlight.compareAndSet(false, true)) {
                recycleQuietly(sessionCopy)
                return@execute
            }
            try {
                if (gen != bakeGeneration.get()) return@execute
                val hd = try {
                    NetworkAlbumArtFetcher.fetchVerifiedHighRes(
                        context = applicationContext,
                        reference = sessionCopy,
                        metadata = meta,
                        packageName = packageName,
                        trackKey = trackKey,
                    )
                } catch (_: Throwable) {
                    null
                }
                if (hd == null || hd.isRecycled) return@execute
                if (gen != bakeGeneration.get()) {
                    recycleQuietly(hd)
                    return@execute
                }
                val sharp = copyBitmap(hd) ?: hd
                if (sharp !== hd) recycleQuietly(hd)
                val enhanced = renderWallpaper(sessionCopy, sharp, params)
                if (sharp !== enhanced) recycleQuietly(sharp)
                if (enhanced == null) return@execute
                hdAppliedForKey = stableKey
                postWallpaper(gen, enhanced)
            } finally {
                recycleQuietly(sessionCopy)
                enhanceInFlight.set(false)
            }
        }
    }

    private fun currentBakeParams(style: String): MagazinePageWallpaper.Params {
        val dm = resources.displayMetrics
        val showBigAlbum = MagazinePageAlbumSlotPolicy.shouldBakeBigAlbumForeground(
            pageStyle = style,
            immersiveLyric = ModuleConfig.magazineImmersiveLyric,
            hideAlbumForLyricPriority = hideBigAlbumForLyric,
        )
        return MagazinePageWallpaper.Params(
            pageStyle = style,
            blurRadius = ModuleConfig.magazineBlurRadius,
            darkOverlayAlpha = ModuleConfig.magazineDarkOverlay,
            albumSizePercent = ModuleConfig.magazineAlbumSize,
            albumAnchorYPercent = ModuleConfig.magazineAlbumAnchorY,
            albumCornerDp = ModuleConfig.magazineAlbumCorner,
            immersiveCenterYPercent = ModuleConfig.magazineImmersiveAlbumCenterY,
            edgeGradientEnabled = ModuleConfig.magazineImmersiveAlbumEdgeGradient,
            width = dm.widthPixels.coerceAtLeast(1),
            height = dm.heightPixels.coerceAtLeast(1),
            showBigAlbum = showBigAlbum,
        )
    }

    private fun renderWallpaper(
        blurSource: Bitmap,
        sharpAlbum: Bitmap,
        params: MagazinePageWallpaper.Params,
    ): Bitmap? {
        return try {
            MagazinePageWallpaper.render(blurSource, sharpAlbum, params)
        } catch (_: Throwable) {
            null
        }
    }

    private fun postWallpaper(gen: Int, out: Bitmap) {
        mainHandler.post {
            if (isFinishing || isDestroyed) {
                recycleQuietly(out)
                return@post
            }
            if (gen != bakeGeneration.get()) {
                recycleQuietly(out)
                return@post
            }
            val old = displayedWallpaper
            displayedWallpaper = out
            wallpaperView?.setImageBitmap(out)
            if (old != null && old !== out && !old.isRecycled) old.recycle()
            try {
                lyricView?.setMagazineWallpaperForTint(out)
                // 取色只走下方 bake 一路广播；勿再并行 onWallpaperAlbumReady，避免底栏/歌词竞态深浅不一致
                if (!MagazinePageLyricVisualPolicy.shouldDeferWallpaperTintExtractToActivity(true)) {
                    val tintSrc = copyBitmap(out)
                    lyricView?.onWallpaperAlbumReady(tintSrc, lastArtKey)
                }
                chromeView?.requestMiBlurRefresh()
            } catch (_: Throwable) {
            }
            bakeExecutor.execute {
                val sample = copyBitmap(out) ?: return@execute
                val pair = try {
                    BlurUtils.extractLowerHalfTintColors(sample)
                } catch (_: Throwable) {
                    null
                } finally {
                    recycleQuietly(sample)
                }
                if (pair == null) return@execute
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    if (gen != bakeGeneration.get()) return@post
                    applySharedMagazineTint(pair.contrast, pair.accent)
                }
            }
        }
    }

    private fun copyBitmap(src: Bitmap): Bitmap? =
        try {
            src.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            null
        }

    private fun recycleQuietly(bmp: Bitmap?) {
        try {
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        } catch (_: Throwable) {
        }
    }
}
