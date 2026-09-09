package com.leowalk.musiclockscreen.xposed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.*
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

/**
 * 锁屏歌词覆盖层（自绘 View 版）
 *
 * 特性：
 * - 双行显示：当前行（白色加粗）+ 下一行（淡灰色小字）
 * - 歌词/翻译互换：有翻译时主行显示翻译，副行显示原文（固定开启）
 * - 主行超长自动换行显示完整内容
 * - 垂直位置：底边 = 屏高 × [lyricBgAnchorY]%，由 [MediaFollowController] 维护
 * - 渐变遮罩背景：取专辑下半主色调，生成自下而上的半透明黑色渐变
 */
class LockscreenLyricView(context: Context) : View(context) {

    /** 画报右划页宿主：绕过 SystemUI 壁纸/通知中心门闩，自钉歌词锚点。 */
    @Volatile
    private var magazinePageHost: Boolean = false

    fun enableMagazinePageHost() {
        magazinePageHost = true
        magazinePinnedBottomYPx = 0
        lyricBootstrapUntilMs = SystemClock.elapsedRealtime() + LYRIC_BOOTSTRAP_GRACE_MS
    }

    private fun isMagazinePageHost(): Boolean = magazinePageHost

    /** 画报页钉死的歌词底边 Y（px）；高度变化时只改 topMargin，底边不跳。 */
    private var magazinePinnedBottomYPx: Int = 0
    /** 上次用于钉底的锚点百分比；配置变更则作废 pin。 */
    private var magazinePinnedAnchorPercent: Float = Float.NaN
    /** 锁屏侧：配置锚点变更后禁止再用旧 pin。 */
    private var lockscreenLyricAnchorPercent: Float = Float.NaN

    /** 歌词样式就绪后同步底栏歌曲信息（MiBlur / 对比色）。 */
    private var magazineChromeStyleSync: (() -> Unit)? = null
    /** 歌词侧单独取色完成后，回推底栏共用同一 contrast + accent。 */
    private var magazineSharedTintListener: ((contrast: Int, accent: Int) -> Unit)? = null

    /** 画报：沉浸歌词占大专辑槽时通知宿主重烘焙壁纸（hide=true 不叠前景专辑）。 */
    private var magazineAlbumSlotListener: ((hideAlbum: Boolean) -> Unit)? = null
    private var lastNotifiedMagazineHideAlbum: Boolean? = null

    fun setMagazineChromeStyleSync(listener: (() -> Unit)?) {
        magazineChromeStyleSync = listener
    }

    fun setMagazineSharedTintListener(listener: ((contrast: Int, accent: Int) -> Unit)?) {
        magazineSharedTintListener = listener
    }

    fun setMagazineAlbumSlotListener(listener: ((hideAlbum: Boolean) -> Unit)?) {
        magazineAlbumSlotListener = listener
        lastNotifiedMagazineHideAlbum = null
    }

    /** 播放/暂停变化可能改变占槽态。 */
    fun notifyMagazinePlaybackMayAffectAlbumSlot() {
        if (!isMagazinePageHost()) return
        updatePlayingState(force = true)
        updateVisibilityState()
    }

    private fun notifyMagazineAlbumSlotIfNeeded() {
        if (!isMagazinePageHost()) return
        // 仅沉浸歌词占专辑槽（与普通锁屏 isLyricPriorityOverAlbum 一致）
        val hide = isLyricPriorityOverAlbum()
        if (lastNotifiedMagazineHideAlbum == hide) return
        lastNotifiedMagazineHideAlbum = hide
        try {
            magazineAlbumSlotListener?.invoke(hide)
        } catch (_: Throwable) {
        }
    }

    /** 画报页壁纸刷新后供对比度取样；调用方保留 bitmap 所有权。 */
    fun setMagazineWallpaperForTint(bitmap: Bitmap?) {
        magazineWallpaperBitmap = bitmap?.takeIf { !it.isRecycled }
        if (isMagazinePageHost()) {
            requestMagazineMiBlurRefresh()
            invalidate()
        }
    }

    fun clearMagazineWallpaperForTint() {
        magazineWallpaperBitmap = null
    }

    /** 画报页：accent 染色 + contrast 判深浅（与底栏成对广播）。 */
    fun applyMagazineAlbumTint(accent: Int, contrast: Int = accent) {
        if (!isMagazinePageHost()) return
        fogTintColor = accent
        magazineContrastColor = contrast
        showFogBackground = !cfgImmersiveLyric
        immersiveMiBlurBlendKey = 0
        requestMagazineMiBlurRefresh()
        try {
            magazineChromeStyleSync?.invoke()
        } catch (_: Throwable) {
        }
    }

    private fun publishMagazineSharedTint(contrast: Int, accent: Int) {
        try {
            magazineSharedTintListener?.invoke(contrast, accent)
        } catch (_: Throwable) {
        }
    }

    /**
     * 画报页：稳定套真·MiBlur（透同窗壁纸），套上前可透明门闩。
     */
    fun requestMagazineMiBlurRefresh() {
        if (!isMagazinePageHost()) return
        val gen = ++magazineMiBlurRefreshGen
        magazineMiBlurRefreshInFlight = true
        if (MagazinePageMiBlurPolicy.hideUntilBlurReady() && !magazineMiBlurRevealed) {
            // 门闩：未就绪时表面 alpha 由 updateVisibilityState 配合
            magazineMiBlurRevealed = false
        }
        for (i in 0 until MagazinePageMiBlurPolicy.stableRetryCount()) {
            val delay = MagazinePageMiBlurPolicy.stableDelayAt(i)
            handler.postDelayed({
                if (gen != magazineMiBlurRefreshGen || !isAttachedToWindow) return@postDelayed
                if (!isMagazinePageHost()) return@postDelayed
                immersiveMiBlurBlendKey = 0
                syncImmersiveMiBlur()
                if (immersiveMiBlurActive) {
                    val firstReveal = !magazineMiBlurRevealed
                    magazineMiBlurRevealed = true
                    magazineMiBlurRefreshInFlight = false
                    applyImmersiveTextColors()
                    if (firstReveal) updateVisibilityState() else invalidate()
                    magazineChromeStyleSync?.invoke()
                    handler.postDelayed({
                        if (gen == magazineMiBlurRefreshGen) {
                            immersiveMiBlurBlendKey = 0
                            syncImmersiveMiBlur()
                            invalidate()
                            magazineChromeStyleSync?.invoke()
                        }
                    }, 90L)
                } else if (i == MagazinePageMiBlurPolicy.stableRetryCount() - 1) {
                    magazineMiBlurRevealed = true
                    magazineMiBlurRefreshInFlight = false
                    applyImmersiveTextColors()
                    updateVisibilityState()
                    magazineChromeStyleSync?.invoke()
                }
            }, delay)
        }
    }

    // ============================================================
    // 常量
    // ============================================================
    /** 渐变遮罩底部/中部/顶部透明度（0-255，底部最浓、向上消散） */
    private val fogMaskAlphaBottom = 238
    private val fogMaskAlphaMid = 175
    private val fogMaskAlphaLight = 80
    /** 歌词内容上下内边距（dp） */
    private val vPaddingDp = 14f

    /** 沉浸歌词固定字号（与默认歌词字号独立） */
    private val immersiveLyricSizeSp = 40f
    private val immersiveSecondSizeRatio = 0.55f
    /** 主歌词至少占用的行数（区域够宽时） */
    private val immersiveMinMainLines = 3
    /** 翻译最多行数 */
    private val immersiveMaxSecondLines = 2
    /** 沉浸歌词文字混入专辑主色的比例 */
    private val immersiveTintWeight = 0.28f
    /** 锁屏歌词切行：离场 / 入场时长见 [LyricMotionPolicy] */
    private val lineFadeOutMs = LyricMotionPolicy.LINE_EXIT_MS
    private val lineFadeInMs = LyricMotionPolicy.LINE_ENTER_MS

    private var lineContentAlpha = 1f
    private var lineContentTx = 0f
    private var lineContentTy = 0f
    private var lineTransitionAnimator: ValueAnimator? = null

    /** 沉浸三行上滑 */
    private var cfgImmersiveLyricStack: Boolean = false
    private var stackPrevText = ""
    private var stackCurrentText = ""
    private var stackCurrentSecondaryText = ""
    private var stackNextText = ""
    private var stackPrevLayout: StaticLayout? = null
    private var stackCurrentLayout: StaticLayout? = null
    private var stackCurrentSecondaryLayout: StaticLayout? = null
    private var stackNextLayout: StaticLayout? = null
    private var stackPrevEndFade = false
    private var stackCurrentEndFade = false
    private var stackCurrentSecondaryEndFade = false
    private var stackNextEndFade = false
    private var mainLayoutEndFade = false
    private var immersiveSecondEndFade = false
    private var stackScrollOffset = 0f
    /** 晋升上滑进度 0→1；静止为 1 */
    private var stackAnimProgress = 1f
    /** 动画态：先滚旧词再换新 triplet（对齐 HyperLyric） */
    private var stackMotionActive = false
    private var stackAnimator: ValueAnimator? = null
    private var pendingStackTriplet: ImmersiveLyricStackPolicy.Triplet? = null
    private var pendingStackIndex: Int = -1
    private var lastStackLineIndex = -1
    /** 画报页切行滞回，抑制进度回弹抽搐 */
    private var magazineHeldLineIndex = -1
    /** 沉浸栈视口高度：同曲只增不减，底边钉死后内容贴底画 */
    private var stackViewportHeightPx = 0
    private val endFadeMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stackLineLayerPaint = Paint()

    // ============================================================
    // 绘制相关
    // ============================================================
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f * resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f, 0f, 3f, Color.argb(230, 0, 0, 0))
    }

    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)
        textSize = 16f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        setShadowLayer(7f, 1f, 3f, Color.argb(210, 0, 0, 0))
    }

    private val hPaddingPx: Float
        get() = 20f * resources.displayMetrics.density

    private val vPaddingPx: Float
        get() = vPaddingDp * resources.displayMetrics.density

    private val lineGapPx: Float
        get() = 4f * resources.displayMetrics.density

    private var currentMainText = ""
    private var currentSecondText = ""
    private var hasSecondLine = false

    // ============================================================
    // 歌词/翻译互换（固定开启）
    // ============================================================
    // 原始的主行/副行文本（互换前的原始数据）
    private var rawMainText = ""
    private var rawSecondText = ""
    private var rawHasSecond = false
    // 第二行是否是翻译（true=翻译，false=下一句歌词）
    private var secondIsTranslation = false

    // ============================================================
    // 主行多行显示（自动换行）
    // ============================================================
    private var mainStaticLayout: StaticLayout? = null
    private var immersiveSecondStaticLayout: StaticLayout? = null

    // ============================================================
    // 渐变遮罩（专辑下半主色调 + 半透明黑，自下而上消散）
    // ============================================================
    private var fogTintColor: Int? = null
    /** 画报页亮度代表色（与 accent 分离，专供浅/深字判定） */
    private var magazineContrastColor: Int? = null
    /** 画报页烘焙壁纸（仅取样，不持有所有权 / 不 recycle） */
    @Volatile
    private var magazineWallpaperBitmap: Bitmap? = null
    private var magazineMiBlurRefreshGen = 0
    private var magazineMiBlurRefreshInFlight = false
    /** 画报页真·MiBlur 已套上并可露出 */
    @Volatile
    private var magazineMiBlurRevealed = false
    /** 是否绘制渐变遮罩背景（歌词文字不受此影响） */
    private var showFogBackground = false
    private var fogBuildGeneration = 0
    /** 缓存渐变，避免每帧重建导致闪 */
    private var fogShader: LinearGradient? = null
    private var fogShaderW = 0
    private var fogShaderH = 0
    private var fogShaderTint: Int = 0
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 沉浸歌词是否已接上系统 MiBlur 透壁纸染色 */
    private var immersiveMiBlurActive = false
    private var immersiveMiBlurBlendKey: Int = 0
    /** 当前壁纸区域偏亮时改用深色透色，保证浅底可读 */
    private var immersiveMiBlurOnLightBg = false

    /** 供动画策略：MiBlur 生效时避免 translation 掉帧 */
    fun hasActiveMiBlur(): Boolean = immersiveMiBlurActive

    // ============================================================
    // 配置
    // ============================================================
    private var cfgLyricEnabled: Boolean = true
    private var cfgShowLyric: Boolean = true
    private var cfgLyricSize: Float = 20f
    private var cfgSwapLyric: Boolean = true
    /** 歌词区域宽度：占屏幕宽度的百分比 */
    private var cfgLyricWidth: Float = 55f
    /** 歌词底边占屏幕高度百分比 */
    private var cfgLyricBgAnchorY: Float = 62f
    private var cfgImmersiveLyric: Boolean = false
    /** 画报沉浸：对标普通沉浸，宽度/底边跟画报专辑档案。 */
    private var cfgMagazineAlbumSize: Float = 70f
    private var cfgMagazineAlbumAnchorY: Float = 55f
    private var cfgLyricHideBackground: Boolean = false
    private var cfgLyricAlign: String = "left"
    private var cfgLyricTransition: String = LyricLineTransitionPolicy.FADE

    // 通知中心/QS 是否展开（展开时歌词应隐藏，只显示在锁屏）
    @Volatile
    private var shadeOpen: Boolean = false

    // 有没有有效歌词
    var hasLyric: Boolean = false
        private set

    // ============================================================
    // 歌词数据
    // ============================================================
    data class LyricLine(val time: Long, val text: String, val translation: String = "")

    private var lastLyricJson: String = "{}"
    private var lastLyricVersion: Int = -1
    private var lastLyricFdVersion: Int = -1
    private var cachedLines: List<LyricLine>? = null
    private var cachedCtx: org.json.JSONObject? = null
    private var lastSongTitle: String = ""
    private var lastKnownTrackKey: String? = null
    /** Provider 仍为上一首歌词、与 Session 不一致时已隐藏，避免每轮询重复 reflow。 */
    private var staleProviderLyricSuppressed = false
    /** 切歌门闩：AOD / 亮屏共用 [TrackLyricGate]。 */
    private var trackGatePhase: TrackLyricGate.Phase = TrackLyricGate.Phase.IDLE
    private var trackGateSnapshot: TrackLyricGate.Snapshot? = null

    private var dataDirty = false
    private var lastVersionsCheck: Long = 0

    private var isPlaying = false
    /** Session 明确 PAUSED/STOPPED；与「短暂读不到 PLAYING」区分。 */
    private var confirmedPaused = false
    private var playbackHoldUntilMs = 0L
    private var lastPlayingCheck: Long = 0
    /**
     * 切歌后尚未确认有/无词：播放中优先占歌词位，避免专辑抢先闪一下。
     */
    private var preferLyricUntilResolved = false
    /** 锁屏↔AOD 切换后钉住歌词可见性，避免淡出再淡入。 */
    private var aodVisibilityPinUntilMs = 0L

    private var posBase: Long = 0
    private var posBaseTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    private var lyricObserver: ContentObserver? = null
    private var lyricObserverRegistered = false

    private var configObserver: ContentObserver? = null
    private var configObserverRegistered = false

    private var aodScreenReceiver: BroadcastReceiver? = null
    private var aodRecoveryBurstGeneration = 0
    private var lyricBootstrapBurstGeneration = 0
    private var lyricBootstrapUntilMs = 0L
    private val clearPreferLyricRunnable = Runnable {
        // 只让出专辑槽；保留切歌快照，否则网络源迟到的 lyric_fd 会被当成旧包丢掉
        preferLyricUntilResolved = false
        if (!hasLyric) {
            updateVisibilityState()
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    // ============================================================
    // 测量与绘制
    // ============================================================
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!shouldShowLyricOverlay()) {
            setMeasuredDimension(0, 0)
            return
        }

        val lyricWidth = computeLyricWidthPx()
        if (cfgImmersiveLyric) {
            if (isImmersiveStackActive()) {
                // 动画中勿在 measure 里重建 StaticLayout（切行首帧会卡一下）
                if (!stackMotionActive) {
                    ensureStackLayouts((lyricWidth - hPaddingPx * 2).toInt().coerceAtLeast(1))
                }
                val contentH = computeImmersiveStackHeightPx()
                val lpH = (layoutParams?.height ?: 0).takeIf { it > 0 } ?: 0
                val h = maxOf(contentH, lpH, stackViewportHeightPx)
                setMeasuredDimension(
                    resolveSizeAndState(lyricWidth, widthMeasureSpec, 0),
                    resolveSizeAndState(h, heightMeasureSpec, 0)
                )
            } else {
                rebuildImmersiveLayouts()
                setMeasuredDimension(
                    resolveSizeAndState(lyricWidth, widthMeasureSpec, 0),
                    resolveSizeAndState(lyricWidth, heightMeasureSpec, 0)
                )
            }
            return
        }

        val mainText = currentMainText.ifBlank { " " }
        val layout = mainStaticLayout?.takeIf {
            it.width == (lyricWidth - hPaddingPx * 2).toInt()
        } ?: buildMainLayout(mainText).also { mainStaticLayout = it }

        val contentHeight = computeContentHeightPx(layout, hasSecondLine)
        setMeasuredDimension(
            resolveSizeAndState(lyricWidth, widthMeasureSpec, 0),
            resolveSizeAndState(contentHeight, heightMeasureSpec, 0)
        )
    }

    /** 按当前主行 StaticLayout + 副行实际内容计算高度（自适应） */
    private fun computeContentHeightPx(layout: StaticLayout, hasSecond: Boolean): Int {
        val secondHeight = if (hasSecond) {
            val sfm = secondPaint.fontMetrics
            sfm.bottom - sfm.top
        } else 0f
        val textHeight = layout.height.toFloat() +
            (if (hasSecond) lineGapPx + secondHeight else 0f)
        return (textHeight + vPaddingPx * 2).toInt().coerceAtLeast(1)
    }

    /**
     * 高度变化时钉死底边：优先用 MediaFollow 已钉像素，避免 top+height 漂移。
     * 沉浸三行视口只增不减，内容在 view 内贴底绘制。
     * 锚点变更时即使高度不变也必须改 topMargin（否则滑条无效）。
     */
    private fun resizeKeepingBottom(newHeight: Int) {
        val lyricWidth = computeLyricWidthPx()
        val lp = layoutParams as? FrameLayout.LayoutParams
        if (lp == null) {
            requestLayout()
            return
        }
        val targetH = if (isImmersiveStackActive()) {
            val grown = maxOf(newHeight, stackViewportHeightPx)
            stackViewportHeightPx = grown
            grown
        } else {
            newHeight
        }
        val oldHeight = when {
            lp.height > 0 -> lp.height
            height > 0 -> height
            measuredHeight > 0 -> measuredHeight
            else -> 0
        }
        val parentH = if (isMagazinePageHost()) {
            // 画报页用屏高钉锚点，避免 parent.height 0→实高时底边跳变
            resources.displayMetrics.heightPixels.coerceAtLeast(1)
        } else {
            (parent as? View)?.height?.takeIf { it > 0 }
                ?: resources.displayMetrics.heightPixels
        }
        val bottom = resolveLyricBottomYPx(parentH, oldHeight, lp.topMargin)
        val desiredTop = (bottom - targetH).coerceAtLeast(0)
        val sizeSame = oldHeight == targetH && lp.width == lyricWidth
        val posSame = lp.topMargin == desiredTop
        if (sizeSame && posSame) {
            if (!isMagazinePageHost()) {
                MediaFollowController.syncLyricLaidOut(targetH)
            }
            invalidate()
            return
        }
        lp.topMargin = desiredTop
        lp.width = lyricWidth
        lp.height = targetH
        lp.gravity = if (isMagazinePageHost()) {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.TOP or Gravity.START
        }
        layoutParams = lp
        if (!isMagazinePageHost()) {
            MediaFollowController.pinLyricBottom(bottom, targetH)
            lockscreenLyricAnchorPercent = if (cfgImmersiveLyric) {
                ConfigReader.albumAnchorY(context).coerceIn(10f, 95f)
            } else {
                ConfigReader.lyricBgAnchorY(context).coerceIn(10f, 95f)
            }
        }
        if (shouldDisplayLyric() && visibility == INVISIBLE) {
            scheduleRevealAfterLayout()
        }
    }

    /** 当前应钉的歌词底边 Y（相对 parent / 画报屏高）。 */
    private fun resolveLyricBottomYPx(parentH: Int, oldHeight: Int, topMargin: Int): Int {
        when {
            !isMagazinePageHost() && MediaFollowController.pinnedLyricBottomY() > 0 &&
                !lockscreenLyricAnchorStale() ->
                return MediaFollowController.pinnedLyricBottomY()
            !isMagazinePageHost() && oldHeight > 0 && topMargin + oldHeight > 0 &&
                !lockscreenLyricAnchorStale() ->
                return topMargin + oldHeight
            isMagazinePageHost() -> {
                val dm = resources.displayMetrics
                val normalMax = MagazinePageChromePolicy.lyricBottomAnchorMaxPercent(
                    screenHeightPx = parentH.coerceAtLeast(dm.heightPixels),
                    density = dm.density,
                    scaledDensity = dm.scaledDensity,
                )
                val anchor = MagazinePageLyricHostPolicy.resolveBottomAnchorYPercent(
                    immersiveLyric = cfgImmersiveLyric,
                    lyricBgAnchorY = cfgLyricBgAnchorY,
                    albumAnchorY = cfgMagazineAlbumAnchorY,
                    normalLyricMaxPercent = normalMax,
                )
                if (magazinePinnedBottomYPx <= 0 ||
                    MediaFollowLyricPinPolicy.shouldClearPinOnAnchorChange(
                        magazinePinnedAnchorPercent,
                        anchor,
                    )
                ) {
                    magazinePinnedAnchorPercent = anchor
                    magazinePinnedBottomYPx = (parentH * (anchor / 100f)).toInt()
                }
                return magazinePinnedBottomYPx
            }
            else -> {
                // 普通歌词跟 lyricBgAnchorY；沉浸跟专辑底边（与 MediaFollowController 一致）
                val anchor = if (cfgImmersiveLyric) {
                    ConfigReader.albumAnchorY(context).coerceIn(10f, 95f)
                } else {
                    ConfigReader.lyricBgAnchorY(context).coerceIn(10f, 95f)
                }
                lockscreenLyricAnchorPercent = anchor
                return (parentH * (anchor / 100f)).toInt()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }

    private fun lockscreenLyricAnchorStale(): Boolean {
        if (lockscreenLyricAnchorPercent.isNaN()) return true
        val expected = if (cfgImmersiveLyric) {
            ConfigReader.albumAnchorY(context).coerceIn(10f, 95f)
        } else {
            ConfigReader.lyricBgAnchorY(context).coerceIn(10f, 95f)
        }
        return MediaFollowLyricPinPolicy.shouldClearPinOnAnchorChange(
            lockscreenLyricAnchorPercent,
            expected,
        )
    }

    /** 歌词区域宽度（px）：沉浸模式用专辑区块宽度，否则屏宽 × 百分比 */
    private fun computeLyricWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return if (cfgImmersiveLyric) {
            val sizePct = if (isMagazinePageHost()) {
                cfgMagazineAlbumSize
            } else {
                ConfigReader.albumSize(context)
            }
            (screenWidth * sizePct / 100f).toInt().coerceAtLeast(1)
        } else {
            (screenWidth * cfgLyricWidth / 100f).toInt().coerceAtLeast(1)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!shouldDisplayLyric()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. 渐变遮罩（普通模式且未接 MiBlur；MiBlur 生效时只留字形透色）— 不参与切行动画
        if (!cfgImmersiveLyric && !immersiveMiBlurActive) {
            drawFogBackground(canvas, w, h)
        }

        val contentWidth = w - hPaddingPx * 2

        // 2. 歌词内容。alpha≈1 且无位移时禁止整 view saveLayer（沉浸切行每帧省一次离屏）
        if (lineContentAlpha <= 0.001f) return
        val needContentLayer =
            lineContentAlpha < 0.999f ||
                lineContentTx != 0f ||
                lineContentTy != 0f
        if (needContentLayer) {
            stackLineLayerPaint.alpha = (lineContentAlpha * 255f).toInt().coerceIn(0, 255)
            val layer = canvas.saveLayer(0f, 0f, w, h, stackLineLayerPaint)
            canvas.translate(lineContentTx, lineContentTy)
            drawContent(canvas, w, contentWidth,
                currentMainText, currentSecondText, hasSecondLine, mainStaticLayout)
            canvas.restoreToCount(layer)
        } else {
            drawContent(canvas, w, contentWidth,
                currentMainText, currentSecondText, hasSecondLine, mainStaticLayout)
        }
    }

    /**
     * 绘制一组歌词内容（主行 + 副行），内容底部对齐到 view 底部内边距之上。
     */
    private fun drawContent(canvas: Canvas, w: Float, contentWidth: Float,
                            mainText: String, secondText: String, hasSecond: Boolean,
                            layout: StaticLayout?) {
        if (cfgImmersiveLyric) {
            drawImmersiveContent(canvas, w, contentWidth, mainText, secondText, hasSecond)
            return
        }

        val h = height.toFloat()
        val contentBottom = h - vPaddingPx

        if (layout != null) {
            val text = mainText.ifBlank { " " }
            val fm = mainPaint.fontMetrics
            val lineHeight = fm.bottom - fm.top

            val secondHeight = if (hasSecond) {
                val sfm = secondPaint.fontMetrics
                sfm.bottom - sfm.top
            } else 0f
            val mainBottom = contentBottom - (if (hasSecond) lineGapPx + secondHeight else 0f)

            mainPaint.textAlign = Paint.Align.LEFT
            val lineCount = layout.lineCount
            val lastBaseline = mainBottom - fm.bottom
            val firstBaseline = lastBaseline - (lineCount - 1) * lineHeight
            var y = firstBaseline

            for (i in 0 until lineCount) {
                val lineStart = layout.getLineStart(i)
                val lineEnd = layout.getLineEnd(i)
                val lineText = text.substring(lineStart, lineEnd)
                val lineWidth = mainPaint.measureText(lineText)
                val x = hPaddingPx + (contentWidth - lineWidth) / 2f
                canvas.drawText(lineText, 0, lineText.length, x, y, mainPaint)
                y += lineHeight
            }
        }

        if (hasSecond) {
            val sfm = secondPaint.fontMetrics
            val secondBaseline = contentBottom - sfm.bottom
            drawTextCentered(canvas, secondText, w / 2f, secondBaseline, secondPaint, contentWidth.toInt())
        }
    }

    /** 沉浸歌词：单行居中，或三行上滑（上一句/当前/下一句） */
    private fun drawImmersiveContent(
        canvas: Canvas, w: Float, contentWidth: Float,
        mainText: String, secondText: String, hasSecond: Boolean
    ) {
        if (isImmersiveStackActive()) {
            drawImmersiveStack(canvas, w, contentWidth)
            return
        }
        val mainLayout = mainStaticLayout ?: return
        val h = height.toFloat()

        val mainH = mainLayout.height.toFloat()
        val secondLayout = if (hasSecond) immersiveSecondStaticLayout else null
        val secondH = secondLayout?.height?.toFloat() ?: 0f
        val gap = if (secondLayout != null) lineGapPx else 0f
        val totalH = mainH + gap + secondH

        val startY = vPaddingPx + ((h - vPaddingPx * 2f - totalH) / 2f).coerceAtLeast(0f)

        canvas.save()
        canvas.translate(hPaddingPx, startY)
        drawStaticLayoutWithOptionalEndFade(canvas, mainLayout, mainLayoutEndFade, mainPaint.textSize)
        canvas.restore()

        if (secondLayout != null) {
            canvas.save()
            canvas.translate(hPaddingPx, startY + mainH + gap)
            drawStaticLayoutWithOptionalEndFade(
                canvas,
                secondLayout,
                immersiveSecondEndFade,
                secondPaint.textSize,
            )
            canvas.restore()
        }
    }

    private fun isImmersiveStackActive(): Boolean {
        return ImmersiveLyricStackPolicy.shouldUseStack(
            immersiveLyric = cfgImmersiveLyric,
            stackEnabled = cfgImmersiveLyricStack,
            screenInteractive = HookUtils.isScreenInteractive(context),
        )
    }

    private fun drawImmersiveStack(canvas: Canvas, w: Float, contentWidth: Float) {
        ensureStackLayouts(contentWidth.toInt().coerceAtLeast(1))
        val current = stackCurrentLayout ?: return
        val gap = lineGapPx
        val prevH = stackPrevLayout?.height?.toFloat() ?: 0f
        val curH = current.height.toFloat()
        val secH = stackCurrentSecondaryLayout?.height?.toFloat() ?: 0f
        val nextH = stackNextLayout?.height?.toFloat() ?: 0f
        val geo = ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = vPaddingPx,
            gapPx = gap,
            prevHeightPx = prevH,
            currentHeightPx = curH,
            secondaryHeightPx = secH,
            nextHeightPx = nextH,
        )
        // 贴视口底：增高只加上方空，当前块相对底边不跳
        val originY = ImmersiveLyricStackPolicy.contentOriginY(height.toFloat(), geo.heightPx)
        val offset = if (stackMotionActive) stackScrollOffset else 0f

        canvas.save()
        canvas.clipRect(0f, 0f, w, height.toFloat())

        fun y(top: Float): Float = originY + top + offset

        val prev = stackPrevLayout
        if (prev != null && prevH > 0f) {
            drawStackLineTop(
                canvas, prev, hPaddingPx, y(geo.prevTop),
                ImmersiveLyricStackPolicy.NEIGHBOR_ALPHA,
                stackPrevEndFade, mainPaint.textSize,
            )
        }
        drawStackLineTop(
            canvas, current, hPaddingPx, y(geo.currentTop), 1f,
            stackCurrentEndFade, mainPaint.textSize,
        )
        val secondary = stackCurrentSecondaryLayout
        if (secondary != null && secH > 0f) {
            drawStackLineTop(
                canvas, secondary, hPaddingPx, y(geo.secondaryTop),
                ImmersiveLyricStackPolicy.SECONDARY_ALPHA,
                stackCurrentSecondaryEndFade, secondPaint.textSize,
            )
        }
        val next = stackNextLayout
        if (next != null && nextH > 0f) {
            drawStackLineTop(
                canvas, next, hPaddingPx, y(geo.nextTop),
                ImmersiveLyricStackPolicy.NEIGHBOR_ALPHA,
                stackNextEndFade, mainPaint.textSize,
            )
        }
        canvas.restore()
    }

    private fun computeImmersiveStackHeightPx(): Int {
        if (!stackMotionActive) {
            ensureStackLayouts((computeLyricWidthPx() - hPaddingPx * 2).toInt().coerceAtLeast(1))
        }
        val current = stackCurrentLayout ?: return computeLyricWidthPx()
        return ImmersiveLyricStackPolicy.bottomGeometry(
            vPaddingPx = vPaddingPx,
            gapPx = lineGapPx,
            prevHeightPx = stackPrevLayout?.height?.toFloat() ?: 0f,
            currentHeightPx = current.height.toFloat(),
            secondaryHeightPx = stackCurrentSecondaryLayout?.height?.toFloat() ?: 0f,
            nextHeightPx = stackNextLayout?.height?.toFloat() ?: 0f,
        ).heightPx.toInt().coerceAtLeast(1)
    }

    private fun drawStackLineTop(
        canvas: Canvas,
        layout: StaticLayout,
        x: Float,
        top: Float,
        alphaScale: Float,
        endFade: Boolean,
        textSizePx: Float,
    ) {
        val paint = layout.paint
        val oldAlpha = paint.alpha
        val oldAlign = paint.textAlign
        paint.alpha = (oldAlpha * alphaScale * lineContentAlpha).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(x, top)
        // 行宽渐隐动画中也要画；半透明用 paint.alpha，不再套一层 saveLayer
        drawStaticLayoutWithOptionalEndFade(canvas, layout, endFade, textSizePx)
        canvas.restore()
        paint.alpha = oldAlpha
        paint.textAlign = oldAlign
    }

    private fun ensureStackLayouts(contentW: Int) {
        val cur = stackCurrentText.ifBlank { " " }
        val needRebuild =
            stackCurrentLayout == null ||
                stackCurrentLayout!!.width != contentW ||
                (stackCurrentLayout!!.text?.toString() ?: "") != cur ||
                (stackCurrentSecondaryLayout?.text?.toString() ?: "") != stackCurrentSecondaryText
        if (!needRebuild) return
        rebuildStackLayouts(contentW)
    }

    private fun rebuildStackLayouts(contentW: Int = (computeLyricWidthPx() - hPaddingPx * 2).toInt().coerceAtLeast(1)) {
        // 上一句 / 下一句只显示一行；当前行可两行；翻译单独一层
        if (stackPrevText.isNotBlank()) {
            val built = buildImmersiveLayout(stackPrevText, mainPaint, contentW, maxLines = 1)
            stackPrevLayout = built.layout
            stackPrevEndFade = built.endFade
        } else {
            stackPrevLayout = null
            stackPrevEndFade = false
        }
        run {
            val built = buildImmersiveLayout(
                stackCurrentText.ifBlank { " " },
                mainPaint,
                contentW,
                maxLines = 2,
            )
            stackCurrentLayout = built.layout
            stackCurrentEndFade = built.endFade
        }
        if (stackCurrentSecondaryText.isNotBlank()) {
            val built = buildImmersiveLayout(
                stackCurrentSecondaryText,
                secondPaint,
                contentW,
                maxLines = immersiveMaxSecondLines,
            )
            stackCurrentSecondaryLayout = built.layout
            stackCurrentSecondaryEndFade = built.endFade
        } else {
            stackCurrentSecondaryLayout = null
            stackCurrentSecondaryEndFade = false
        }
        if (stackNextText.isNotBlank()) {
            val built = buildImmersiveLayout(stackNextText, mainPaint, contentW, maxLines = 1)
            stackNextLayout = built.layout
            stackNextEndFade = built.endFade
        } else {
            stackNextLayout = null
            stackNextEndFade = false
        }
    }

    private fun rebuildImmersiveLayouts() {
        if (isImmersiveStackActive()) {
            rebuildStackLayouts()
            return
        }
        val block = computeLyricWidthPx()
        val contentW = (block - hPaddingPx * 2).toInt().coerceAtLeast(1)
        val main = currentMainText.ifBlank { " " }
        val maxMain = computeImmersiveMaxMainLines(hasSecondLine, block)
        val mainBuilt = buildImmersiveLayout(main, mainPaint, contentW, maxMain)
        mainStaticLayout = mainBuilt.layout
        mainLayoutEndFade = mainBuilt.endFade
        if (hasSecondLine && currentSecondText.isNotBlank()) {
            val secondBuilt = buildImmersiveLayout(
                currentSecondText, secondPaint, contentW, immersiveMaxSecondLines
            )
            immersiveSecondStaticLayout = secondBuilt.layout
            immersiveSecondEndFade = secondBuilt.endFade
        } else {
            immersiveSecondStaticLayout = null
            immersiveSecondEndFade = false
        }
    }

    private fun computeImmersiveMaxMainLines(hasSecond: Boolean, blockSizePx: Int): Int {
        val contentH = blockSizePx - vPaddingPx * 2f
        val mainLineH = mainPaint.fontMetrics.run { bottom - top }
        val secondReserve = if (hasSecond) {
            val slh = secondPaint.fontMetrics.run { bottom - top }
            slh * immersiveMaxSecondLines + lineGapPx
        } else 0f
        val fit = ((contentH - secondReserve) / mainLineH).toInt().coerceAtLeast(1)
        return if (fit >= immersiveMinMainLines) {
            fit.coerceAtLeast(immersiveMinMainLines)
        } else {
            fit
        }
    }

    private data class ImmersiveLayoutBuild(
        val layout: StaticLayout,
        val endFade: Boolean,
    )

    private fun buildImmersiveLayout(
        text: String, paint: Paint, contentWidth: Int, maxLines: Int
    ): ImmersiveLayoutBuild {
        val tp = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        val alignment = when (cfgLyricAlign) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val raw = text.ifBlank { " " }
        // maxLines  alone 不会裁剪；先量满布局，再截到可见字符后重建
        val full = StaticLayout.Builder
            .obtain(raw, 0, raw.length, tp, contentWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .build()
        val clippedEnd = LyricTextFadeTruncate.visibleTextEndOffset(
            fullTextLength = raw.length,
            unrestrictedLineCount = full.lineCount,
            maxLines = maxLines,
        ) { line -> full.getLineEnd(line) }
        val shown = if (clippedEnd >= raw.length) raw else raw.substring(0, clippedEnd)
        val layout = StaticLayout.Builder
            .obtain(shown, 0, shown.length, tp, contentWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .build()
        var endFade = LyricTextFadeTruncate.needsEndFadeForClippedLayout(
            unrestrictedLineCount = full.lineCount,
            clippedLineCount = minOf(maxLines, full.lineCount),
            maxLines = maxLines,
            clippedTextEndOffset = clippedEnd,
            fullTextLength = raw.length,
        )
        // 任意语言：行数未超但行宽溢出框外（不可断长词等）
        if (!endFade) {
            for (i in 0 until layout.lineCount) {
                if (LyricTextFadeTruncate.needsEndFadeForLineWidth(
                        layout.getLineWidth(i),
                        contentWidth.toFloat(),
                    )
                ) {
                    endFade = true
                    break
                }
            }
        }
        return ImmersiveLayoutBuild(layout, endFade)
    }

    /**
     * 绘制 StaticLayout：永不画省略号；截断时在阅读方向末尾可视边渐隐（全语言）。
     */
    private fun drawStaticLayoutWithOptionalEndFade(
        canvas: Canvas,
        layout: StaticLayout,
        endFade: Boolean,
        textSizePx: Float,
    ) {
        if (layout.width <= 0 || layout.height <= 0) return
        val layoutW = layout.width.toFloat()
        val lastLine = layout.lineCount - 1
        val lastOverflow = lastLine >= 0 && LyricTextFadeTruncate.needsEndFadeForLineWidth(
            layout.getLineWidth(lastLine),
            layoutW,
        )
        if (!endFade && !lastOverflow) {
            layout.draw(canvas)
            return
        }
        val text = layout.text
        val paint = layout.paint
        // 升降部可能超出 lineTop/Bottom，略扩层高以免渐隐裁切字形
        val vPad = (textSizePx * 0.2f).coerceIn(2f, 8f)

        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            var end = layout.getLineEnd(i)
            if (end > start && text[end - 1] == '\n') end--
            if (end <= start) continue
            val baseline = layout.getLineBaseline(i).toFloat()
            val left = layout.getLineLeft(i)
            val top = layout.getLineTop(i).toFloat() - vPad
            val bottom = layout.getLineBottom(i).toFloat() + vPad

            if (i != lastLine) {
                canvas.drawText(text, start, end, left, baseline, paint)
                continue
            }

            val textRight = layout.getLineRight(i)
            val rtl = layout.getParagraphDirection(i) == Layout.DIR_RIGHT_TO_LEFT
            val fadeW = LyricTextFadeTruncate.resolveFadeWidthPx(
                text = text,
                lineStart = start,
                lineEnd = end,
                textSizePx = textSizePx,
            ) { s, e -> paint.measureText(text, s, e) }
            val geo = LyricTextFadeTruncate.endFadeGeometry(
                lineLeft = left,
                lineRight = textRight,
                layoutWidth = layoutW,
                fadeWidthPx = fadeW,
                rtl = rtl,
            )

            val layer = canvas.saveLayer(geo.layerLeft, top, geo.layerRight, bottom, null)
            canvas.drawText(text, start, end, left, baseline, paint)
            endFadeMaskPaint.shader = LinearGradient(
                geo.fadeOpaqueX, 0f, geo.fadeTransparentX, 0f,
                intArrayOf(Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            endFadeMaskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(geo.layerLeft, top, geo.layerRight, bottom, endFadeMaskPaint)
            endFadeMaskPaint.xfermode = null
            endFadeMaskPaint.shader = null
            canvas.restoreToCount(layer)
        }
    }

    private fun drawTextCentered(
        canvas: Canvas, text: String, centerX: Float, y: Float,
        paint: Paint, maxWidthPx: Int
    ) {
        val raw = text.ifBlank { " " }
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT

        val textWidth = paint.measureText(raw)
        if (textWidth <= maxWidthPx) {
            val x = centerX - textWidth / 2f
            canvas.drawText(raw, x, y, paint)
            paint.textAlign = originalAlign
            return
        }

        val count = paint.breakText(raw, true, maxWidthPx.toFloat(), null).coerceAtLeast(1)
        val shownEnd = count.coerceAtMost(raw.length)
        val shownW = paint.measureText(raw, 0, shownEnd)
        val x = centerX - shownW / 2f
        drawTextWithLastGlyphFade(canvas, raw, 0, shownEnd, x, y, paint)
        paint.textAlign = originalAlign
    }

    private fun drawTextAligned(
        canvas: Canvas, text: String, viewWidth: Float, y: Float,
        paint: Paint, maxWidthPx: Int
    ) {
        val raw = text.ifBlank { " " }
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        val contentWidth = maxWidthPx.toFloat()
        val textWidth = paint.measureText(raw)
        if (textWidth <= contentWidth) {
            val x = when (cfgLyricAlign) {
                "center" -> hPaddingPx + (contentWidth - textWidth) / 2f
                "right" -> hPaddingPx + contentWidth - textWidth
                else -> hPaddingPx
            }
            canvas.drawText(raw, x, y, paint)
            paint.textAlign = originalAlign
            return
        }
        val count = paint.breakText(raw, true, contentWidth, null).coerceAtLeast(1)
        val shownEnd = count.coerceAtMost(raw.length)
        val shownW = paint.measureText(raw, 0, shownEnd)
        val x = when (cfgLyricAlign) {
            "center" -> hPaddingPx + (contentWidth - shownW) / 2f
            "right" -> hPaddingPx + contentWidth - shownW
            else -> hPaddingPx
        }
        drawTextWithLastGlyphFade(canvas, raw, 0, shownEnd, x, y, paint)
        paint.textAlign = originalAlign
    }

    /** 阅读方向末尾可视边渐隐，不画省略号（全语言）。 */
    private fun drawTextWithLastGlyphFade(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        y: Float,
        paint: Paint,
    ) {
        if (end <= start) return
        val fm = paint.fontMetrics
        val shownW = paint.measureText(text, start, end)
        val textRight = x + shownW
        val fadeW = LyricTextFadeTruncate.resolveFadeWidthPx(
            text = text,
            lineStart = start,
            lineEnd = end,
            textSizePx = paint.textSize,
        ) { s, e -> paint.measureText(text, s, e) }
        val geo = LyricTextFadeTruncate.endFadeGeometry(
            lineLeft = x,
            lineRight = textRight,
            layoutWidth = textRight,
            fadeWidthPx = fadeW,
            rtl = false,
        )
        val top = y + fm.ascent - 2f
        val bottom = y + fm.descent + 2f
        val layer = canvas.saveLayer(geo.layerLeft, top, geo.layerRight, bottom, null)
        canvas.drawText(text, start, end, x, y, paint)
        endFadeMaskPaint.shader = LinearGradient(
            geo.fadeOpaqueX, 0f, geo.fadeTransparentX, 0f,
            intArrayOf(Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        endFadeMaskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(geo.layerLeft, top, geo.layerRight, bottom, endFadeMaskPaint)
        endFadeMaskPaint.xfermode = null
        endFadeMaskPaint.shader = null
        canvas.restoreToCount(layer)
    }

    /**
     * 渐变遮罩：取专辑下半主色调，与黑色混合后自下而上半透明消散。
     */
    private fun drawFogBackground(canvas: Canvas, w: Float, h: Float) {
        if (cfgImmersiveLyric || !showFogBackground || cfgLyricHideBackground) return
        val tint = fogTintColor ?: return
        val wi = w.toInt()
        val hi = h.toInt()
        if (fogShader == null || fogShaderW != wi || fogShaderH != hi || fogShaderTint != tint) {
            fogShader = LinearGradient(
                0f, h, 0f, 0f,
                intArrayOf(
                    tintedMaskColor(fogMaskAlphaBottom, tint, 0.28f),
                    tintedMaskColor(fogMaskAlphaMid, tint, 0.18f),
                    tintedMaskColor(fogMaskAlphaLight, tint, 0.10f),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            fogShaderW = wi
            fogShaderH = hi
            fogShaderTint = tint
            fogPaint.shader = fogShader
        }

        val cornerRadius = ConfigReader.albumCorner(context) * resources.displayMetrics.density
        val rect = RectF(0f, 0f, w, h)
        val path = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(rect, fogPaint)
        canvas.restore()
    }

    /** 将专辑主色与黑色混合，得到带色调的半透明遮罩色。 */
    private fun tintedMaskColor(alpha: Int, tint: Int, colorWeight: Float): Int {
        val r = (Color.red(tint) * colorWeight).toInt().coerceIn(0, 255)
        val g = (Color.green(tint) * colorWeight).toInt().coerceIn(0, 255)
        val b = (Color.blue(tint) * colorWeight).toInt().coerceIn(0, 255)
        return Color.argb(alpha, r, g, b)
    }

    private fun clearFogCaches() {
        fogTintColor = null
        magazineContrastColor = null
        fogShader = null
        fogShaderW = 0
        fogShaderH = 0
        fogShaderTint = 0
        fogPaint.shader = null
    }

    /** 切歌 / 换封面：先隐藏渐变遮罩，等壁纸专辑更新后再生成。 */
    fun onWallpaperAlbumPending() {
        fogBuildGeneration++
        showFogBackground = false
        clearFogCaches()
        // 只清 fog，绝不走切歌门闩：首次开音乐锁屏 / 静默换壁纸时
        // LyricFocus 版本往往未变（或已经是新歌），再 snapshot 会把当前歌词挡到下一跳。
        if (visibility == VISIBLE) {
            invalidate()
        }
    }

    /**
     * 首次进入音乐锁屏或同曲重开：拉取 provider 现有歌词（允许同 version）。
     * 与 [onTrackMayHaveChanged] 不同，不进入切歌 WAITING。
     */
    fun ensureLyricsLoaded() {
        dataDirty = true
        lastVersionsCheck = 0
        clearTrackGate()
        lyricBootstrapUntilMs = SystemClock.elapsedRealtime() + LYRIC_BOOTSTRAP_GRACE_MS
        val mediaTitle = readCurrentMediaTitle()
        if (mediaTitle.isNotBlank()) lastSongTitle = mediaTitle
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        if (trackKey != null) lastKnownTrackKey = trackKey
        if (isMusicLockscreenActive() && isKeyguardLocked()) {
            startPolling()
            handler.post {
                updatePlayingState(force = true)
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
            }
            scheduleLyricBootstrapBurst()
        }
    }

    /** 切歌时重拉 Provider 歌词；播放中优先占歌词位直到首句或确认无词。 */
    fun onTrackMayHaveChanged() {
        refreshLyricsFromProvider(clearLineCache = true)
        // 亮屏画报 / AOD：LyricFocus 滞后，短 burst 重拉
        if (isAodLyricRefreshMode() || isMagazinePageHost()) {
            scheduleAodLyricRecoveryBurst()
        }
    }

    /** 从 LyricDataProvider 拉取并刷新显示；Provider 侧假定始终为最新。 */
    private fun refreshLyricsFromProvider(clearLineCache: Boolean) {
        dataDirty = true
        lastVersionsCheck = 0
        if (clearLineCache) {
            // 先拍快照再清屏：WAITING 用旧 JSON 拒「同内容旧曲」包
            markPreferLyricUntilResolved()
            purgeDisplayedLyrics(resetProviderSnapshot = true)
        } else {
            clearTrackGate()
        }
        val mediaTitle = readCurrentMediaTitle()
        if (mediaTitle.isNotBlank()) lastSongTitle = mediaTitle
        lastKnownTrackKey = AlbumArtResolver.getCachedTrackKey()
        if (isMusicLockscreenActive() && isKeyguardLocked()) {
            startPolling()
            handler.post {
                updatePlayingState(force = true)
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
            }
        } else {
            updateVisibilityState()
            invalidate()
        }
    }

    /** 立即清屏，避免切歌/无词时残留上一首可见文本。 */
    private fun purgeDisplayedLyrics(resetProviderSnapshot: Boolean = false) {
        cancelLineTransition()
        cachedLines = null
        cachedCtx = null
        hasLyric = false
        magazineHeldLineIndex = -1
        clearLyricDisplay()
        if (resetProviderSnapshot) {
            lastLyricJson = "{}"
            lastLyricVersion = -1
            lastLyricFdVersion = -1
        }
        updateVisibilityState()
        invalidate()
    }

    private fun clearTrackGate() {
        trackGatePhase = TrackLyricGate.Phase.IDLE
        trackGateSnapshot = null
    }

    /** 切歌后播放中优先占歌词位，等首句或确认无词。 */
    private fun markPreferLyricUntilResolved() {
        preferLyricUntilResolved = true
        trackGatePhase = TrackLyricGate.Phase.WAITING
        trackGateSnapshot = TrackLyricGate.Snapshot(
            vLyric = lastLyricVersion,
            vFd = lastLyricFdVersion,
            lyricJson = lastLyricJson,
            startedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        handler.removeCallbacks(clearPreferLyricRunnable)
        handler.postDelayed(
            clearPreferLyricRunnable,
            LyricAlbumSlotTransition.PREFER_LYRIC_WAIT_MS,
        )
    }

    private fun clearPreferLyricUntilResolved() {
        val wasPrefer = preferLyricUntilResolved
        val wasWaiting = trackGatePhase == TrackLyricGate.Phase.WAITING
        preferLyricUntilResolved = false
        handler.removeCallbacks(clearPreferLyricRunnable)
        if (trackGatePhase == TrackLyricGate.Phase.WAITING) {
            trackGatePhase = TrackLyricGate.Phase.IDLE
            // 保留 trackGateSnapshot：网络歌词常在超时后才 putlyricfd
        }
        // 画报大专辑：等词结束 / 确认无词后须通知宿主重烘焙前景专辑
        if (isMagazinePageHost() && (wasPrefer || wasWaiting)) {
            notifyMagazineAlbumSlotIfNeeded()
        }
    }

    /** @return true 表示载荷已写入 lastLyricJson 并应继续 apply；false 表示本包暂无有效行（不清屏，避免轮询打爆 UI）。 */
    private fun ingestProviderPayload(json: JSONObject, raw: String, vLyric: Int, vFd: Int): Boolean {
        val hasValid = AodLyricDisplayPolicy.hasValidLyricLines(json)
        val mediaTitle = readCurrentMediaTitle()
        val providerTitle = json.optString("title", "")
        val titleMatches = TrackLyricGate.titlesMatch(providerTitle, mediaTitle)
        val contentFromSwitch = trackGateSnapshot?.let {
            AodLyricDisplayPolicy.lyricContentChangedFromSnapshot(json, it.lyricJson)
        } ?: false
        val contentFromCurrent = AodLyricDisplayPolicy.lyricContentChangedFromSnapshot(
            json,
            lastLyricJson,
        )
        val decision = TrackLyricGate.decide(
            TrackLyricGate.Input(
                phase = trackGatePhase,
                snapshot = trackGateSnapshot,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                vLyric = vLyric,
                vFd = vFd,
                hasValidLines = hasValid,
                titleMatchesMedia = titleMatches,
                contentChangedFromSwitchSnapshot = contentFromSwitch,
                contentChangedFromCurrentDisplay = contentFromCurrent,
            ),
        )
        when (decision) {
            TrackLyricGate.Decision.IGNORE -> {
                // WAITING 时旧曲包继续空等；IDLE 且标题不齐则抑制旧词
                if (trackGatePhase == TrackLyricGate.Phase.IDLE && hasValid && !titleMatches) {
                    if (mediaTitle.isNotBlank() && providerTitle.isNotBlank()) {
                        hideStaleProviderLyric()
                    }
                }
                return false
            }
            TrackLyricGate.Decision.SHOW_ALBUM -> {
                resolveNoLyric()
                return false
            }
            TrackLyricGate.Decision.SHOW_LYRIC -> {
                if (!hasValid) return false
            }
        }
        lastLyricJson = raw
        lastLyricVersion = vLyric
        lastLyricFdVersion = vFd
        clearPreferLyricUntilResolved()
        clearTrackGate()
        dataDirty = false
        staleProviderLyricSuppressed = false
        return true
    }

    /** Provider 歌词与当前 MediaSession 不同曲时隐藏；保留 Provider 快照供新词到达后比对。 */
    private fun ensureProviderLyricMatchesMedia(json: JSONObject): Boolean {
        val mediaTitle = readCurrentMediaTitle()
        if (!AodLyricDisplayPolicy.isProviderLyricStaleForMedia(json, mediaTitle)) {
            staleProviderLyricSuppressed = false
            return true
        }
        if (!staleProviderLyricSuppressed) {
            logI(
                "suppress stale provider lyric: provider=[${json.optString("title", "").trim()}] " +
                    "media=[$mediaTitle]"
            )
        }
        staleProviderLyricSuppressed = true
        hideStaleProviderLyric()
        return false
    }

    private fun hideStaleProviderLyric() {
        if (!hasLyric && visibility == GONE && cachedLines == null && currentMainText.isEmpty()) {
            return
        }
        cancelLineTransition()
        cachedLines = null
        cachedCtx = null
        hasLyric = false
        clearLyricDisplay()
        updateVisibilityState()
    }

    private fun resolveNoLyric() {
        clearPreferLyricUntilResolved()
        clearTrackGate()
        lastLyricJson = "{}"
        cachedCtx = null
        cachedLines = null
        dataDirty = false
        if (!hasLyric && visibility == GONE) {
            // 已空屏：仍要通知画报恢复大专辑（切歌 WAITING 期间可能已烘焙无专辑壁纸）
            if (isMagazinePageHost()) {
                notifyMagazineAlbumSlotIfNeeded()
            }
            return
        }
        hasLyric = false
        clearLyricDisplay()
        updateVisibilityState()
        requestLayout()
        try {
            MediaFollowController.requestReflow()
        } catch (_: Throwable) {
        }
    }

    /** 模糊壁纸 bitmap 已更新：按歌词背后取样重算 MiBlur / 字色对比度。 */
    fun onBlurredWallpaperUpdated() {
        if (!isMusicLockscreenActive() || !allowsAlbumTintEffects()) return
        if (fogTintColor == null && sampleWallpaperBehindLyrics() == null) return
        immersiveMiBlurBlendKey = 0
        syncImmersiveMiBlur()
        if (!immersiveMiBlurActive) {
            applyImmersiveTextColors()
        }
        invalidate()
    }

    /** 壁纸专辑已应用到锁屏：后台取下半主色并生成渐变遮罩。 */
    fun onWallpaperAlbumReady(sourceAlbum: Bitmap? = null, trackKey: String? = null) {
        if (!isMusicLockscreenActive()) return
        // 画报页遮挡锁屏时 isOnKeyguard 常为 false，仍需取色 / MiBlur
        if (!allowsAlbumTintEffects()) return
        val gen = fogBuildGeneration
        val expectedKey = trackKey ?: AlbumArtResolver.getCachedTrackKey()
        val album = sourceAlbum ?: AlbumArtResolver.getCached() ?: return
        val ownsAlbumCopy = sourceAlbum != null
        val magazineHost = isMagazinePageHost()

        Thread {
            try {
                val tintPair = BlurUtils.extractLowerHalfTintColors(album)
                val tintColor = tintPair.accent
                post {
                    if (gen != fogBuildGeneration || !isMusicLockscreenActive() ||
                        !allowsAlbumTintEffects()
                    ) {
                        if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                        // pending 抬升 generation 作废了在飞取色：稳定后补一次，避免取色永久空窗
                        scheduleFogTintRecovery(abandonedGeneration = fogBuildGeneration)
                        return@post
                    }
                    if (!MagazinePageLyricVisualPolicy.shouldAcceptExtractedTint(
                            magazinePageHost = magazineHost,
                            hasExplicitSourceAlbum = ownsAlbumCopy,
                            expectedKey = expectedKey,
                            cachedTrackKey = AlbumArtResolver.getCachedTrackKey(),
                        )
                    ) {
                        if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                        scheduleFogTintRecovery(abandonedGeneration = fogBuildGeneration)
                        return@post
                    }
                    fogTintColor = tintColor
                    if (magazineHost) {
                        magazineContrastColor = tintPair.contrast
                    }
                    if (!magazineHost) {
                        LockscreenClockController.onAlbumTint(tintColor)
                    }
                    if (cfgImmersiveLyric) {
                        showFogBackground = false
                    } else {
                        showFogBackground = true
                    }
                    // 沉浸 / 普通歌词共用 MiBlur 透色；失败则走混色回退
                    if (magazineHost) {
                        // 歌词侧自行取色时回推底栏，避免与 Activity 广播不同步
                        publishMagazineSharedTint(tintPair.contrast, tintColor)
                        requestMagazineMiBlurRefresh()
                    } else {
                        immersiveMiBlurBlendKey = 0
                        syncImmersiveMiBlur()
                        if (!immersiveMiBlurActive) {
                            applyImmersiveTextColors()
                        }
                    }
                    invalidate()
                    if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                }
            } catch (_: Throwable) {
                if (ownsAlbumCopy && !album.isRecycled) album.recycle()
            }
        }.start()
    }

    /**
     * 取色线程被 generation 作废后：若一段时间无新的 pending，用当前缓存封面再取一次。
     */
    private fun scheduleFogTintRecovery(abandonedGeneration: Int) {
        handler.postDelayed({
            if (!isMusicLockscreenActive() || !allowsAlbumTintEffects()) return@postDelayed
            if (abandonedGeneration != fogBuildGeneration) return@postDelayed
            if (isFogBackgroundReady()) return@postDelayed
            // 画报优先用当前壁纸取样，避免封面主色与底栏壁纸取色分裂
            if (isMagazinePageHost()) {
                val wall = magazineWallpaperBitmap?.takeIf { !it.isRecycled }
                if (wall != null) {
                    val copy = try {
                        wall.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Throwable) {
                        null
                    }
                    if (copy != null) {
                        logI("fog tint recovery (magazine wallpaper) gen=$abandonedGeneration")
                        onWallpaperAlbumReady(copy, AlbumArtResolver.getCachedTrackKey())
                        return@postDelayed
                    }
                }
            }
            val album = AlbumArtResolver.getCached()?.takeIf { !it.isRecycled } ?: return@postDelayed
            logI("fog tint recovery after abandoned gen=$abandonedGeneration")
            onWallpaperAlbumReady(null, AlbumArtResolver.getCachedTrackKey())
        }, 150L)
    }

    private fun allowsAlbumTintEffects(): Boolean =
        MagazinePageLyricVisualPolicy.allowsAlbumTintEffects(
            magazinePageHost = isMagazinePageHost(),
            onKeyguard = HookUtils.isOnKeyguard(context),
        )

    /** 渐变遮罩是否已生成（沉浸模式仅需专辑取色用于文字染色）。 */
    fun isFogBackgroundReady(): Boolean {
        if (cfgImmersiveLyric) return fogTintColor != null
        return showFogBackground && fogTintColor != null
    }

    fun invalidateBlurBackground() {
        if (showFogBackground || fogTintColor != null) {
            onWallpaperAlbumReady()
        } else {
            onWallpaperAlbumPending()
        }
    }

    /** 关闭音乐锁屏时彻底清理歌词状态 */
    fun resetForMusicLockscreenOff() {
        cancelLineTransition()
        clearImmersiveMiBlur()
        fogBuildGeneration++
        showFogBackground = false
        clearFogCaches()
        cachedLines = null
        cachedCtx = null
        lastLyricJson = "{}"
        lastLyricVersion = -1
        lastLyricFdVersion = -1
        lastSongTitle = ""
        lastKnownTrackKey = null
        clearTrackGate()
        hasLyric = false
        clearLyricDisplay()
        alpha = 0f
        visibility = GONE
        invalidate()
    }

    /** 解锁离开锁屏：仅隐藏，保留数据供再次锁屏恢复 */
    fun onLeftKeyguard() {
        cancelLineTransition()
        clearImmersiveMiBlur()
        animate().cancel()
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        alpha = 0f
        visibility = GONE
    }

    /** 重新进入锁屏：按当前状态刷新可见性，并强制重拉歌词（解锁期间切歌） */
    fun onKeyguardShown() {
        dataDirty = true
        lastVersionsCheck = 0
        if (isMusicLockscreenActive()) {
            startPolling()
            handler.post { readAndUpdate() }
        }
        updateVisibilityState()
    }

    fun refreshVisibility() {
        updateVisibilityState()
    }

    /**
     * 媒体控件「显示歌词」开关：同步本地配置并走专辑↔歌词槽位过渡。
     * 勿再紧跟 [MusicLockscreenManager.showAlbumOverlay]，否则会 snap 掉淡入淡出。
     */
    fun onShowLyricToggled(show: Boolean) {
        cfgShowLyric = show
        updateVisibilityState()
    }

    // ============================================================
    // 生命周期
    // ============================================================
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerLyricObserver()
        registerConfigObserver()
        registerAodScreenReceiver()
        applyLyricConfig()
        startPolling()
        refreshNow()
        if (isMagazinePageHost()) {
            scheduleLyricBootstrapBurst()
            requestMagazineMiBlurRefresh()
        }
    }

    override fun onDetachedFromWindow() {
        magazineMiBlurRefreshGen++
        magazineMiBlurRefreshInFlight = false
        magazineMiBlurRevealed = false
        magazinePinnedBottomYPx = 0
        super.onDetachedFromWindow()
        stopPolling()
        unregisterAodScreenReceiver()
        unregisterLyricObserver()
        unregisterConfigObserver()
    }

    /** 内部改可见性时跳过 [setVisibility] 副作用，避免递归刷新。 */
    private var suppressingVisibilitySideEffects = false

    override fun setVisibility(visibility: Int) {
        val previous = getVisibility()
        val runSideEffects = LyricAlbumSlotTransition.shouldRunSetVisibilitySideEffects(
            previousVisibility = previous,
            newVisibility = visibility,
            suppressingSideEffects = suppressingVisibilitySideEffects,
        )
        super.setVisibility(visibility)
        if (!runSideEffects) return
        when (visibility) {
            VISIBLE -> {
                dataDirty = true
                lastVersionsCheck = 0
                startPolling()
                // 延迟刷新，避免在 setVisibility 调用栈内再入 updateVisibilityState
                handler.post {
                    if (getVisibility() != VISIBLE) return@post
                    try {
                        refreshNow()
                        syncImmersiveMiBlur()
                    } catch (t: Throwable) {
                        logE("deferred refresh after visible failed", t)
                    }
                }
            }
            GONE -> {
                // 音乐锁屏 + 锁屏/AOD 时保持轮询，否则切歌后歌词不会刷新
                if (!isMusicLockscreenActive() || !isKeyguardLocked()) {
                    stopPolling()
                }
                alpha = 0f
                clearImmersiveMiBlur()
            }
            // INVISIBLE：等 MediaFollow 定位，保持轮询以便 AOD 切歌仍能刷新
            else -> Unit
        }
    }

    /** 内部静默改可见性（不触发 refreshNow），打断 VISIBLE 递归。 */
    private fun setOverlayVisibilityQuiet(visibility: Int) {
        if (getVisibility() == visibility) return
        suppressingVisibilitySideEffects = true
        try {
            setVisibility(visibility)
        } finally {
            suppressingVisibilitySideEffects = false
        }
    }

    // ============================================================
    // ContentObserver
    // ============================================================
    private fun registerLyricObserver() {
        if (lyricObserverRegistered) return
        try {
            val uri = Uri.parse(PROVIDER_URI)
            lyricObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    dataDirty = true
                    handler.post {
                        readAndUpdate()
                        finalizeLyricDisplayAfterContentUpdate()
                    }
                }
            }
            context.contentResolver.registerContentObserver(uri, true, lyricObserver!!)
            lyricObserverRegistered = true
        } catch (e: Throwable) {
            logE("registerLyricObserver error", e)
        }
    }

    private fun unregisterLyricObserver() {
        if (!lyricObserverRegistered) return
        try {
            lyricObserver?.let { context.contentResolver.unregisterContentObserver(it) }
            lyricObserver = null
            lyricObserverRegistered = false
        } catch (_: Throwable) {
        }
    }

    private fun registerConfigObserver() {
        if (configObserverRegistered) return
        try {
            val uri = Uri.parse(CONFIG_URI)
            configObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    applyLyricConfig()
                }
            }
            context.contentResolver.registerContentObserver(uri, true, configObserver!!)
            configObserverRegistered = true
        } catch (e: Throwable) {
            logE("registerConfigObserver error", e)
        }
    }

    private fun unregisterConfigObserver() {
        if (!configObserverRegistered) return
        try {
            configObserver?.let { context.contentResolver.unregisterContentObserver(it) }
            configObserver = null
            configObserverRegistered = false
        } catch (_: Throwable) {
        }
    }

    private fun applyLyricConfig() {
        try {
            if (isMagazinePageHost()) {
                applyMagazinePageLyricConfig()
                return
            }
            // 与 BigAlbum 共用 ConfigReader：先失效，避免 showAlbumOverlay 读到旧的
            // immersive_album / immersive_lyric，在「沉浸歌词大专辑→沉浸专辑」时误把方形封面又画出来盖住模糊底。
            ConfigReader.invalidate()
            val uri = Uri.parse(CONFIG_URI)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idxEnabled = cursor.getColumnIndex("lyric_enabled")
                val idxShow = cursor.getColumnIndex("show_lyric")
                val idxSize = cursor.getColumnIndex("lyric_size")
                val idxSwap = cursor.getColumnIndex("swap_lyric")
                val idxWidth = cursor.getColumnIndex("lyric_width")
                val idxBgAnchorY = cursor.getColumnIndex("lyric_bg_anchor_y")
                val idxImmersive = cursor.getColumnIndex("immersive_lyric")
                val idxHideBg = cursor.getColumnIndex("lyric_hide_background")
                val idxAlign = cursor.getColumnIndex("lyric_align")
                val idxTransition = cursor.getColumnIndex("lyric_transition")
                val idxStack = cursor.getColumnIndex("immersive_lyric_stack")

                if (idxEnabled >= 0) {
                    cfgLyricEnabled = cursor.getInt(idxEnabled) != 0
                }
                if (idxShow >= 0) {
                    val newShow = cursor.getInt(idxShow) != 0
                    val showChanged = newShow != cfgShowLyric
                    cfgShowLyric = newShow
                    // 边沿变化，或进屏后配置重读且默认已开：都要重拉，避免「要再开关一次」
                    if (LyricDisplayPolicy.shouldForceLyricBootstrapOnEnter(
                            cfgLyricEnabled,
                            newShow,
                            isMusicLockscreenActive(),
                        ) && (showChanged || !hasLyric)
                    ) {
                        dataDirty = true
                        lastVersionsCheck = 0
                        handler.post {
                            readAndUpdate()
                            finalizeLyricDisplayAfterContentUpdate()
                            try {
                                MediaFollowController.requestReflow()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
                if (idxSize >= 0) cfgLyricSize = cursor.getFloat(idxSize)
                var swapChanged = false
                if (idxSwap >= 0) {
                    val newSwap = cursor.getInt(idxSwap) != 0
                    swapChanged = newSwap != cfgSwapLyric
                    cfgSwapLyric = newSwap
                }
                var positionChanged = false
                var styleChanged = false
                if (idxWidth >= 0) {
                    val newWidth = cursor.getFloat(idxWidth)
                    if (newWidth != cfgLyricWidth) positionChanged = true
                    cfgLyricWidth = newWidth
                }
                if (idxBgAnchorY >= 0) {
                    val newAnchor = cursor.getFloat(idxBgAnchorY)
                    if (newAnchor != cfgLyricBgAnchorY) {
                        positionChanged = true
                        lockscreenLyricAnchorPercent = Float.NaN
                    }
                    cfgLyricBgAnchorY = newAnchor
                }
                if (idxImmersive >= 0) {
                    val newImmersive = cursor.getInt(idxImmersive) == 1
                    if (newImmersive != cfgImmersiveLyric) {
                        positionChanged = true
                        styleChanged = true
                    }
                    cfgImmersiveLyric = newImmersive
                }
                if (idxHideBg >= 0) {
                    cfgLyricHideBackground = cursor.getInt(idxHideBg) == 1
                    styleChanged = true
                }
                if (idxAlign >= 0) {
                    val newAlign = cursor.getString(idxAlign) ?: "left"
                    if (newAlign != cfgLyricAlign) styleChanged = true
                    cfgLyricAlign = newAlign
                }
                if (idxTransition >= 0) {
                    cfgLyricTransition = LyricLineTransitionPolicy.normalize(
                        cursor.getString(idxTransition)
                    )
                }
                if (idxStack >= 0) {
                    val newStack = cursor.getInt(idxStack) == 1
                    if (newStack != cfgImmersiveLyricStack) styleChanged = true
                    cfgImmersiveLyricStack = newStack
                }
                if (positionChanged || lockscreenLyricAnchorStale()) {
                    lockscreenLyricAnchorPercent = Float.NaN
                    MediaFollowController.clearLyricPinAndReflow()
                }

                cursor.close()
                applyLyricStyle()
                if (swapChanged) {
                    applySwapIfNeeded()
                }
                if (styleChanged) {
                    mainStaticLayout = null
                    immersiveSecondStaticLayout = null
                    invalidate()
                }
                updateVisibilityState()
                // 歌词占槽时由 applyAlbumSlotVisibility 负责淡出专辑；此处再 show 会把 alpha snap 回 1
                if (LyricAlbumSlotTransition.shouldForceShowAlbumAfterVisibilityUpdate(
                        lyricPriorityOverAlbum = isLyricPriorityOverAlbum(),
                    )
                ) {
                    MusicLockscreenManager.showAlbumOverlay()
                }
                KeepScreenController.sync()
                LockscreenClockController.sync()
            }
        } catch (e: Throwable) {
            logE("applyLyricConfig error", e)
        }
    }

    /** 画报页：读 ModuleConfig.magazine_*，与普通锁屏 ConfigReader 档案隔离。 */
    private fun applyMagazinePageLyricConfig() {
        try {
            try {
                com.leowalk.musiclockscreen.ModuleConfig.init(context)
            } catch (_: Throwable) {
            }
            val cfg = com.leowalk.musiclockscreen.ModuleConfig
            cfgLyricEnabled = cfg.lyricEnabled
            val newShow = cfg.magazineShowLyric
            val showChanged = newShow != cfgShowLyric
            cfgShowLyric = newShow
            cfgLyricSize = cfg.magazineLyricSize
            val newSwap = cfg.magazineSwapLyric
            val swapChanged = newSwap != cfgSwapLyric
            cfgSwapLyric = newSwap
            var positionChanged = false
            var styleChanged = false
            val newWidth = cfg.magazineLyricWidth
            if (newWidth != cfgLyricWidth) positionChanged = true
            cfgLyricWidth = newWidth
            val newAnchor = cfg.magazineLyricBgAnchorY
            if (newAnchor != cfgLyricBgAnchorY) positionChanged = true
            cfgLyricBgAnchorY = newAnchor
            val newAlbumSize = cfg.magazineAlbumSize
            if (newAlbumSize != cfgMagazineAlbumSize) positionChanged = true
            cfgMagazineAlbumSize = newAlbumSize
            val newAlbumAnchor = cfg.magazineAlbumAnchorY
            if (newAlbumAnchor != cfgMagazineAlbumAnchorY) positionChanged = true
            cfgMagazineAlbumAnchorY = newAlbumAnchor
            val newImmersive = cfg.magazineImmersiveLyric
            if (newImmersive != cfgImmersiveLyric) {
                positionChanged = true
                styleChanged = true
            }
            cfgImmersiveLyric = newImmersive
            cfgLyricHideBackground = cfg.magazineLyricHideBackground
            styleChanged = true
            val newAlign = cfg.magazineLyricAlign
            if (newAlign != cfgLyricAlign) styleChanged = true
            cfgLyricAlign = newAlign
            cfgLyricTransition = LyricLineTransitionPolicy.normalize(cfg.magazineLyricTransition)
            val newStack = cfg.magazineImmersiveLyricStack
            if (newStack != cfgImmersiveLyricStack) styleChanged = true
            cfgImmersiveLyricStack = newStack

            if (LyricDisplayPolicy.shouldForceLyricBootstrapOnEnter(
                    cfgLyricEnabled,
                    newShow,
                    isMusicLockscreenActive(),
                ) && (showChanged || !hasLyric)
            ) {
                dataDirty = true
                lastVersionsCheck = 0
                handler.post {
                    readAndUpdate()
                    finalizeLyricDisplayAfterContentUpdate()
                }
            }
            applyLyricStyle()
            if (swapChanged) applySwapIfNeeded()
            if (styleChanged || positionChanged) {
                // 锚点/沉浸切换后重钉底边，禁止保留旧 topMargin
                magazinePinnedBottomYPx = 0
                magazinePinnedAnchorPercent = Float.NaN
                mainStaticLayout = null
                immersiveSecondStaticLayout = null
                // 高度不变时也要立刻按新锚点落位（见 resizeKeepingBottom）
                val h = when {
                    height > 0 -> height
                    measuredHeight > 0 -> measuredHeight
                    layoutParams?.height?.let { it > 0 } == true -> layoutParams!!.height
                    else -> 0
                }
                if (h > 0) {
                    resizeKeepingBottom(h)
                } else {
                    requestLayout()
                }
                invalidate()
            }
            updateVisibilityState()
        } catch (e: Throwable) {
            logE("applyMagazinePageLyricConfig error", e)
        }
    }

    private fun applyLyricStyle() {
        cancelLineTransition()
        cancelStackAnimator(commitPending = true)
        stackScrollOffset = 0f
        stackAnimProgress = 1f
        stackViewportHeightPx = 0
        lastStackLineIndex = -1
        pendingStackTriplet = null
        pendingStackIndex = -1
        val density = resources.displayMetrics.density

        val useMiSans = MagazinePageLyricVisualPolicy.shouldUseMiSansTypeface(
            magazinePageHost = isMagazinePageHost(),
            immersiveLyric = cfgImmersiveLyric,
        )
        if (cfgImmersiveLyric) {
            showFogBackground = false
            mainPaint.textSize = immersiveLyricSizeSp * density
            secondPaint.textSize = immersiveLyricSizeSp * immersiveSecondSizeRatio * density
            applyImmersiveTypeface()
        } else {
            mainPaint.textSize = cfgLyricSize * density
            secondPaint.textSize = cfgLyricSize * 0.8f * density
            if (useMiSans) {
                applyImmersiveTypeface()
            } else {
                mainPaint.typeface = Typeface.DEFAULT_BOLD
                mainPaint.isFakeBoldText = false
                secondPaint.typeface = Typeface.DEFAULT
                secondPaint.isFakeBoldText = false
            }
        }
        // 沉浸 / 普通歌词共用系统 MiBlur 白中透色
        applyImmersiveTextColors()
        syncImmersiveMiBlur()

        val lp = layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            val preserveTop = MagazinePageLyricVisualPolicy.shouldPreservePinnedTopMargin(
                isMagazinePageHost(),
            ) && magazinePinnedBottomYPx > 0
            val pinnedTop = if (preserveTop) lp.topMargin else 0
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.leftMargin = 0
            lp.topMargin = pinnedTop
            lp.rightMargin = 0
            lp.bottomMargin = 0
            // 样式变更后恢复 WRAP，由 onMeasure 自适应
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams = lp
        }

        mainStaticLayout = null
        immersiveSecondStaticLayout = null
        stackPrevLayout = null
        stackCurrentLayout = null
        stackCurrentSecondaryLayout = null
        stackNextLayout = null
        requestLayout()
        if (!isMagazinePageHost()) {
            MediaFollowController.requestReflow()
        }
        invalidate()
    }

    /**
     * 歌词文字染色：锁屏 / 画报优先真·MiBlur；画报浅色底用共用高对比样式。
     */
    private fun applyImmersiveTextColors() {
        val bgRef = contrastBackgroundColor()
        if (immersiveMiBlurActive) {
            if (isMagazinePageHost()) {
                val onLight = immersiveMiBlurOnLightBg
                val ink = MagazinePageTextStylePolicy.glyphPrimaryRgb(onLight)
                mainPaint.color = ink
                secondPaint.color = MagazinePageTextStylePolicy.glyphSecondaryArgb(onLight)
                mainPaint.alpha = 255
                secondPaint.alpha = 255
                val sh = MagazinePageTextStylePolicy.glyphShadow(onLight)
                mainPaint.setShadowLayer(sh.radius, 0f, sh.dy, sh.colorArgb)
                secondPaint.setShadowLayer(sh.radius * 0.75f, 0f, sh.dy, sh.colorArgb)
                return
            }
            if (immersiveMiBlurOnLightBg) {
                val ink = Color.rgb(28, 28, 30)
                mainPaint.color = ink
                secondPaint.color = ink
                mainPaint.alpha = 255
                secondPaint.alpha = 255
                mainPaint.setShadowLayer(10f, 0f, 2f, Color.argb(90, 255, 255, 255))
                secondPaint.setShadowLayer(8f, 0f, 2f, Color.argb(70, 255, 255, 255))
            } else {
                mainPaint.color = Color.WHITE
                secondPaint.color = Color.WHITE
                mainPaint.alpha = 255
                secondPaint.alpha = 255
                mainPaint.setShadowLayer(14f, 0f, 5f, Color.argb(220, 0, 0, 0))
                secondPaint.setShadowLayer(10f, 0f, 3f, Color.argb(190, 0, 0, 0))
            }
            return
        }
        applyMagazineAlbumTintPaint(bgRef)
    }

    private fun applyMagazineAlbumTintPaint(bgRef: Int) {
        val tint = boostAlbumTint(fogTintColor ?: bgRef)
        if (isMagazinePageHost()) {
            val onLight = MagazinePageTextStylePolicy.isLightBackground(bgRef)
            val mainColor = MagazinePageTextStylePolicy.fallbackReadableRgb(onLight, tint)
            mainPaint.color = mainColor
            secondPaint.color = MagazinePageTextStylePolicy.fallbackSecondaryArgb(onLight, mainColor)
            val sh = MagazinePageTextStylePolicy.fallbackShadow(onLight)
            mainPaint.setShadowLayer(sh.radius, 0f, sh.dy, sh.colorArgb)
            secondPaint.setShadowLayer(sh.radius * 0.75f, 0f, sh.dy, sh.colorArgb)
            return
        }
        val onLight = isNearWhiteBackground(bgRef)
        val mainColor = if (onLight) {
            blendTextColor(Color.rgb(32, 32, 34), tint, 0.28f)
        } else {
            blendTextColor(Color.WHITE, tint, immersiveTintWeight)
        }
        mainPaint.color = mainColor
        secondPaint.color = Color.argb(
            if (onLight) 200 else 160,
            Color.red(mainColor),
            Color.green(mainColor),
            Color.blue(mainColor)
        )
        if (onLight) {
            mainPaint.setShadowLayer(10f, 0f, 2f, Color.argb(100, 255, 255, 255))
            secondPaint.setShadowLayer(8f, 0f, 2f, Color.argb(80, 255, 255, 255))
        } else {
            mainPaint.setShadowLayer(10f, 0f, 3f, Color.argb(230, 0, 0, 0))
            secondPaint.setShadowLayer(7f, 1f, 3f, Color.argb(210, 0, 0, 0))
        }
    }

    private fun syncImmersiveMiBlur() {
        if (!isMusicLockscreenActive() || visibility == GONE) {
            clearImmersiveMiBlur()
            applyImmersiveTextColors()
            return
        }
        if (!HyperMiBlurHelper.isSupported(context)) {
            clearImmersiveMiBlur()
            applyImmersiveTextColors()
            return
        }
        // 对比度看「歌词背后的壁纸」，透色仍可用专辑色
        val bgRef = contrastBackgroundColor()
        val tint = boostAlbumTint(fogTintColor ?: bgRef)
        val bgLum = colorLuminance(bgRef)
        val onLight: Boolean
        val blend: Int
        val primary: Int
        val over: Int
        val blendAlpha: Int
        val labAlpha: Int
        if (isMagazinePageHost()) {
            // 画报：与歌曲信息共用浅色底判定与 MiBlur 参数
            onLight = MagazinePageTextStylePolicy.isLightBackground(bgRef)
            blend = MagazinePageTextStylePolicy.miBlurBlendRgb(onLight, tint)
            primary = MagazinePageTextStylePolicy.miBlurPrimaryRgb(onLight)
            over = MagazinePageTextStylePolicy.miBlurOverArgb(onLight)
            val alphas = MagazinePageTextStylePolicy.miBlurAlphas(onLight)
            blendAlpha = alphas.blendAlpha
            labAlpha = alphas.labAlpha
        } else {
            // 锁屏：仅近白转深色
            onLight = isNearWhiteBackground(bgRef)
            if (onLight) {
                blend = blendTextColor(Color.rgb(24, 24, 26), tint, 0.40f)
                primary = Color.rgb(22, 22, 24)
                over = Color.argb(160, 0, 0, 0)
                blendAlpha = 200
                labAlpha = 230
            } else {
                blend = blendTextColor(Color.WHITE, tint, 0.42f)
                primary = Color.WHITE
                over = Color.argb(130, 255, 255, 255)
                blendAlpha = 180
                labAlpha = 170
            }
        }
        val modeBit = if (cfgImmersiveLyric) 0x10 else 0x20
        val magazineBit = if (isMagazinePageHost()) 0x40 else 0
        val blendKey = blend xor bgRef xor (if (onLight) 0x91 else 0x92) xor modeBit xor magazineBit xor
            (if (visibility == VISIBLE) 1 else 0)
        if (immersiveMiBlurActive &&
            blendKey == immersiveMiBlurBlendKey &&
            onLight == immersiveMiBlurOnLightBg
        ) {
            return
        }

        val ok = HyperMiBlurHelper.applyTextBlend(
            view = this,
            blendColor = blend,
            primaryColor = primary,
            colorDark = onLight,
            enablePassBlurOnSelf = if (isMagazinePageHost()) {
                MagazinePageMiBlurPolicy.enablePassWindowBlur()
            } else {
                true
            },
            sampleSiblingContent = if (isMagazinePageHost()) {
                MagazinePageMiBlurPolicy.sampleSiblingContent()
            } else {
                false
            },
            passBlurRadius = (45f * resources.displayMetrics.density).toInt().coerceIn(28, 90),
            blendAlpha = blendAlpha,
            labAlpha = labAlpha,
            overColor = over
        )
        if (ok) {
            immersiveMiBlurActive = true
            immersiveMiBlurOnLightBg = onLight
            immersiveMiBlurBlendKey = blendKey
            if (!cfgImmersiveLyric) {
                showFogBackground = false
            }
            applyImmersiveTextColors()
            logI(
                "lyric MiBlur applied immersive=$cfgImmersiveLyric nearWhite=$onLight " +
                    "magazine=${isMagazinePageHost()} bgLum=${"%.2f".format(bgLum)} " +
                    "bg=#${Integer.toHexString(bgRef)} blend=#${Integer.toHexString(blend)}"
            )
        } else {
            clearImmersiveMiBlur()
            if (!cfgImmersiveLyric) {
                showFogBackground = fogTintColor != null && !cfgLyricHideBackground
            }
            applyImmersiveTextColors()
            logI("lyric MiBlur unavailable, fallback album tint")
        }
    }

    private fun clearImmersiveMiBlur() {
        if (immersiveMiBlurActive || immersiveMiBlurBlendKey != 0) {
            HyperMiBlurHelper.clearTextBlend(this)
        }
        immersiveMiBlurActive = false
        immersiveMiBlurOnLightBg = false
        immersiveMiBlurBlendKey = 0
    }

    private fun colorLuminance(color: Int): Float {
        return (0.2126f * Color.red(color) +
            0.7152f * Color.green(color) +
            0.0722f * Color.blue(color)) / 255f
    }

    /** 仅接近纯白/浅灰白才切深色字；浅彩底不算。 */
    private fun isNearWhiteBackground(color: Int): Boolean {
        val lum = colorLuminance(color)
        if (lum < 0.88f) return false
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1] < 0.18f
    }

    /**
     * 歌词区域背后的壁纸代表色（对比度判断用）。
     * 画报页优先用共享 contrast（亮度），accent 只用于染色混色。
     * 锁屏沉浸专辑时 Monet 浅色底与专辑主色常不一致，仍优先采样歌词背后。
     */
    private fun contrastBackgroundColor(): Int {
        if (isMagazinePageHost()) {
            return MagazinePageLyricVisualPolicy.magazineContrastBackground(
                sharedContrast = magazineContrastColor,
                fogTint = fogTintColor,
                sampledBehindLyrics = sampleWallpaperBehindLyrics(),
                defaultColor = Color.rgb(40, 40, 44),
            )
        }
        sampleWallpaperBehindLyrics()?.let { return it }
        fogTintColor?.let { return it }
        return Color.WHITE
    }

    private fun sampleWallpaperBehindLyrics(): Int? {
        val bmp = if (isMagazinePageHost()) {
            magazineWallpaperBitmap
        } else {
            MusicLockscreenManager.blurredWallpaperBitmap
        }
        if (bmp == null || bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) return null
        return try {
            val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
            val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val centerY = if (height > 0) {
                (loc[1] + height / 2).coerceIn(0, screenH - 1)
            } else {
                ((cfgLyricBgAnchorY / 100f) * screenH).toInt().coerceIn(0, screenH - 1)
            }
            val centerX = if (width > 0) {
                (loc[0] + width / 2).coerceIn(0, screenW - 1)
            } else {
                screenW / 2
            }
            val y = ((centerY.toFloat() / screenH) * bmp.height).toInt()
                .coerceIn(0, bmp.height - 1)
            val x = ((centerX.toFloat() / screenW) * bmp.width).toInt()
                .coerceIn(0, bmp.width - 1)
            val band = (bmp.height / 36).coerceAtLeast(2)
            val y0 = (y - band).coerceAtLeast(0)
            val y1 = (y + band).coerceAtMost(bmp.height - 1)
            val x0 = (x - bmp.width / 8).coerceAtLeast(0)
            val x1 = (x + bmp.width / 8).coerceAtMost(bmp.width - 1)
            val stepX = ((x1 - x0) / 12).coerceAtLeast(1)
            val stepY = ((y1 - y0) / 6).coerceAtLeast(1)
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var n = 0
            var yy = y0
            while (yy <= y1) {
                var xx = x0
                while (xx <= x1) {
                    val p = bmp.getPixel(xx, yy)
                    rSum += Color.red(p)
                    gSum += Color.green(p)
                    bSum += Color.blue(p)
                    n++
                    xx += stepX
                }
                yy += stepY
            }
            if (n == 0) null else Color.rgb((rSum / n).toInt(), (gSum / n).toInt(), (bSum / n).toInt())
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyImmersiveTypeface() {
        mainPaint.typeface = resolveMiSansTypeface(bold = true)
        mainPaint.isFakeBoldText = false
        secondPaint.typeface = resolveMiSansTypeface(bold = false)
        secondPaint.isFakeBoldText = false
    }

    /** 提高专辑色饱和度，混进白字后更易察觉 */
    private fun boostAlbumTint(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.35f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.12f).coerceIn(0.35f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun resolveMiSansTypeface(bold: Boolean): Typeface {
        if (bold) {
            cachedMiSansBold?.let { return it }
        } else {
            cachedMiSansMedium?.let { return it }
        }
        val paths = if (bold) {
            arrayOf(
                "/system/fonts/MiSans-Heavy.ttf",
                "/system/fonts/MiSans-Bold.ttf",
                "/system/fonts/MiSans-Semibold.ttf",
                "/system/fonts/MiSans-Demibold.ttf",
                "/product/fonts/MiSans-Bold.ttf",
                "/product/fonts/MiSans-Heavy.ttf",
                "/system/fonts/MiSansVF.ttf",
                "/system/fonts/MiSansVF.otf",
            )
        } else {
            arrayOf(
                "/system/fonts/MiSans-Medium.ttf",
                "/system/fonts/MiSans-Regular.ttf",
                "/system/fonts/MiSans-Demibold.ttf",
                "/product/fonts/MiSans-Regular.ttf",
                "/product/fonts/MiSans-Medium.ttf",
                "/system/fonts/MiSansVF.ttf",
            )
        }
        for (path in paths) {
            try {
                val file = java.io.File(path)
                if (!file.exists()) continue
                val tf = Typeface.createFromFile(file)
                if (bold) cachedMiSansBold = tf else cachedMiSansMedium = tf
                return tf
            } catch (_: Throwable) {
            }
        }
        // 画报 app 进程偶发读不到字体文件时，再靠系统族名
        val families = if (bold) {
            arrayOf(
                "misans-bold",
                "misans-heavy",
                "MiSans",
                "mipro-bold",
                "mipro-heavy",
                "sans-serif-black",
            )
        } else {
            arrayOf(
                "misans-medium",
                "misans-regular",
                "MiSans",
                "mipro-medium",
                "sans-serif-medium",
            )
        }
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        val defaultFace = Typeface.create(Typeface.DEFAULT, style)
        for (name in families) {
            try {
                val tf = Typeface.create(name, style)
                if (tf != null && tf !== Typeface.DEFAULT && tf !== defaultFace) {
                    if (bold) cachedMiSansBold = tf else cachedMiSansMedium = tf
                    return tf
                }
            } catch (_: Throwable) {
            }
        }
        if (bold) cachedMiSansBold = defaultFace else cachedMiSansMedium = defaultFace
        return defaultFace
    }

    private fun blendTextColor(base: Int, tint: Int, weight: Float): Int {
        val w = weight.coerceIn(0f, 1f)
        val inv = 1f - w
        return Color.rgb(
            (Color.red(base) * inv + Color.red(tint) * w).toInt().coerceIn(0, 255),
            (Color.green(base) * inv + Color.green(tint) * w).toInt().coerceIn(0, 255),
            (Color.blue(base) * inv + Color.blue(tint) * w).toInt().coerceIn(0, 255)
        )
    }

    private fun isMusicLockscreenActive(): Boolean {
        if (isMagazinePageHost()) return true
        return MusicLockscreenManager.isShowing || WallpaperController.isShowing()
    }

    private fun hasDisplayableText(): Boolean {
        return currentMainText.isNotBlank() ||
            (hasSecondLine && currentSecondText.isNotBlank())
    }

    private fun shouldDisplayLyric(): Boolean {
        if (isMagazinePageHost()) {
            return MagazinePageLyricHostPolicy.shouldDisplay(
                lyricEnabled = cfgLyricEnabled,
                showLyric = cfgShowLyric,
                hasLyric = hasLyric,
                playbackOk = isPlaybackOkForLyric(),
                hasDisplayableText = hasDisplayableText(),
            )
        }
        return isMusicLockscreenActive() &&
            LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, cfgShowLyric) &&
            isKeyguardLocked() &&
            !isBouncerShowing() &&
            !shadeOpen &&
            LockscreenNotificationController.shouldShowKeyguardOverlays() &&
            hasLyric &&
            isPlaybackOkForLyric() &&
            hasDisplayableText()
    }

    /** 含电源切换 sticky / AOD 钉住：有词则保持，避免锁屏↔AOD 闪灭。 */
    private fun shouldShowLyricOverlay(): Boolean {
        return shouldDisplayLyric() || isAodVisibilityPinActive()
    }

    private fun isAodVisibilityPinActive(): Boolean {
        return LyricAlbumSlotTransition.shouldPinLyricVisibility(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            pinUntilMs = aodVisibilityPinUntilMs,
            showLyricEnabled = LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, cfgShowLyric),
            musicLockscreenActive = isMusicLockscreenActive(),
            onKeyguard = isKeyguardLocked(),
            hasLyricData = hasLyric,
            hasDisplayableText = hasDisplayableText(),
            confirmedPaused = confirmedPaused,
            inScreenPowerTransition = KeyguardSleepTransition.isInLinkageAnimWindow(),
        )
    }

    private fun armAodVisibilityPin() {
        if (!hasLyric || !hasDisplayableText()) return
        if (!LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, cfgShowLyric)) return
        val now = SystemClock.elapsedRealtime()
        aodVisibilityPinUntilMs = now + LyricAlbumSlotTransition.AOD_VISIBILITY_PIN_MS
        // 延长播放 hold，覆盖联动尾抖；联动期内不信假暂停
        if (!confirmedPaused || KeyguardSleepTransition.isInLinkageAnimWindow()) {
            playbackHoldUntilMs = maxOf(
                playbackHoldUntilMs,
                now + LyricAlbumSlotTransition.PLAYBACK_HOLD_MS,
            )
        }
        KeyguardSleepTransition.onScreenPowerEvent()
    }

    private fun isPlaybackOkForLyric(): Boolean {
        return AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
            isPlaying = isPlaying,
            screenInteractive = HookUtils.isScreenInteractive(context),
            musicLockscreenActive = isMusicLockscreenActive(),
            onKeyguard = isKeyguardLocked(),
            mediaPlaybackActive = ConfigReader.mediaPlaybackActive(context),
            hasLyricData = hasLyric,
            hasDisplayableText = hasDisplayableText(),
            confirmedPaused = confirmedPaused,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            playbackHoldUntilMs = playbackHoldUntilMs,
            inScreenPowerTransition = KeyguardSleepTransition.isInLinkageAnimWindow() ||
                isAodVisibilityPinActive(),
        )
    }

    private fun isAodLyricRefreshMode(): Boolean {
        return AodLyricDisplayPolicy.isAodLyricRefreshMode(
            HookUtils.isScreenInteractive(context),
            isKeyguardLocked(),
        )
    }

    private fun finalizeLyricDisplayAfterContentUpdate() {
        if (!hasLyric || !hasDisplayableText()) return
        updateVisibilityState()
        if (!shouldShowLyricOverlay()) return
        if (visibility == VISIBLE) return
        scheduleRevealAfterLayout()
    }

    private fun isLyricBootstrapGraceActive(): Boolean {
        return SystemClock.elapsedRealtime() < lyricBootstrapUntilMs
    }

    /** 首次进入音乐锁屏：Session / Provider 信号滞后，短 burst 重拉避免等解锁再锁屏。 */
    private fun scheduleLyricBootstrapBurst() {
        if (!HookUtils.isScreenInteractive(context)) return
        if (!isMusicLockscreenActive() || !isKeyguardLocked()) return
        val generation = ++lyricBootstrapBurstGeneration
        val delays = longArrayOf(50L, 150L, 400L, 800L, 1500L)
        for (delayMs in delays) {
            handler.postDelayed({
                if (generation != lyricBootstrapBurstGeneration) return@postDelayed
                if (!isLyricBootstrapGraceActive()) return@postDelayed
                if (!isMusicLockscreenActive() || !isKeyguardLocked()) return@postDelayed
                updatePlayingState(force = true)
                dataDirty = true
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
            }, delayMs)
        }
    }

    /** AOD 切歌后 LyricFocus / Session 信号滞后，短 burst 重拉避免等亮屏。 */
    fun scheduleAodLyricRecoveryBurst() {
        if (!isMusicLockscreenActive() || !isKeyguardLocked()) return
        val generation = ++aodRecoveryBurstGeneration
        // 次数/间隔收敛：过多同步 CP 调用会堵 SystemUI 主线程
        val delays = longArrayOf(200L, 800L, 2000L)
        for (delayMs in delays) {
            handler.postDelayed({
                if (generation != aodRecoveryBurstGeneration) return@postDelayed
                if (!isMusicLockscreenActive() || !isKeyguardLocked()) return@postDelayed
                try {
                    dataDirty = true
                    readAndUpdate()
                    finalizeLyricDisplayAfterContentUpdate()
                } catch (t: Throwable) {
                    logE("aod recovery burst failed", t)
                }
            }, delayMs)
        }
    }

    private fun registerAodScreenReceiver() {
        if (aodScreenReceiver != null) return
        val app = context.applicationContext
        aodScreenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                // 必须极轻：重活一律 post，否则 SCREEN_OFF 会 ANR 拖死 SystemUI
                val action = intent?.action ?: return
                handler.post {
                    try {
                        when (action) {
                            Intent.ACTION_SCREEN_OFF -> onScreenPoweredOffForAod()
                            Intent.ACTION_SCREEN_ON -> onScreenPoweredOnFromAod()
                        }
                    } catch (t: Throwable) {
                        logE("screen power handler failed", t)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        app.registerReceiver(aodScreenReceiver, filter)
    }

    private fun unregisterAodScreenReceiver() {
        val app = context.applicationContext
        val receiver = aodScreenReceiver ?: return
        try {
            app.unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
        aodScreenReceiver = null
    }

    private fun onScreenPoweredOffForAod() {
        if (!isMusicLockscreenActive() || !isKeyguardLocked()) return
        armAodVisibilityPin()
        // 已有歌词时立刻钉住可见，禁止先淡出（静默设 visibility，避免递归刷新）
        if (hasLyric && hasDisplayableText() &&
            (visibility == VISIBLE || visibility == INVISIBLE)
        ) {
            animate().cancel()
            setOverlayVisibilityQuiet(VISIBLE)
            alpha = 1f
            invalidate()
        }
        dataDirty = true
        startPolling()
        handler.post {
            try {
                updatePlayingState(force = true)
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
                if (isAodVisibilityPinActive() && hasDisplayableText()) {
                    animate().cancel()
                    setOverlayVisibilityQuiet(VISIBLE)
                    alpha = 1f
                }
            } catch (t: Throwable) {
                logE("aod screen-off refresh failed", t)
            }
        }
        // 恢复 burst 降频，避免主线程被 ContentProvider 打满
        scheduleAodLyricRecoveryBurst()
    }

    private fun onScreenPoweredOnFromAod() {
        if (!isMusicLockscreenActive() || !isKeyguardLocked()) return
        armAodVisibilityPin()
        if (hasLyric && hasDisplayableText() &&
            (visibility == VISIBLE || visibility == INVISIBLE)
        ) {
            animate().cancel()
            setOverlayVisibilityQuiet(VISIBLE)
            alpha = 1f
            invalidate()
        }
        handler.post {
            try {
                updatePlayingState(force = true)
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
                if (isAodVisibilityPinActive() && hasDisplayableText()) {
                    animate().cancel()
                    setOverlayVisibilityQuiet(VISIBLE)
                    alpha = 1f
                }
            } catch (t: Throwable) {
                logE("aod screen-on refresh failed", t)
            }
        }
    }

    /** 沉浸歌词模式且当前有歌词正在显示（占用专辑区块）。 */
    fun isImmersiveLyricDisplayActive(): Boolean {
        return cfgImmersiveLyric && shouldDisplayLyric()
    }

    /**
     * 沉浸歌词是否应让出方形专辑位（含切歌等待首句；暂停不让出）。
     * 普通歌词有独立锚点，不与专辑同槽，故不参与占槽绑定。
     */
    fun isLyricPriorityOverAlbum(): Boolean {
        if (!cfgImmersiveLyric) return false
        val inPowerSticky = KeyguardSleepTransition.isInLinkageAnimWindow() ||
            isAodVisibilityPinActive()
        val playbackForSlot = isPlaying ||
            LyricAlbumSlotTransition.isPlaybackOkForLyricSlot(
                isPlaying = isPlaying,
                confirmedPaused = confirmedPaused,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                playbackHoldUntilMs = playbackHoldUntilMs,
                inScreenPowerTransition = inPowerSticky,
                musicLockscreenActive = isMusicLockscreenActive(),
                onKeyguard = isKeyguardLocked(),
                hasLyricData = hasLyric,
                hasDisplayableText = hasDisplayableText(),
            )
        return LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
            showLyricEnabled = LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, cfgShowLyric),
            musicLockscreenActive = isMusicLockscreenActive(),
            onKeyguard = isKeyguardLocked(),
            isPlaying = playbackForSlot,
            lyricCurrentlyDisplayed = shouldShowLyricOverlay(),
            trackGatePhase = trackGatePhase,
            hasLyricData = hasLyric,
            hasDisplayableText = hasDisplayableText(),
            preferLyricUntilResolved = preferLyricUntilResolved,
        )
    }

    /** 槽位淡入：MiBlur 生效时只做 alpha，避免 translation 掉帧。 */
    private fun springFadeInLyric() {
        if (isMagazinePageHost() &&
            !MagazinePageLyricHostPolicy.shouldRestartSurfaceFadeIn(
                visibilityVisible = visibility == VISIBLE,
                alpha = alpha,
            )
        ) {
            alpha = 1f
            translationY = 0f
            return
        }
        animate().cancel()
        alpha = 0f
        translationY = 0f
        val anim = if (LyricMotionPolicy.shouldTranslateSlot(this)) {
            translationY = LyricMotionPolicy.slotSlidePx(resources.displayMetrics.density)
            animate().alpha(1f).translationY(0f)
        } else {
            animate().alpha(1f)
        }
        LyricMotionPolicy.applySpring(anim).start()
    }

    /** 槽位淡出。 */
    private fun springFadeOutLyric(onEnd: (() -> Unit)? = null) {
        animate().cancel()
        val anim = if (LyricMotionPolicy.shouldTranslateSlot(this)) {
            val slide = LyricMotionPolicy.slotSlidePx(resources.displayMetrics.density)
            animate().alpha(0f).translationY(slide)
        } else {
            animate().alpha(0f)
        }
        LyricMotionPolicy.applySpring(anim).withEndAction {
            translationY = 0f
            onEnd?.invoke()
        }.start()
    }

    private fun scheduleRevealAfterLayout() {
        handler.removeCallbacks(revealAfterLayoutRunnable)
        handler.post(revealAfterLayoutRunnable)
    }

    private val revealAfterLayoutRunnable = Runnable {
        if (!shouldShowLyricOverlay()) return@Runnable
        if (!isMagazinePageHost()) {
            MediaFollowController.requestReflow()
        }
        if (visibility == INVISIBLE || visibility == GONE) {
            setOverlayVisibilityQuiet(INVISIBLE)
            requestLayout()
        }
        if (visibility == INVISIBLE) {
            setOverlayVisibilityQuiet(VISIBLE)
            // 电源钉住：直接显示，禁止从 0 再淡入造成「消失→出现」
            if (isAodVisibilityPinActive()) {
                animate().cancel()
                alpha = 1f
                translationY = 0f
            } else {
                springFadeInLyric()
            }
            invalidate()
            syncImmersiveMiBlur()
        }
    }

    private fun updateVisibilityState() {
        val wantLyric = shouldShowLyricOverlay()
        val hideAlbum = isLyricPriorityOverAlbum()
        val pinActive = isAodVisibilityPinActive()
        if (wantLyric) {
            if (isMagazinePageHost()) {
                updateMagazinePageVisibilityStable()
                notifyMagazineAlbumSlotIfNeeded()
                return
            }
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            startPolling()
            val alreadyVisible = visibility == View.VISIBLE && alpha > 0.5f
            val snapKeep = LyricAlbumSlotTransition.shouldSnapKeepVisible(
                alreadyVisible = alreadyVisible || visibility == View.VISIBLE,
                pinActive = pinActive,
                hasDisplayableText = hasDisplayableText(),
            )
            val needLayoutReveal = visibility == View.GONE || visibility == View.INVISIBLE
            if (snapKeep) {
                setOverlayVisibilityQuiet(View.VISIBLE)
                alpha = 1f
                translationY = 0f
                elevation = 48f * resources.displayMetrics.density
                translationZ = elevation
                invalidate()
                syncImmersiveMiBlur()
            } else if (needLayoutReveal) {
                // 暂停后再播：尺寸已知则直接淡入，避免再走一轮 INVISIBLE 空窗
                if (hasDisplayableText() && width > 0 && height > 0) {
                    setOverlayVisibilityQuiet(View.VISIBLE)
                    elevation = 48f * resources.displayMetrics.density
                    translationZ = elevation
                    if (pinActive) {
                        alpha = 1f
                        translationY = 0f
                    } else {
                        springFadeInLyric()
                    }
                    syncImmersiveMiBlur()
                    MediaFollowController.requestReflow()
                } else {
                    setOverlayVisibilityQuiet(View.INVISIBLE)
                    alpha = if (pinActive) 1f else 0f
                    elevation = 48f * resources.displayMetrics.density
                    translationZ = elevation
                    requestLayout()
                    MediaFollowController.requestReflow()
                    scheduleRevealAfterLayout()
                }
            } else {
                if (alpha < 0.99f) {
                    if (pinActive) {
                        alpha = 1f
                        translationY = 0f
                    } else {
                        springFadeInLyric()
                    }
                } else {
                    alpha = 1f
                    translationY = 0f
                }
                elevation = 48f * resources.displayMetrics.density
                translationZ = elevation
                invalidate()
                syncImmersiveMiBlur()
            }
            try {
                bringToFront()
            } catch (_: Throwable) {
            }
            applyAlbumSlotVisibility(hideAlbum = hideAlbum, animate = !pinActive)
        } else {
            if (isMagazinePageHost()) {
                fadeOutLyricOverlay()
                notifyMagazineAlbumSlotIfNeeded()
                return
            }
            fadeOutLyricOverlay()
            applyAlbumSlotVisibility(hideAlbum = hideAlbum, animate = true)
        }
    }

    /**
     * 画报页：禁止反复 cancel+springFadeIn；已显示则钉住 alpha，只做切行/栈动画。
     */
    private fun updateMagazinePageVisibilityStable() {
        scaleX = 1f
        scaleY = 1f
        startPolling()
        if (!hasDisplayableText()) {
            fadeOutLyricOverlay()
            return
        }
        // 真·MiBlur 未套上前保持透明，避免错色首帧
        if (MagazinePageMiBlurPolicy.hideUntilBlurReady() && !magazineMiBlurRevealed) {
            setOverlayVisibilityQuiet(View.VISIBLE)
            animate().cancel()
            alpha = 0f
            translationY = 0f
            if (!magazineMiBlurRefreshInFlight) {
                requestMagazineMiBlurRefresh()
            }
            return
        }
        val snap = MagazinePageLyricHostPolicy.shouldSnapKeepVisible(
            visibilityVisible = visibility == View.VISIBLE,
            hasDisplayableText = true,
        )
        if (snap) {
            if (alpha < 0.99f) {
                animate().cancel()
                alpha = 1f
                translationY = 0f
            }
        } else {
            setOverlayVisibilityQuiet(View.VISIBLE)
            animate().cancel()
            alpha = 1f
            translationY = 0f
        }
        elevation = 48f * resources.displayMetrics.density
        translationZ = elevation
        try {
            bringToFront()
        } catch (_: Throwable) {
        }
        syncImmersiveMiBlur()
        invalidate()
    }

    /** 歌词淡出隐藏；保留文本以便恢复播放时立刻淡入。 */
    private fun fadeOutLyricOverlay() {
        // 电源钉住期间禁止淡出，否则锁屏→AOD 会「消失再出现」
        if (isAodVisibilityPinActive()) {
            animate().cancel()
            if (hasDisplayableText()) {
                setOverlayVisibilityQuiet(View.VISIBLE)
                alpha = 1f
                translationY = 0f
            }
            return
        }
        animate().cancel()
        setLayerType(View.LAYER_TYPE_NONE, null)
        if (!isMusicLockscreenActive() || !hasLyric) {
            clearLyricDisplay()
            alpha = 0f
            translationY = 0f
            setOverlayVisibilityQuiet(View.GONE)
            return
        }
        if (visibility == View.GONE) {
            alpha = 0f
            translationY = 0f
            return
        }
        if (visibility == View.VISIBLE && alpha > 0.01f) {
            springFadeOutLyric {
                if (!shouldShowLyricOverlay()) {
                    setOverlayVisibilityQuiet(View.GONE)
                    alpha = 0f
                    translationY = 0f
                }
            }
        } else {
            alpha = 0f
            translationY = 0f
            setOverlayVisibilityQuiet(View.GONE)
        }
    }

    private fun applyAlbumSlotVisibility(hideAlbum: Boolean, animate: Boolean) {
        val album = MusicLockscreenManager.bigAlbumView ?: return
        if (hideAlbum) {
            album.animate().cancel()
            if (animate && album.visibility == View.VISIBLE && album.alpha > 0.01f) {
                val slide = LyricMotionPolicy.slotSlidePx(album.resources.displayMetrics.density)
                LyricMotionPolicy.applySpring(
                    album.animate().alpha(0f).translationY(slide),
                ).withEndAction {
                        try {
                            if (isLyricPriorityOverAlbum()) {
                                album.visibility = View.GONE
                                album.alpha = 1f
                                album.translationY = 0f
                                MediaFollowController.requestReflow()
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    .start()
            } else {
                album.visibility = View.GONE
                album.alpha = 1f
                album.translationY = 0f
                MediaFollowController.requestReflow()
            }
        } else {
            if (!MusicLockscreenManager.isShowing ||
                !LockscreenNotificationController.shouldShowKeyguardOverlays()
            ) {
                album.visibility = View.GONE
                return
            }
            val wasHidden = album.visibility != View.VISIBLE || album.alpha < 0.01f
            if (animate && wasHidden) {
                album.showForMusicLockscreen(preserveAlpha = true)
                if (album.visibility == View.VISIBLE) {
                    album.animate().cancel()
                    album.alpha = 0f
                    album.translationY = LyricMotionPolicy.slotSlidePx(album.resources.displayMetrics.density)
                    LyricMotionPolicy.applySpring(
                        album.animate().alpha(1f).translationY(0f),
                    ).start()
                }
            } else {
                MusicLockscreenManager.showAlbumOverlay()
            }
            if (cfgImmersiveLyric) {
                MediaFollowController.requestReflow()
            }
        }
    }

    private fun clearLyricDisplay() {
        cancelStackAnimator(commitPending = false)
        stackScrollOffset = 0f
        stackAnimProgress = 1f
        stackViewportHeightPx = 0
        lastStackLineIndex = -1
        pendingStackTriplet = null
        pendingStackIndex = -1
        stackPrevText = ""
        stackCurrentText = ""
        stackCurrentSecondaryText = ""
        stackNextText = ""
        stackPrevLayout = null
        stackCurrentLayout = null
        stackCurrentSecondaryLayout = null
        stackNextLayout = null
        rawMainText = ""
        rawSecondText = ""
        rawHasSecond = false
        currentMainText = ""
        currentSecondText = ""
        hasSecondLine = false
        secondIsTranslation = false
        mainStaticLayout = null
        immersiveSecondStaticLayout = null
        requestLayout()
        invalidate()
    }

    /**
     * 由 [StatusBarStateHook] 回调：已离开锁屏界面（OS4 解锁；非「锁屏下拉通知中心」）
     */
    fun setShadeOpen(open: Boolean) {
        if (shadeOpen == open) return
        shadeOpen = open
        post {
            updateVisibilityState()
            invalidate()
        }
    }

    // ============================================================
    // 歌词数据
    // ============================================================
    companion object {
        private const val PROVIDER_URI = "content://com.leowalk.musiclockscreen.lyric"
        private const val CONFIG_URI = "content://com.leowalk.musiclockscreen.config/config"
        internal const val LYRIC_BOOTSTRAP_GRACE_MS = 3000L

        @Volatile
        private var cachedMiSansBold: Typeface? = null

        @Volatile
        private var cachedMiSansMedium: Typeface? = null
    }

    private fun readAndUpdate() {
        try {
            detectTrackOrSongChange()

            val uri = Uri.parse(PROVIDER_URI)
            var newVLyric = lastLyricVersion
            var newVLyricFd = lastLyricFdVersion
            try {
                val vb = context.contentResolver.call(uri, "versions", null, null)
                if (vb != null) {
                    newVLyric = vb.getInt("lyric", -1)
                    newVLyricFd = vb.getInt("lyricfd", -1)
                }
            } catch (_: Throwable) {}

            val versionsChanged = newVLyric != lastLyricVersion || newVLyricFd != lastLyricFdVersion
            val forceProviderRead = AodLyricDisplayPolicy.shouldForceProviderReadOnDirty(
                dataDirty = dataDirty,
                versionsChanged = versionsChanged,
            )

            if (!forceProviderRead) {
                // LyricFocus 常不 bump version：轮询仍合并最新轻量 l/s
                val softApplied = if (AodLyricDisplayPolicy.shouldSoftMergeLightLyricEachPoll(
                        versionsChanged = false,
                        hasDisplayableCache = cachedLines?.isNotEmpty() == true || hasLyric,
                    )
                ) {
                    softMergeLatestLightLyric()
                } else {
                    false
                }
                // softMerge 已按焦点行上屏时禁止再用进度索引盖掉，否则「不及时」
                if (!softApplied) {
                    refreshCurrentLineFromCache()
                }
                return
            }
            dataDirty = false
            lastVersionsCheck = SystemClock.elapsedRealtime()

            doReadAndUpdate(newVLyric, newVLyricFd, forceProviderRead = true)
        } catch (e: Throwable) {
            logE("readAndUpdate error", e)
        }
    }

    private fun doReadAndUpdate(newVLyric: Int, newVLyricFd: Int, forceProviderRead: Boolean) {
        val oldVLyric = lastLyricVersion
        val oldVLyricFd = lastLyricFdVersion
        val snapshotEmpty = AodLyricDisplayPolicy.isLyricSnapshotEmpty(lastLyricJson)
        try {
            // 禁止在读到内容前进 version：否则读失败后 version 已对齐，轮询不再拉取，
            // 只能等 LyricFocus 切源 bump——表现为「重启后要切源才出词」。
            val uri = Uri.parse(PROVIDER_URI)

            var fdRead = false
            var providerContacted = false
            // dataDirty 未 bump version 时只强制轻量包；FD 仍按 version/空快照策略
            val reloadFd = AodLyricDisplayPolicy.shouldReloadLyricFd(
                oldVLyricFd,
                newVLyricFd,
                snapshotEmpty,
            )
            if (reloadFd) {
                try {
                    val fb = context.contentResolver.call(uri, "lyric_fd", null, null)
                    providerContacted = true
                    val pfd = fb?.getParcelable("fd") as? android.os.ParcelFileDescriptor
                    if (pfd != null) {
                        val fis = FileInputStream(pfd.fileDescriptor)
                        val bos = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var n: Int
                        while (true) {
                            n = fis.read(buf)
                            if (n <= 0) break
                            bos.write(buf, 0, n)
                        }
                        fis.close()
                        pfd.close()
                        val raw = String(bos.toByteArray(), Charsets.UTF_8).ifBlank { "{}" }
                        try {
                            val jo = JSONObject(raw)
                            if (ingestProviderPayload(jo, raw, newVLyric, newVLyricFd)) {
                                fdRead = true
                                if (!AodLyricDisplayPolicy.hasValidLyricLines(jo)) {
                                    cachedCtx = null
                                    cachedLines = null
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                } catch (e: Throwable) {
                    logE("read lyric_fd error", e)
                }
            }

            val lightReload = forceProviderRead ||
                AodLyricDisplayPolicy.shouldReloadLightLyric(
                    oldVLyric = oldVLyric,
                    newVLyric = newVLyric,
                    snapshotEmpty = AodLyricDisplayPolicy.isLyricSnapshotEmpty(lastLyricJson),
                    fdVersionUnchangedOrFdFailed = oldVLyricFd == newVLyricFd || !fdRead,
                )
            if (lightReload) {
                try {
                    val lb = context.contentResolver.call(uri, "lyric", null, null)
                    providerContacted = true
                    val j = lb?.getString("n")
                    if (j != null) {
                        try {
                            val neu = JSONObject(j)
                            val emptyPush = !neu.has("l") && !neu.has("s") &&
                                !neu.has("title") && !neu.has("ctx")
                            if (emptyPush) {
                                resolveNoLyric()
                                lastLyricVersion = newVLyric
                                lastLyricFdVersion = newVLyricFd
                            } else if (!AodLyricDisplayPolicy.hasValidLyricLines(neu)) {
                                val old = try {
                                    JSONObject(lastLyricJson)
                                } catch (_: Throwable) {
                                    JSONObject("{}")
                                }
                                val sameSong = LyricReceivePolicy.titlesConfirmedSame(
                                    old.optString("title", ""),
                                    neu.optString("title", ""),
                                )
                                // 前奏空 l：仅确认同曲才保留 timeline；空标题不当同曲
                                if (AodLyricDisplayPolicy.shouldPreserveExistingLyricOnWeakLightPush(
                                        incomingValid = false,
                                        existingValid = AodLyricDisplayPolicy.hasValidLyricLines(old),
                                        sameSong = sameSong,
                                    )
                                ) {
                                    if (shouldMergeLyricCtx(old, neu)) {
                                        neu.put("ctx", old.get("ctx"))
                                        lastLyricJson = neu.toString()
                                    }
                                } else if (!sameSong && trackGatePhase == TrackLyricGate.Phase.WAITING) {
                                    // 切歌后弱包：保持空屏，勿把旧词写回
                                } else {
                                    lastLyricJson = neu.toString()
                                }
                                lastLyricVersion = newVLyric
                                lastLyricFdVersion = newVLyricFd
                                dataDirty = false
                            } else {
                                val old = try {
                                    JSONObject(lastLyricJson)
                                } catch (_: Throwable) {
                                    JSONObject("{}")
                                }
                                if (shouldMergeLyricCtx(old, neu)) {
                                    neu.put("ctx", old.get("ctx"))
                                }
                                // 必须走 ingest：轻量路径以前直接写 lastLyricJson，会绕过切歌门闩把旧词上屏
                                ingestProviderPayload(neu, neu.toString(), newVLyric, newVLyricFd)
                            }
                        } catch (_: Throwable) {
                            lastLyricJson = j
                        }
                    }
                } catch (e: Throwable) {
                    logE("read lyric bundle error", e)
                }
            }

            applyLyricFromJson()

            // 仍无快照且本次未成功提交 version → 保持旧 version，下轮继续拉
            if (AodLyricDisplayPolicy.isLyricSnapshotEmpty(lastLyricJson) &&
                lastLyricVersion == oldVLyric &&
                lastLyricFdVersion == oldVLyricFd
            ) {
                if (providerContacted && newVLyricFd < 0 && newVLyric <= 0) {
                    lastLyricVersion = newVLyric
                    lastLyricFdVersion = newVLyricFd
                } else {
                    dataDirty = true
                }
            }
        } catch (e: Throwable) {
            lastLyricVersion = oldVLyric
            lastLyricFdVersion = oldVLyricFd
            dataDirty = true
            logE("doReadAndUpdate fail", e)
        }
    }

    private fun applyLyricFromJson() {
        try {
            val raw = lastLyricJson.trim().ifEmpty { "{}" }
            if (raw != lastLyricJson) lastLyricJson = raw
            val lo = JSONObject(raw)

            if (raw == "{}" || !AodLyricDisplayPolicy.hasValidLyricLines(lo)) {
                // 弱快照但本地仍有 timeline：仅同曲前奏可保留；切歌 WAITING 禁止残留旧曲
                val lines = cachedLines
                val allowKeep = lines != null && lines.isNotEmpty() &&
                    trackGatePhase != TrackLyricGate.Phase.WAITING &&
                    !preferLyricUntilResolved &&
                    !staleProviderLyricSuppressed
                if (allowKeep) {
                    hasLyric = true
                    refreshCurrentLineFromCache()
                    return
                }
                hasLyric = false
                clearLyricDisplay()
                updateVisibilityState()
                requestLayout()
                return
            }

            if (!ensureProviderLyricMatchesMedia(lo)) return

            val l = lo.optString("l", "") ?: ""
            val s = lo.optString("s", "") ?: ""
            val ctx = lo.optJSONObject("ctx")

            updatePlayingState(force = !HookUtils.isScreenInteractive(context))

            if (ctx != null) {
                val linesArr = ctx.optJSONArray("lines")
                if (linesArr != null && linesArr.length() > 0
                    && linesArr.optJSONObject(0)?.has("tm") == true) {
                    val lines = ArrayList<LyricLine>()
                    for (i in 0 until linesArr.length()) {
                        val o = linesArr.optJSONObject(i) ?: continue
                        val tm = o.optLong("tm", Long.MAX_VALUE)
                        val t = o.optString("t", "") ?: ""
                        if (t.isNotBlank()) {
                            lines.add(LyricLine(tm, t, o.optString("r", "") ?: ""))
                        }
                    }
                    val focus = l.trim()
                    val matchesFocus = focus.isEmpty() || lines.any {
                        it.text.trim() == focus || it.translation.trim() == focus
                    }
                    if (LyricReceivePolicy.shouldDropCachedTimeline(
                            lightMain = l,
                            cachedLineCount = lines.size,
                            lightMatchesCachedLine = matchesFocus,
                            waitingForNewTrack = trackGatePhase == TrackLyricGate.Phase.WAITING,
                        )
                    ) {
                        cachedCtx = null
                        cachedLines = null
                    } else if (lines.isNotEmpty()) {
                        cachedCtx = ctx
                        cachedLines = lines
                    }
                }
            } else {
                cachedCtx = null
                cachedLines = null
            }

            val hasLines = cachedLines != null && cachedLines!!.isNotEmpty()
            val hasLight = l.isNotBlank() && l.trim().isNotEmpty()
            val newHasLyric = (hasLines || hasLight) &&
                AodLyricDisplayPolicy.hasValidLyricLines(lo)
            if (newHasLyric != hasLyric) {
                hasLyric = newHasLyric
                if (!newHasLyric) {
                    clearLyricDisplay()
                    updateVisibilityState()
                    requestLayout()
                }
            } else if (!newHasLyric) {
                clearLyricDisplay()
                updateVisibilityState()
            }

            if (!hasLyric) return

            if (cachedLines != null && cachedLines!!.isNotEmpty()) {
                refreshCurrentLineFromCache()
                return
            }

            val lines = cachedLines
            val useCache = lines != null && lines!!.isNotEmpty()

            val newMain: String
            val newSecond: String
            val newSecondIsTranslation: Boolean

            if (useCache) {
                val pos = getCurrentPosition()
                var idx = -1
                if (pos >= 0) idx = findCurrentLineIndex(lines!!, pos)
                val cur = if (idx >= 0) lines!![idx] else null
                val curTrans = cur?.translation?.takeIf { it.isNotBlank() } ?: ""
                newMain = if (cur != null) {
                    cur.text.ifBlank { " " }
                } else {
                    l.ifBlank { " " }
                }
                newSecond = if (curTrans.isNotEmpty()) {
                    curTrans
                } else if (!cfgImmersiveLyric && idx >= 0 && idx + 1 < lines!!.size) {
                    lines!![idx + 1].text
                } else {
                    ""
                }
                newSecondIsTranslation = curTrans.isNotEmpty()
            } else {
                val light = AodLyricDisplayPolicy.resolveLightLyricDisplay(
                    l = l,
                    s = s,
                    songHasTranslation = AodLyricDisplayPolicy.songHasTranslationFromCtx(lo),
                )
                newMain = light.main
                newSecond = light.second
                newSecondIsTranslation = light.isTranslation
            }

            setLyricLines(newMain, newSecond, newSecond.isNotBlank(), newSecondIsTranslation)
            finalizeLyricDisplayAfterContentUpdate()
        } catch (e: Throwable) {
            logE("applyLyricFromJson error", e)
        }
    }

    private fun updatePlayingState(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val onAod = !HookUtils.isScreenInteractive(context) && isKeyguardLocked()
        val throttleMs = if (onAod) 300L else 1000L
        if (!force && now - lastPlayingCheck < throttleMs) return
        lastPlayingCheck = now
        try {
            var sawPlaying = false
            var sawPaused = false
            for (controller in getMediaControllers()) {
                val state = controller.playbackState ?: continue
                when (state.state) {
                    PlaybackState.STATE_PLAYING -> sawPlaying = true
                    PlaybackState.STATE_PAUSED,
                    PlaybackState.STATE_STOPPED -> sawPaused = true
                }
            }
            when {
                sawPlaying -> {
                    isPlaying = true
                    confirmedPaused = false
                    playbackHoldUntilMs = now + LyricAlbumSlotTransition.PLAYBACK_HOLD_MS
                }
                sawPaused -> {
                    isPlaying = false
                    if (isMagazinePageHost()) {
                        // 保留播放 hold，避免 Session 短暂 PAUSED 触发淡出再淡入抽搐
                        if (now >= playbackHoldUntilMs) {
                            confirmedPaused = true
                            playbackHoldUntilMs = 0L
                        }
                    } else {
                        confirmedPaused = true
                        playbackHoldUntilMs = 0L
                    }
                }
                else -> {
                    // Session 短暂无态：保留 hold / confirmedPaused，避免 AOD 切换闪灭
                    isPlaying = false
                }
            }
        } catch (_: Throwable) {
        }
    }

    // ============================================================
    // 轮询刷新
    // ============================================================
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!polling) return

            val onKeyguard = isKeyguardLocked()
            val isBouncer = isBouncerShowing()
            val onAod = !HookUtils.isScreenInteractive(context)
            updatePlayingState(force = onAod && onKeyguard)
            if (onAod && onKeyguard) {
                ConfigReader.invalidate()
            }

            if (isMusicLockscreenActive() && onKeyguard && !isBouncer) {
                refreshNow()
                finalizeLyricDisplayAfterContentUpdate()
                KeepScreenController.sync()
            } else if (isMusicLockscreenActive() && onKeyguard) {
                // 歌词暂不可见（如 INVISIBLE 定位中）仍拉取数据，AOD 切歌不丢
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
            }

            updateVisibilityState()

            val interval = when {
                !isMusicLockscreenActive() || !onKeyguard -> 1000L
                !HookUtils.isScreenInteractive(context) && isMusicLockscreenActive() -> 300L
                isPlaying && (visibility == VISIBLE || visibility == INVISIBLE) -> 200L
                else -> 500L
            }
            handler.postDelayed(this, interval)
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(refreshRunnable)
    }

    private fun stopPolling() {
        if (!polling) return
        polling = false
        handler.removeCallbacks(refreshRunnable)
    }

    private fun refreshNow() {
        readAndUpdate()
    }

    /**
     * LyricFocus 常就地改 l/s 而不 bump version：合并进本地快照，供当前行立刻刷新。
     * @return true 已按轻量焦点行上屏（调用方勿再用进度索引覆盖）
     */
    private fun softMergeLatestLightLyric(): Boolean {
        try {
            val uri = Uri.parse(PROVIDER_URI)
            val lb = context.contentResolver.call(uri, "lyric", null, null) ?: return false
            val j = lb.getString("n") ?: return false
            val neu = JSONObject(j)
            val old = try {
                JSONObject(lastLyricJson.trim().ifEmpty { "{}" })
            } catch (_: Throwable) {
                JSONObject("{}")
            }
            val newL = neu.optString("l", "")
            val newS = neu.optString("s", "")
            val oldL = old.optString("l", "")
            val oldS = old.optString("s", "")
            val newTitle = neu.optString("title", "").trim()
            var changed = newL != oldL || newS != oldS
            if (newTitle.isNotBlank() && newTitle != old.optString("title", "").trim()) {
                changed = true
                old.put("title", newTitle)
            }
            if (!changed && !neu.has("ctx")) return false

            // 切歌 WAITING / 标题不齐：禁止把旧曲轻量行直接上屏
            if (AodLyricDisplayPolicy.hasValidLyricLines(neu)) {
                if (ingestProviderPayload(neu, j, lastLyricVersion, lastLyricFdVersion)) {
                    applyLyricFromJson()
                    if (newL.trim().isNotEmpty()) {
                        applyLightFocusLineNow(newL, newS)
                    }
                    return true
                }
                // ingest 拒绝（旧曲 / 未就绪）：不要再走 applyLightFocusLineNow
                return false
            }

            if (!changed) return false
            if (!ensureProviderLyricMatchesMedia(neu)) return false
            if (trackGatePhase == TrackLyricGate.Phase.WAITING) return false

            old.put("l", newL)
            old.put("s", newS)
            if (shouldMergeLyricCtx(old, neu)) {
                // neu 无 ctx，保留 old ctx
            } else if (neu.has("ctx")) {
                old.put("ctx", neu.get("ctx"))
            }
            lastLyricJson = old.toString()
            if (newL.trim().isNotEmpty() || newS.trim().isNotEmpty()) {
                hasLyric = true
            }
            // 立刻上屏：焦点行驱动（含沉浸栈），不能只改 JSON
            return applyLightFocusLineNow(newL, newS)
        } catch (e: Throwable) {
            logE("softMergeLatestLightLyric error", e)
            return false
        }
    }

    /**
     * LyricFocus 当前焦点行立刻上屏（timeline 匹配或直接显示 l/s）。
     * @return true 已写入显示内容
     */
    private fun applyLightFocusLineNow(lightMain: String, lightSecond: String): Boolean {
        val focus = lightMain.trim()
        val lines = cachedLines
        if (lines != null && lines.isNotEmpty() && focus.isNotEmpty()) {
            val byFocus = lines.indexOfFirst {
                it.text.trim() == focus || it.translation.trim() == focus
            }
            if (byFocus >= 0) {
                magazineHeldLineIndex = byFocus
                if (isImmersiveStackActive()) {
                    applyImmersiveStackFromLines(lines, byFocus)
                    return true
                }
                val currentText = lines[byFocus].text
                val currentTrans = lines[byFocus].translation.takeIf { it.isNotBlank() } ?: ""
                val nextText = if (byFocus + 1 < lines.size) lines[byFocus + 1].text else ""
                val songHasTrans = lines.any { it.translation.isNotBlank() }
                val resolved = AodLyricDisplayPolicy.resolveCachedLineDisplay(
                    currentText = currentText,
                    lineTranslation = currentTrans,
                    nextLineText = nextText,
                    lightMain = lightMain,
                    lightTranslation = lightSecond,
                    immersiveLyric = cfgImmersiveLyric,
                    songHasTranslation = songHasTrans,
                )
                setLyricLines(
                    resolved.main,
                    resolved.second,
                    resolved.hasSecond,
                    isTranslation = resolved.isTranslation,
                )
                finalizeLyricDisplayAfterContentUpdate()
                return true
            }
        }
        if (focus.isNotEmpty()) {
            // 沉浸栈需 timeline 索引才能画三行；无匹配时交还进度刷新
            if (isImmersiveStackActive()) return false
            val hasSecond = lightSecond.trim().isNotEmpty()
            setLyricLines(
                focus,
                lightSecond.trim(),
                hasSecond,
                isTranslation = hasSecond,
            )
            finalizeLyricDisplayAfterContentUpdate()
            return true
        }
        return false
    }

    private fun refreshCurrentLineFromCache() {
        try {
            val snapshot = try {
                JSONObject(lastLyricJson.trim().ifEmpty { "{}" })
            } catch (_: Throwable) {
                JSONObject("{}")
            }
            if (!ensureProviderLyricMatchesMedia(snapshot)) return

            val lines = cachedLines
            if (lines == null || lines.isEmpty()) {
                if (isAodLyricRefreshMode()) {
                    tryLoadAndAcceptLyricPayload(lastLyricVersion, lastLyricFdVersion)
                }
                return
            }

            val pos = getCurrentPosition()
            val found = if (pos >= 0) findCurrentLineIndex(lines, pos) else -1
            val rawIdx = AodLyricDisplayPolicy.clampLyricLineIndex(found, lines.size)
            // LyricFocus 焦点行优先于进度索引，保证及时切行
            val focusIdx = try {
                val light = AodLyricDisplayPolicy.parseLyricSnapshotFields(
                    JSONObject(lastLyricJson.trim().ifEmpty { "{}" }),
                )
                val focus = light.l.trim()
                if (focus.isEmpty()) -1
                else lines.indexOfFirst {
                    it.text.trim() == focus || it.translation.trim() == focus
                }
            } catch (_: Throwable) {
                -1
            }
            val preferred = if (focusIdx >= 0) focusIdx else rawIdx
            if (LyricReceivePolicy.shouldDropCachedTimeline(
                    lightMain = try {
                        AodLyricDisplayPolicy.parseLyricSnapshotFields(
                            JSONObject(lastLyricJson.trim().ifEmpty { "{}" }),
                        ).l
                    } catch (_: Throwable) {
                        ""
                    },
                    cachedLineCount = lines.size,
                    lightMatchesCachedLine = focusIdx >= 0,
                    waitingForNewTrack = trackGatePhase == TrackLyricGate.Phase.WAITING,
                )
            ) {
                cachedCtx = null
                cachedLines = null
                val light = try {
                    AodLyricDisplayPolicy.parseLyricSnapshotFields(
                        JSONObject(lastLyricJson.trim().ifEmpty { "{}" }),
                    )
                } catch (_: Throwable) {
                    AodLyricDisplayPolicy.LyricSnapshotFields()
                }
                if (light.l.trim().isNotEmpty()) {
                    applyLightFocusLineNow(light.l, light.s)
                }
                return
            }
            val idx = if (isMagazinePageHost() && focusIdx < 0) {
                MagazinePageLyricHostPolicy.stabilizeLineIndex(
                    rawIndex = preferred,
                    heldIndex = magazineHeldLineIndex,
                    posMs = pos,
                    lineTimeMsAt = { i -> lines[i].time },
                    lineCount = lines.size,
                ).also { magazineHeldLineIndex = it }
            } else {
                preferred.also { if (it >= 0) magazineHeldLineIndex = it }
            }
            if (idx < 0) return

            val currentText = lines[idx].text
            val currentTrans = lines[idx].translation.takeIf { it.isNotBlank() } ?: ""
            val nextText = if (idx + 1 < lines.size) lines[idx + 1].text else ""

            if (isImmersiveStackActive()) {
                applyImmersiveStackFromLines(lines, idx)
                return
            }

            val lightFields = try {
                AodLyricDisplayPolicy.parseLyricSnapshotFields(
                    JSONObject(lastLyricJson.trim().ifEmpty { "{}" })
                )
            } catch (_: Throwable) {
                AodLyricDisplayPolicy.LyricSnapshotFields()
            }
            val songHasTrans = lines.any { it.translation.isNotBlank() }
            val resolved = AodLyricDisplayPolicy.resolveCachedLineDisplay(
                currentText = currentText,
                lineTranslation = currentTrans,
                nextLineText = nextText,
                lightMain = lightFields.l,
                lightTranslation = lightFields.s,
                immersiveLyric = cfgImmersiveLyric,
                songHasTranslation = songHasTrans,
            )

            setLyricLines(
                resolved.main,
                resolved.second,
                resolved.hasSecond,
                isTranslation = resolved.isTranslation,
            )
            finalizeLyricDisplayAfterContentUpdate()
        } catch (e: Throwable) {
            logE("refreshCurrentLineFromCache error", e)
        }
    }

    private fun findCurrentLineIndex(lines: List<LyricLine>, pos: Long): Int {
        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].time <= pos) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun getCurrentPosition(): Long {
        try {
            val mediaActive = try {
                ConfigReader.mediaPlaybackActive(context)
            } catch (_: Throwable) {
                false
            }
            for (controller in getMediaControllers()) {
                val state = controller.playbackState ?: continue
                val playing = state.state == PlaybackState.STATE_PLAYING
                // AOD 上 Session 状态偶发滞后：有活跃媒体时也按进度外推
                if (!playing && !isPlaying && !mediaActive) continue
                val now = SystemClock.elapsedRealtime()
                val speed = if (playing || isPlaying) {
                    state.playbackSpeed.takeIf { it > 0f } ?: 1f
                } else {
                    0f
                }
                val delta = now - posBaseTime
                // AOD 更勤刷原始 position，避免外推漂移过大
                val refreshMs = if (!HookUtils.isScreenInteractive(context)) 800L else 2000L
                if (delta > refreshMs || (!playing && !isPlaying) || posBaseTime == 0L) {
                    val p = state.position
                    if (playing) isPlaying = true
                    posBase = p
                    posBaseTime = now
                    return p
                }
                return posBase + (delta * speed).toLong()
            }
        } catch (_: Throwable) {
        }
        return -1L
    }

    // ============================================================
    // 歌词/翻译互换 + 滚动核心逻辑
    // ============================================================

    /**
     * 设置原始歌词行（从数据源来的）
     * main: 原文 / 当前行
     * second: 翻译 / 下一行
     * hasSecond: 是否有第二行
     * isTranslation: 第二行是不是翻译（true=翻译，false=下一句歌词）
     */
    private fun setLyricLines(rawMain: String, rawSecond: String, hasSecond: Boolean, isTranslation: Boolean = false) {
        val mainChanged = rawMain != rawMainText
        val secondChanged = rawSecond != rawSecondText
        val hasSecondChanged = hasSecond != rawHasSecond
        val transChanged = isTranslation != secondIsTranslation

        if (!mainChanged && !secondChanged && !hasSecondChanged && !transChanged) return

        rawMainText = rawMain
        rawSecondText = rawSecond
        rawHasSecond = hasSecond
        secondIsTranslation = isTranslation

        android.util.Log.i("HyperLockMusic_Lyric",
            "setLyricLines main=[$rawMain] second=[$rawSecond] hasSecond=$hasSecond isTranslation=$isTranslation")

        applySwapIfNeeded()
    }

    /**
     * 根据互换开关和翻译有无，决定实际显示的主行/副行。
     */
    private fun applySwapIfNeeded() {
        val swapped = AodLyricDisplayPolicy.applyLyricSwap(
            rawMain = rawMainText,
            rawSecond = rawSecondText,
            hasSecond = rawHasSecond,
            isTranslation = secondIsTranslation,
            swapEnabled = cfgSwapLyric,
        )
        val displayMain = swapped.main
        val displaySecond = swapped.second
        val displayHasSecond = swapped.hasSecond
        val hasTrans = secondIsTranslation && rawHasSecond && rawSecondText.isNotBlank()

        android.util.Log.i("HyperLockMusic_Lyric",
            "swapResult hasTrans=$hasTrans => main=[$displayMain] second=[$displaySecond]")

        val mainChanged = currentMainText != displayMain
        val secondChanged = currentSecondText != displaySecond
        val hasSecondChanged = hasSecondLine != displayHasSecond

        if (mainChanged || secondChanged || hasSecondChanged) {
            val stackActive = isImmersiveStackActive()
            val wantAnim = mainChanged &&
                shouldDisplayLyric() &&
                !stackActive &&
                LyricLineTransitionPolicy.shouldAnimate(HookUtils.isScreenInteractive(context))
            if (wantAnim) {
                animateLineChange(displayMain, displaySecond, displayHasSecond)
            } else {
                cancelLineTransition()
                applyLyricContentImmediate(displayMain, displaySecond, displayHasSecond)
            }
            finalizeLyricDisplayAfterContentUpdate()
        }
    }

    private fun applyImmersiveStackFromLines(lines: List<LyricLine>, index: Int) {
        val lineTexts = lines.map {
            ImmersiveLyricStackPolicy.LineText(it.text, it.translation)
        }
        val triplet = ImmersiveLyricStackPolicy.resolveTriplet(
            lines = lineTexts,
            index = index,
            swapEnabled = cfgSwapLyric,
        )
        val indexChanged = lastStackLineIndex >= 0 && index != lastStackLineIndex
        val textChanged =
            triplet.prev != stackPrevText ||
                triplet.current != stackCurrentText ||
                triplet.currentSecondary != stackCurrentSecondaryText ||
                triplet.next != stackNextText

        val i = index.coerceIn(0, lines.lastIndex)
        val curLine = lines[i]
        val hasTrans = curLine.translation.isNotBlank()
        // 同步主/副行字段（供其它逻辑）；三行模式在栈里画翻译副行
        rawMainText = curLine.text
        rawSecondText = if (hasTrans) {
            if (cfgSwapLyric) curLine.text else curLine.translation
        } else ""
        rawHasSecond = hasTrans && rawSecondText.isNotBlank()
        secondIsTranslation = hasTrans && !cfgSwapLyric
        currentMainText = triplet.current
        currentSecondText = triplet.currentSecondary
        hasSecondLine = triplet.currentSecondary.isNotBlank()

        if (!textChanged && !indexChanged) {
            lastStackLineIndex = index
            finalizeLyricDisplayAfterContentUpdate()
            return
        }

        val animate = indexChanged &&
            index == lastStackLineIndex + 1 &&
            shouldDisplayLyric() &&
            ImmersiveLyricStackPolicy.shouldUseStack(
                immersiveLyric = true,
                stackEnabled = true,
                screenInteractive = HookUtils.isScreenInteractive(context),
            )

        if (animate) {
            cancelLineTransition()
            cancelStackAnimator(commitPending = true)
            // 先换上「已展开」的新 triplet，再整块从下方滑入——无单行再展开
            commitStackTriplet(triplet, index, prepareSlideIn = true)
            animateStackSlideIn()
        } else {
            cancelStackAnimator(commitPending = true)
            commitStackTriplet(triplet, index, prepareSlideIn = false)
            invalidate()
        }
        finalizeLyricDisplayAfterContentUpdate()
    }

    private fun commitStackTriplet(
        triplet: ImmersiveLyricStackPolicy.Triplet,
        index: Int,
        prepareSlideIn: Boolean = false,
    ) {
        stackPrevText = triplet.prev
        stackCurrentText = triplet.current
        stackCurrentSecondaryText = triplet.currentSecondary
        stackNextText = triplet.next
        lastStackLineIndex = index
        rebuildStackLayouts()
        // 先定最终视口高度（含展开翻译），动画帧内不再改高
        resizeKeepingBottom(computeImmersiveStackHeightPx())
        if (prepareSlideIn) {
            val curH = stackCurrentLayout?.height?.toFloat() ?: 1f
            val secH = stackCurrentSecondaryLayout?.height?.toFloat() ?: 0f
            val step = ImmersiveLyricStackPolicy.promotionStepPx(curH, secH, lineGapPx)
            stackMotionActive = true
            stackAnimProgress = 0f
            stackScrollOffset = step
        } else {
            stackScrollOffset = 0f
            stackAnimProgress = 1f
            stackMotionActive = false
        }
    }

    /**
     * 已是展开新词：仅把整块从 +step 收到 0。
     * 帧回调用 postInvalidateOnAnimation，并对齐 vsync；绘制走轻量路径。
     */
    private fun animateStackSlideIn() {
        stackAnimator?.cancel()
        stackAnimator = null
        ensureStackLayouts((computeLyricWidthPx() - hPaddingPx * 2).toInt().coerceAtLeast(1))
        val curH = stackCurrentLayout?.height?.toFloat() ?: return
        val secH = stackCurrentSecondaryLayout?.height?.toFloat() ?: 0f
        val step = ImmersiveLyricStackPolicy.promotionStepPx(curH, secH, lineGapPx)
        stackMotionActive = true
        stackAnimProgress = 0f
        stackScrollOffset = step
        val duration = ImmersiveLyricStackPolicy.PROMOTION_MS
        stackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LyricMotionPolicy.fastOutSlowIn()
            addUpdateListener {
                val p = (it.animatedValue as Float).coerceIn(0f, 1f)
                stackAnimProgress = p
                stackScrollOffset = ImmersiveLyricStackPolicy.scrollOffsetPx(p, step)
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    stackAnimator = null
                    stackMotionActive = false
                    stackScrollOffset = 0f
                    stackAnimProgress = 1f
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    stackAnimator = null
                    stackMotionActive = false
                }
            })
            start()
        }
    }

    private fun cancelStackAnimator(commitPending: Boolean = false) {
        val pending = pendingStackTriplet
        val idx = pendingStackIndex
        val running = stackAnimator
        stackAnimator = null
        stackMotionActive = false
        running?.cancel()
        stackAnimProgress = 1f
        stackScrollOffset = 0f
        // 兼容旧 pending 路径（若有）
        if (commitPending && pending != null && idx >= 0) {
            pendingStackTriplet = null
            pendingStackIndex = -1
            commitStackTriplet(pending, idx, prepareSlideIn = false)
        } else {
            pendingStackTriplet = null
            pendingStackIndex = -1
        }
    }

    private fun applyLyricContentImmediate(main: String, second: String, hasSecond: Boolean) {
        currentMainText = main
        currentSecondText = second
        hasSecondLine = hasSecond

        if (cfgImmersiveLyric) {
            rebuildImmersiveLayouts()
            val h = if (isImmersiveStackActive()) {
                computeImmersiveStackHeightPx()
            } else {
                computeLyricWidthPx()
            }
            resizeKeepingBottom(h)
        } else {
            val layout = buildMainLayout(main.ifBlank { " " })
            mainStaticLayout = layout
            resizeKeepingBottom(computeContentHeightPx(layout, hasSecond))
        }
        notifyMagazineHostLyric(main)
        invalidate()
    }

    private fun notifyMagazineHostLyric(main: String) {
        try {
            if (isMagazinePageHost()) return
            if (!ConfigReader.isMagazineChrome(context)) return
            if (!MagazineModePolicy.shouldUseLyricOverlay(
                    chromeMagazine = true,
                    lyricEnabled = ConfigReader.lyricEnabled(context),
                    showLyric = ConfigReader.showLyric(context),
                )
            ) {
                return
            }
            MagazineHost.updateLyricLine(main.trim())
        } catch (_: Throwable) {
        }
    }

    private fun cancelLineTransition(resetTransform: Boolean = true) {
        lineTransitionAnimator?.cancel()
        lineTransitionAnimator = null
        if (resetTransform) {
            lineContentAlpha = 1f
            lineContentTx = 0f
            lineContentTy = 0f
        }
    }

    private fun applyLineTransform(t: LyricLineTransitionPolicy.Transform) {
        lineContentAlpha = t.alpha
        lineContentTx = t.tx
        lineContentTy = t.ty
        invalidate()
    }

    private fun lineSlidePx(): Float {
        return LyricMotionPolicy.lineSlidePx(resources.displayMetrics.density)
    }

    /** 切行：先离场再换词入场；AOD 不走此路径。 */
    private fun animateLineChange(main: String, second: String, hasSecond: Boolean) {
        cancelLineTransition(resetTransform = false)
        val mode = LyricLineTransitionPolicy.normalize(cfgLyricTransition)
        val slide = lineSlidePx()

        fun startEnter() {
            lineTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = lineFadeInMs
                interpolator = LyricMotionPolicy.forLineEnter(mode)
                addUpdateListener {
                    val p = it.animatedValue as Float
                    applyLineTransform(
                        LyricLineTransitionPolicy.enterTransform(mode, p, slide)
                    )
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        lineTransitionAnimator = null
                        applyLineTransform(LyricLineTransitionPolicy.Transform(1f, 0f, 0f))
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        lineTransitionAnimator = null
                    }
                })
                start()
            }
        }

        fun swapAndEnter() {
            applyLyricContentImmediate(main, second, hasSecond)
            applyLineTransform(LyricLineTransitionPolicy.enterTransform(mode, 0f, slide))
            startEnter()
        }

        if (lineContentAlpha <= 0.05f) {
            swapAndEnter()
            return
        }

        lineTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (lineFadeOutMs * lineContentAlpha).toLong().coerceIn(40L, lineFadeOutMs)
            interpolator = LyricMotionPolicy.forLineExit(mode)
            addUpdateListener {
                val p = it.animatedValue as Float
                applyLineTransform(
                    LyricLineTransitionPolicy.exitTransform(mode, p, slide)
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lineTransitionAnimator = null
                    swapAndEnter()
                }

                override fun onAnimationCancel(animation: Animator) {
                    lineTransitionAnimator = null
                }
            })
            start()
        }
    }

    private fun buildMainLayout(text: String): StaticLayout {
        val lyricWidth = computeLyricWidthPx()
        val maxContentWidth = (lyricWidth - hPaddingPx * 2).toInt()
        val mainTextPaint = TextPaint(mainPaint).apply {
            textAlign = Paint.Align.LEFT
        }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, mainTextPaint, maxContentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .build()
    }

    // ============================================================
    // 工具
    // ============================================================
    private fun readCurrentMediaTitle(): String {
        return try {
            for (controller in getMediaControllers()) {
                val title = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                if (!title.isNullOrBlank()) return title
            }
            ""
        } catch (_: Throwable) {
            ""
        }
    }

    /** AOD / 锁屏：优先经通知使用权组件取会话，失败再回退 null */
    private fun getMediaControllers(): List<android.media.session.MediaController> {
        return try {
            com.leowalk.musiclockscreen.MediaSessionAccess.getActiveControllers(context)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** trackKey 或 Session 歌名变化时清快照；Provider 歌名不得写进 lastSongTitle。 */
    private fun detectTrackOrSongChange(): Boolean {
        val mediaTitle = readCurrentMediaTitle()
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        val keyChanged = AodLyricDisplayPolicy.shouldResetLyricForTrackKeyChange(
            lastKnownTrackKey,
            trackKey,
        )
        val titleChanged = LyricReceivePolicy.shouldResetForMediaTitleChange(
            lastSongTitle,
            mediaTitle,
        )
        val trackChanged = keyChanged || titleChanged
        if (trackChanged) {
            staleProviderLyricSuppressed = false
            // 先拍快照再清：拒 Provider 仍推旧曲；同时清 lastLyricJson 防 softMerge 当「当前」
            markPreferLyricUntilResolved()
            purgeDisplayedLyrics(resetProviderSnapshot = true)
            dataDirty = true
        }
        if (mediaTitle.isNotBlank()) lastSongTitle = mediaTitle
        if (trackKey != null) lastKnownTrackKey = trackKey
        return trackChanged
    }

    /**
     * 切歌后 LyricFocus 常就地更新 l/s/ctx 而不 bump version；主动读 provider 判定是否可上屏。
     * @return true 表示已接受新歌词并完成 apply
     */
    private fun tryLoadAndAcceptLyricPayload(vLyric: Int, vFd: Int): Boolean {
        val uri = Uri.parse(PROVIDER_URI)
        try {
            val lb = context.contentResolver.call(uri, "lyric", null, null)
            val j = lb?.getString("n")
            if (j != null) {
                val jo = JSONObject(j)
                if (ingestProviderPayload(jo, j, vLyric, vFd)) {
                    applyLyricFromJson()
                    return true
                }
                if (trackGatePhase == TrackLyricGate.Phase.IDLE && !hasLyric) {
                    // SHOW_ALBUM 已在 ingest 处理
                    return false
                }
            }
        } catch (e: Throwable) {
            logE("tryLoad light lyric error", e)
        }

        try {
            val fb = context.contentResolver.call(uri, "lyric_fd", null, null)
            val pfd = fb?.getParcelable("fd") as? android.os.ParcelFileDescriptor ?: return false
            val fis = FileInputStream(pfd.fileDescriptor)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var n: Int
            while (true) {
                n = fis.read(buf)
                if (n <= 0) break
                bos.write(buf, 0, n)
            }
            fis.close()
            pfd.close()
            val json = String(bos.toByteArray(), Charsets.UTF_8).ifBlank { "{}" }
            val jo = JSONObject(json)
            if (!ingestProviderPayload(jo, json, vLyric, vFd)) return false
            applyLyricFromJson()
            return true
        } catch (e: Throwable) {
            logE("tryLoad lyric_fd error", e)
            return false
        }
    }

    /** 轻量包缺 ctx 时仅在同曲合并全量 ctx。 */
    private fun shouldMergeLyricCtx(old: JSONObject, neu: JSONObject): Boolean {
        if (neu.has("ctx") || !old.has("ctx")) return false
        return LyricReceivePolicy.shouldMergePreviousCtx(
            incomingHasCtx = neu.has("ctx"),
            previousHasCtx = old.has("ctx"),
            titlesConfirmedSame = LyricReceivePolicy.titlesConfirmedSame(
                old.optString("title", ""),
                neu.optString("title", ""),
            ),
            waitingForNewTrack = trackGatePhase == TrackLyricGate.Phase.WAITING,
        )
    }

    private fun isKeyguardLocked(): Boolean {
        if (isMagazinePageHost()) return true
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            km.isKeyguardLocked
        } catch (e: Throwable) {
            false
        }
    }

    private fun isBouncerShowing(): Boolean {
        return HookUtils.isBouncerShowing(this)
    }

    private fun logI(msg: String) {
        android.util.Log.i("HyperLockMusic_Lyric", msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e("HyperLockMusic_Lyric", msg, e)
    }
}
