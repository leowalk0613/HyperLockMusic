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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
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
    /** 沉浸歌词切行：淡出 / 淡入时长（合计 250ms，避免系统限帧卡顿） */
    private val immersiveFadeOutMs = 125L
    private val immersiveFadeInMs = 125L

    private var immersiveContentAlpha = 1f
    private var immersiveFadeAnimator: ValueAnimator? = null
    private val immersiveFadeInterpolator = LinearInterpolator()

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
    private var cfgLyricHideBackground: Boolean = false
    private var cfgLyricAlign: String = "left"

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
    private var lastPlayingCheck: Long = 0

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
            rebuildImmersiveLayouts()
            setMeasuredDimension(
                resolveSizeAndState(lyricWidth, widthMeasureSpec, 0),
                resolveSizeAndState(lyricWidth, heightMeasureSpec, 0)
            )
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
     * 高度随歌词自适应，但底边锚点不变：先改 topMargin 再改 height，避免遮罩整块跳一帧。
     */
    private fun resizeKeepingBottom(newHeight: Int) {
        val lyricWidth = computeLyricWidthPx()
        val lp = layoutParams as? FrameLayout.LayoutParams
        if (lp == null) {
            requestLayout()
            return
        }
        val oldHeight = when {
            height > 0 -> height
            measuredHeight > 0 -> measuredHeight
            lp.height > 0 -> lp.height
            else -> 0
        }
        if (oldHeight == newHeight && lp.width == lyricWidth) {
            invalidate()
            return
        }
        if (oldHeight > 0) {
            val bottom = lp.topMargin + oldHeight
            lp.topMargin = (bottom - newHeight).coerceAtLeast(0)
        }
        lp.width = lyricWidth
        lp.height = newHeight
        layoutParams = lp
        MediaFollowController.syncLyricLaidOut(newHeight)
        if (shouldDisplayLyric() && visibility == INVISIBLE) {
            scheduleRevealAfterLayout()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }

    /** 歌词区域宽度（px）：沉浸模式用专辑区块宽度，否则屏宽 × 百分比 */
    private fun computeLyricWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return if (cfgImmersiveLyric) {
            (screenWidth * ConfigReader.albumSize(context) / 100f).toInt().coerceAtLeast(1)
        } else {
            (screenWidth * cfgLyricWidth / 100f).toInt().coerceAtLeast(1)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!shouldDisplayLyric()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. 渐变遮罩（普通模式且未接 MiBlur；MiBlur 生效时只留字形透色）
        if (!cfgImmersiveLyric && !immersiveMiBlurActive) {
            drawFogBackground(canvas, w, h)
        }

        val contentWidth = w - hPaddingPx * 2

        // 2. 歌词内容
        drawContent(canvas, w, contentWidth,
            currentMainText, currentSecondText, hasSecondLine, mainStaticLayout)
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

    /** 沉浸歌词：主行多行填满区块，翻译小字在下方，超出区域末行省略 */
    private fun drawImmersiveContent(
        canvas: Canvas, w: Float, contentWidth: Float,
        mainText: String, secondText: String, hasSecond: Boolean
    ) {
        if (immersiveContentAlpha <= 0.001f) return
        val alphaByte = (immersiveContentAlpha * 255f).toInt().coerceIn(0, 255)
        if (alphaByte == 0) return

        val mainLayout = mainStaticLayout ?: return
        val h = height.toFloat()

        val mainH = mainLayout.height.toFloat()
        val secondLayout = if (hasSecond) immersiveSecondStaticLayout else null
        val secondH = secondLayout?.height?.toFloat() ?: 0f
        val gap = if (secondLayout != null) lineGapPx else 0f
        val totalH = mainH + gap + secondH

        val startY = vPaddingPx + ((h - vPaddingPx * 2f - totalH) / 2f).coerceAtLeast(0f)

        val layerPaint = Paint().apply { alpha = alphaByte }
        val layer = canvas.saveLayer(0f, 0f, w, h, layerPaint)

        canvas.save()
        canvas.translate(hPaddingPx, startY)
        mainLayout.draw(canvas)
        canvas.restore()

        if (secondLayout != null) {
            canvas.save()
            canvas.translate(hPaddingPx, startY + mainH + gap)
            secondLayout.draw(canvas)
            canvas.restore()
        }

        canvas.restoreToCount(layer)
    }

    private fun rebuildImmersiveLayouts() {
        val block = computeLyricWidthPx()
        val contentW = (block - hPaddingPx * 2).toInt().coerceAtLeast(1)
        val main = currentMainText.ifBlank { " " }
        val maxMain = computeImmersiveMaxMainLines(hasSecondLine, block)
        mainStaticLayout = buildImmersiveLayout(main, mainPaint, contentW, maxMain)
        immersiveSecondStaticLayout = if (hasSecondLine && currentSecondText.isNotBlank()) {
            buildImmersiveLayout(
                currentSecondText, secondPaint, contentW, immersiveMaxSecondLines
            )
        } else {
            null
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

    private fun buildImmersiveLayout(
        text: String, paint: Paint, contentWidth: Int, maxLines: Int
    ): StaticLayout {
        val tp = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        val alignment = when (cfgLyricAlign) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        return StaticLayout.Builder
            .obtain(text.ifBlank { " " }, 0, text.length, tp, contentWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun drawTextCentered(
        canvas: Canvas, text: String, centerX: Float, y: Float,
        paint: Paint, maxWidthPx: Int
    ) {
        var displayText = text.ifBlank { " " }
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT

        val textWidth = paint.measureText(displayText)
        val x: Float

        if (textWidth <= maxWidthPx) {
            x = centerX - textWidth / 2f
        } else {
            val ellipsis = "…"
            val ellipsisW = paint.measureText(ellipsis)
            val targetW = maxWidthPx - ellipsisW
            var end = paint.breakText(displayText, true, targetW, null)
            if (end > 0) {
                displayText = displayText.substring(0, end) + ellipsis
            }
            val finalWidth = paint.measureText(displayText)
            x = centerX - finalWidth / 2f
        }

        canvas.drawText(displayText, x, y, paint)
        paint.textAlign = originalAlign
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidthPx: Int): String {
        val raw = text.ifBlank { " " }
        if (paint.measureText(raw) <= maxWidthPx) return raw
        val textPaint = if (paint is TextPaint) paint else TextPaint(paint)
        return TextUtils.ellipsize(
            raw, textPaint, maxWidthPx.toFloat(), TextUtils.TruncateAt.END
        ).toString()
    }

    private fun drawTextAligned(
        canvas: Canvas, text: String, viewWidth: Float, y: Float,
        paint: Paint, maxWidthPx: Int
    ) {
        val displayText = ellipsizeText(text, paint, maxWidthPx)
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT

        val textWidth = paint.measureText(displayText)
        val contentWidth = maxWidthPx.toFloat()
        val x = when (cfgLyricAlign) {
            "center" -> hPaddingPx + (contentWidth - textWidth) / 2f
            "right" -> hPaddingPx + contentWidth - textWidth
            else -> hPaddingPx
        }

        canvas.drawText(displayText, x, y, paint)
        paint.textAlign = originalAlign
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

    /** 切歌时重拉 Provider 歌词（信任 LyricFocus 输出，不走 WAITING 门闩）。 */
    fun onTrackMayHaveChanged() {
        refreshLyricsFromProvider(clearLineCache = true)
        if (isAodLyricRefreshMode()) {
            scheduleAodLyricRecoveryBurst()
        }
    }

    /** 从 LyricDataProvider 拉取并刷新显示；Provider 侧假定始终为最新。 */
    private fun refreshLyricsFromProvider(clearLineCache: Boolean) {
        dataDirty = true
        lastVersionsCheck = 0
        clearTrackGate()
        if (clearLineCache) {
            purgeDisplayedLyrics(resetProviderSnapshot = true)
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
        cancelImmersiveLineFade()
        cachedLines = null
        cachedCtx = null
        hasLyric = false
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

    /** @return true 表示载荷已写入 lastLyricJson 并应继续 apply；false 表示本包暂无有效行（不清屏，避免轮询打爆 UI）。 */
    private fun ingestProviderPayload(json: JSONObject, raw: String, vLyric: Int, vFd: Int): Boolean {
        if (!AodLyricDisplayPolicy.hasValidLyricLines(json)) {
            return false
        }
        lastLyricJson = raw
        lastLyricVersion = vLyric
        lastLyricFdVersion = vFd
        clearTrackGate()
        dataDirty = false
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
        cancelImmersiveLineFade()
        cachedLines = null
        cachedCtx = null
        hasLyric = false
        clearLyricDisplay()
        updateVisibilityState()
    }

    private fun resolveNoLyric() {
        clearTrackGate()
        lastLyricJson = "{}"
        cachedCtx = null
        cachedLines = null
        dataDirty = false
        if (!hasLyric && visibility == GONE) return
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
        if (!isMusicLockscreenActive() || !HookUtils.isOnKeyguard(context)) return
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
        // 渐变遮罩是 overlay，锁屏即可渲染；不要求屏幕 interactive，避免 AOD/过渡期丢背景
        if (!HookUtils.isOnKeyguard(context)) return
        val gen = fogBuildGeneration
        val expectedKey = trackKey ?: AlbumArtResolver.getCachedTrackKey()
        val album = sourceAlbum ?: AlbumArtResolver.getCached() ?: return
        val ownsAlbumCopy = sourceAlbum != null

        Thread {
            try {
                val tintColor = BlurUtils.extractLowerHalfDominantColor(album)
                post {
                    if (gen != fogBuildGeneration || !isMusicLockscreenActive() ||
                        !HookUtils.isOnKeyguard(context)
                    ) {
                        if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                        return@post
                    }
                    if (expectedKey != null && expectedKey != AlbumArtResolver.getCachedTrackKey()) {
                        if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                        return@post
                    }
                    fogTintColor = tintColor
                    LockscreenClockController.onAlbumTint(tintColor)
                    if (cfgImmersiveLyric) {
                        showFogBackground = false
                    } else {
                        showFogBackground = true
                    }
                    // 沉浸 / 普通歌词共用 MiBlur 透色；失败则走混色回退
                    immersiveMiBlurBlendKey = 0
                    syncImmersiveMiBlur()
                    if (!immersiveMiBlurActive) {
                        applyImmersiveTextColors()
                    }
                    invalidate()
                    if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                }
            } catch (_: Throwable) {
                if (ownsAlbumCopy && !album.isRecycled) album.recycle()
            }
        }.start()
    }

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
        cancelImmersiveLineFade()
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
        cancelImmersiveLineFade()
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPolling()
        unregisterAodScreenReceiver()
        unregisterLyricObserver()
        unregisterConfigObserver()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        when (visibility) {
            VISIBLE -> {
                dataDirty = true
                lastVersionsCheck = 0
                startPolling()
                refreshNow()
                syncImmersiveMiBlur()
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

                if (idxEnabled >= 0) {
                    cfgLyricEnabled = cursor.getInt(idxEnabled) != 0
                }
                if (idxShow >= 0) {
                    val newShow = cursor.getInt(idxShow) != 0
                    if (newShow != cfgShowLyric) {
                        cfgShowLyric = newShow
                        if (LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, newShow) &&
                            isMusicLockscreenActive()
                        ) {
                            dataDirty = true
                            lastVersionsCheck = 0
                            handler.post { readAndUpdate() }
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
                    if (newAnchor != cfgLyricBgAnchorY) positionChanged = true
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
                if (positionChanged) MediaFollowController.requestReflow()

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
                MusicLockscreenManager.showAlbumOverlay()
                KeepScreenController.sync()
                LockscreenClockController.sync()
            }
        } catch (e: Throwable) {
            logE("applyLyricConfig error", e)
        }
    }

    private fun applyLyricStyle() {
        cancelImmersiveLineFade()
        val density = resources.displayMetrics.density

        if (cfgImmersiveLyric) {
            showFogBackground = false
            mainPaint.textSize = immersiveLyricSizeSp * density
            secondPaint.textSize = immersiveLyricSizeSp * immersiveSecondSizeRatio * density
            applyImmersiveTypeface()
        } else {
            mainPaint.textSize = cfgLyricSize * density
            secondPaint.textSize = cfgLyricSize * 0.8f * density
            mainPaint.typeface = Typeface.DEFAULT_BOLD
            mainPaint.isFakeBoldText = false
            secondPaint.typeface = Typeface.DEFAULT
            secondPaint.isFakeBoldText = false
        }
        // 沉浸 / 普通歌词共用系统 MiBlur 白中透色
        applyImmersiveTextColors()
        syncImmersiveMiBlur()

        val lp = layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.leftMargin = 0
            lp.topMargin = 0
            lp.rightMargin = 0
            lp.bottomMargin = 0
            // 样式变更后恢复 WRAP，由 onMeasure 自适应
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams = lp
        }

        mainStaticLayout = null
        immersiveSecondStaticLayout = null
        requestLayout()
        MediaFollowController.requestReflow()
        invalidate()
    }

    /**
     * 歌词文字染色：优先系统 MiBlur（白中透色 / 近白底深色）；失败则专辑色混字。
     * 沉浸与普通模式共用。
     */
    private fun applyImmersiveTextColors() {
        val bgRef = contrastBackgroundColor()
        if (immersiveMiBlurActive) {
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
        val tint = boostAlbumTint(fogTintColor ?: bgRef)
        val mainColor = if (isNearWhiteBackground(bgRef)) {
            blendTextColor(Color.rgb(32, 32, 34), tint, 0.28f)
        } else {
            blendTextColor(Color.WHITE, tint, immersiveTintWeight)
        }
        mainPaint.color = mainColor
        secondPaint.color = Color.argb(
            if (isNearWhiteBackground(bgRef)) 200 else 160,
            Color.red(mainColor),
            Color.green(mainColor),
            Color.blue(mainColor)
        )
        if (isNearWhiteBackground(bgRef)) {
            mainPaint.setShadowLayer(10f, 0f, 2f, Color.argb(100, 255, 255, 255))
            secondPaint.setShadowLayer(8f, 0f, 2f, Color.argb(80, 255, 255, 255))
        } else {
            mainPaint.setShadowLayer(10f, 0f, 3f, Color.argb(230, 0, 0, 0))
            secondPaint.setShadowLayer(7f, 1f, 3f, Color.argb(210, 0, 0, 0))
        }
    }

    private fun syncImmersiveMiBlur() {
        if (!isMusicLockscreenActive() ||
            visibility == GONE || !HyperMiBlurHelper.isSupported(context)
        ) {
            clearImmersiveMiBlur()
            return
        }
        // 对比度看「歌词背后的壁纸」，透色仍可用专辑色
        val bgRef = contrastBackgroundColor()
        val tint = boostAlbumTint(fogTintColor ?: bgRef)
        val bgLum = colorLuminance(bgRef)
        // 仅近白壁纸转深色（用壁纸取样，避免沉浸专辑 Monet 浅底却按深色专辑误判）
        val onLight = isNearWhiteBackground(bgRef)
        val blend: Int
        val primary: Int
        val over: Int
        val blendAlpha: Int
        val labAlpha: Int
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
        val modeBit = if (cfgImmersiveLyric) 0x10 else 0x20
        val blendKey = blend xor bgRef xor (if (onLight) 0x91 else 0x92) xor modeBit xor
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
            enablePassBlurOnSelf = true,
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
                    "bgLum=${"%.2f".format(bgLum)} bg=#${Integer.toHexString(bgRef)} " +
                    "blend=#${Integer.toHexString(blend)}"
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
     * 沉浸专辑时 Monet 浅色底与专辑主色常不一致，不能只看 fogTint。
     */
    private fun contrastBackgroundColor(): Int {
        sampleWallpaperBehindLyrics()?.let { return it }
        return fogTintColor ?: Color.WHITE
    }

    private fun sampleWallpaperBehindLyrics(): Int? {
        val bmp = MusicLockscreenManager.blurredWallpaperBitmap
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
            )
        } else {
            arrayOf(
                "/system/fonts/MiSans-Medium.ttf",
                "/system/fonts/MiSans-Regular.ttf",
                "/system/fonts/MiSans-Demibold.ttf",
                "/product/fonts/MiSans-Regular.ttf",
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
        val family = if (bold) "sans-serif-black" else "sans-serif-medium"
        for (name in arrayOf(if (bold) "MiSans" else "MiSans", "mipro-medium", family)) {
            try {
                val style = if (bold) Typeface.BOLD else Typeface.NORMAL
                val tf = Typeface.create(name, style)
                if (bold) cachedMiSansBold = tf else cachedMiSansMedium = tf
                return tf
            } catch (_: Throwable) {
            }
        }
        val fallback = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (bold) cachedMiSansBold = fallback else cachedMiSansMedium = fallback
        return fallback
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
        return MusicLockscreenManager.isShowing || WallpaperController.isShowing()
    }

    private fun hasDisplayableText(): Boolean {
        return currentMainText.isNotBlank() ||
            (hasSecondLine && currentSecondText.isNotBlank())
    }

    private fun shouldDisplayLyric(): Boolean {
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

    /** 含 AOD 兜底：文本已就绪但 Session 播放态滞后时仍应上屏。 */
    private fun shouldShowLyricOverlay(): Boolean {
        return shouldDisplayLyric() || isAodLyricRevealEligible()
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
        )
    }

    private fun isAodLyricRefreshMode(): Boolean {
        return AodLyricDisplayPolicy.isAodLyricRefreshMode(
            HookUtils.isScreenInteractive(context),
            isKeyguardLocked(),
        )
    }

    /** 息屏/AOD 下歌词文本已就绪即可尝试上屏（不依赖 MediaFollow 以外的播放态）。 */
    private fun isAodLyricRevealEligible(): Boolean {
        return !HookUtils.isScreenInteractive(context) &&
            LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, cfgShowLyric) &&
            isMusicLockscreenActive() &&
            isKeyguardLocked() &&
            !isBouncerShowing() &&
            !shadeOpen &&
            LockscreenNotificationController.shouldShowKeyguardOverlays() &&
            hasLyric &&
            hasDisplayableText()
    }

    private fun finalizeLyricDisplayAfterContentUpdate() {
        if (!hasLyric || !hasDisplayableText()) return
        updateVisibilityState()
        if (!shouldDisplayLyric() && !isAodLyricRevealEligible()) return
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
        val delays = longArrayOf(100L, 300L, 600L, 1200L, 2000L, 3500L)
        for (delayMs in delays) {
            handler.postDelayed({
                if (generation != aodRecoveryBurstGeneration) return@postDelayed
                if (!isMusicLockscreenActive() || !isKeyguardLocked()) return@postDelayed
                ConfigReader.invalidate()
                dataDirty = true
                readAndUpdate()
                finalizeLyricDisplayAfterContentUpdate()
            }, delayMs)
        }
    }

    private fun registerAodScreenReceiver() {
        if (aodScreenReceiver != null) return
        val app = context.applicationContext
        aodScreenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenPoweredOffForAod()
                    Intent.ACTION_SCREEN_ON -> {
                        ConfigReader.invalidate()
                        handler.post {
                            readAndUpdate()
                            finalizeLyricDisplayAfterContentUpdate()
                        }
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
        ConfigReader.invalidate()
        dataDirty = true
        startPolling()
        handler.post {
            readAndUpdate()
            finalizeLyricDisplayAfterContentUpdate()
        }
        scheduleAodLyricRecoveryBurst()
    }

    /** 沉浸歌词模式且当前有歌词正在显示（占用专辑区块）。 */
    fun isImmersiveLyricDisplayActive(): Boolean {
        return cfgImmersiveLyric && shouldDisplayLyric()
    }

    /** 歌词开关开启时应让出方形专辑位（含 AOD 切歌等待新歌词）。 */
    fun isLyricPriorityOverAlbum(): Boolean {
        return LyricAlbumPriorityPolicy.shouldHideSquareAlbum(
            showLyricEnabled = LyricDisplayPolicy.shouldShowLyric(cfgLyricEnabled, cfgShowLyric),
            musicLockscreenActive = isMusicLockscreenActive(),
            onKeyguard = isKeyguardLocked(),
            lyricCurrentlyDisplayed = shouldDisplayLyric(),
            trackGatePhase = trackGatePhase,
            hasLyricData = hasLyric,
            hasDisplayableText = hasDisplayableText(),
        )
    }

    private fun scheduleRevealAfterLayout() {
        handler.removeCallbacks(revealAfterLayoutRunnable)
        handler.post(revealAfterLayoutRunnable)
    }

    private val revealAfterLayoutRunnable = Runnable {
        if (!shouldShowLyricOverlay()) return@Runnable
        MediaFollowController.requestReflow()
        if (visibility == INVISIBLE || visibility == GONE) {
            visibility = INVISIBLE
            requestLayout()
        }
        if (visibility == INVISIBLE) {
            visibility = VISIBLE
            alpha = 1f
            invalidate()
            syncImmersiveMiBlur()
        }
    }

    private fun updateVisibilityState() {
        if (shouldShowLyricOverlay()) {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            startPolling()
            // 保持 INVISIBLE，等 MediaFollow 写好 topMargin 后再设 VISIBLE
            if (visibility == View.GONE || visibility == View.INVISIBLE) {
                visibility = View.INVISIBLE
                alpha = 1f
                elevation = 48f * resources.displayMetrics.density
                translationZ = elevation
                requestLayout()
                MediaFollowController.requestReflow()
                scheduleRevealAfterLayout()
            } else {
                alpha = 1f
                elevation = 48f * resources.displayMetrics.density
                translationZ = elevation
                invalidate()
                syncImmersiveMiBlur()
            }
            try {
                bringToFront()
            } catch (_: Throwable) {
            }
            if (isLyricPriorityOverAlbum()) {
                MusicLockscreenManager.bigAlbumView?.visibility = View.GONE
            } else if (cfgImmersiveLyric) {
                MusicLockscreenManager.showAlbumOverlay()
            }
        } else {
            alpha = 0f
            setLayerType(View.LAYER_TYPE_NONE, null)
            if (!isMusicLockscreenActive() || !hasLyric) {
                clearLyricDisplay()
            }
            visibility = View.GONE
            if (isLyricPriorityOverAlbum()) {
                MusicLockscreenManager.bigAlbumView?.visibility = View.GONE
                MediaFollowController.requestReflow()
            } else {
                MusicLockscreenManager.showAlbumOverlay()
                if (cfgImmersiveLyric) {
                    MediaFollowController.requestReflow()
                }
            }
        }
    }

    private fun clearLyricDisplay() {
        rawMainText = ""
        rawSecondText = ""
        rawHasSecond = false
        currentMainText = ""
        currentSecondText = ""
        hasSecondLine = false
        secondIsTranslation = false
        mainStaticLayout = null
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

            if (!dataDirty && !versionsChanged) {
                refreshCurrentLineFromCache()
                return
            }
            dataDirty = false
            lastVersionsCheck = SystemClock.elapsedRealtime()

            doReadAndUpdate(newVLyric, newVLyricFd)
        } catch (e: Throwable) {
            logE("readAndUpdate error", e)
        }
    }

    private fun doReadAndUpdate(newVLyric: Int, newVLyricFd: Int) {
        val oldVLyric = lastLyricVersion
        val oldVLyricFd = lastLyricFdVersion
        try {
            lastLyricVersion = newVLyric
            lastLyricFdVersion = newVLyricFd

            val uri = Uri.parse(PROVIDER_URI)

            // 1. FD 版本变化或首次加载 → 读全量
            var fdRead = false
            if (oldVLyricFd != newVLyricFd || (oldVLyricFd < 0 && lastLyricJson == "{}")) {
                try {
                    val fb = context.contentResolver.call(uri, "lyric_fd", null, null)
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
                            } else {
                                fdRead = false
                            }
                        } catch (_: Throwable) {
                        }
                    }
                } catch (e: Throwable) {
                    logE("read lyric_fd error", e)
                }
            }

            // 2. 轻量版本变化 → 合并 ctx（Provider 输出即最新）
            if ((oldVLyric != newVLyric || oldVLyric < 0) && (oldVLyricFd == newVLyricFd || !fdRead)) {
                try {
                    val lb = context.contentResolver.call(uri, "lyric", null, null)
                    val j = lb?.getString("n")
                    if (j != null) {
                        try {
                            val neu = JSONObject(j)
                            val emptyPush = !neu.has("l") && !neu.has("s") &&
                                !neu.has("title") && !neu.has("ctx")
                            if (emptyPush) {
                                resolveNoLyric()
                            } else if (!AodLyricDisplayPolicy.hasValidLyricLines(neu)) {
                                val old = try {
                                    JSONObject(lastLyricJson)
                                } catch (_: Throwable) {
                                    JSONObject("{}")
                                }
                                val newTitle = neu.optString("title", "").trim()
                                if (newTitle.isNotBlank()) lastSongTitle = newTitle
                                if (AodLyricDisplayPolicy.isSameSongLyricPayload(old, neu) &&
                                    shouldMergeLyricCtx(old, neu)
                                ) {
                                    neu.put("ctx", old.get("ctx"))
                                    lastLyricJson = neu.toString()
                                } else {
                                    lastLyricJson = neu.toString()
                                    if (newTitle.isNotBlank()) lastSongTitle = newTitle
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
                                val newTitle = neu.optString("title", "")
                                val lightEmpty = neu.optString("l", "").trim().isEmpty() &&
                                    neu.optString("s", "").trim().isEmpty() &&
                                    !neu.has("ctx")
                                val songChanged = newTitle.isNotBlank() &&
                                    lastSongTitle.isNotBlank() &&
                                    !AodLyricDisplayPolicy.isSameSongLyricPayload(lastSongTitle, newTitle)

                                if (songChanged) {
                                    purgeDisplayedLyrics()
                                    cachedCtx = null
                                    cachedLines = null
                                    if (newTitle.isNotBlank()) lastSongTitle = newTitle
                                    lastLyricJson = neu.toString()
                                } else if (lightEmpty) {
                                    cachedCtx = null
                                    cachedLines = null
                                    lastLyricJson = neu.toString()
                                    if (newTitle.isNotBlank()) lastSongTitle = newTitle
                                } else if (shouldMergeLyricCtx(old, neu)) {
                                    neu.put("ctx", old.get("ctx"))
                                    lastLyricJson = neu.toString()
                                } else {
                                    lastLyricJson = neu.toString()
                                    if (newTitle.isNotBlank()) lastSongTitle = newTitle
                                }
                                lastLyricVersion = newVLyric
                                lastLyricFdVersion = newVLyricFd
                                clearTrackGate()
                                dataDirty = false
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
        } catch (e: Throwable) {
            lastLyricVersion = oldVLyric
            lastLyricFdVersion = oldVLyricFd
            logE("doReadAndUpdate fail", e)
        }
    }

    private fun applyLyricFromJson() {
        try {
            val raw = lastLyricJson.trim().ifEmpty { "{}" }
            if (raw != lastLyricJson) lastLyricJson = raw
            val lo = JSONObject(raw)

            if (raw == "{}" || !AodLyricDisplayPolicy.hasValidLyricLines(lo)) {
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
                    cachedCtx = ctx
                    val lines = ArrayList<LyricLine>()
                    for (i in 0 until linesArr.length()) {
                        val o = linesArr.optJSONObject(i) ?: continue
                        val tm = o.optLong("tm", Long.MAX_VALUE)
                        val t = o.optString("t", "") ?: ""
                        if (t.isNotBlank()) {
                            lines.add(LyricLine(tm, t, o.optString("r", "") ?: ""))
                        }
                    }
                    if (lines.isNotEmpty()) cachedLines = lines
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
                val light = AodLyricDisplayPolicy.resolveLightLyricDisplay(l, s)
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
            for (controller in getMediaControllers()) {
                val state = controller.playbackState
                if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                    isPlaying = true
                    return
                }
            }
            isPlaying = false
        } catch (_: Throwable) {}
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
            var idx = if (pos >= 0) findCurrentLineIndex(lines, pos) else 0
            if (idx < 0) idx = 0

            val currentText = lines[idx].text
            val currentTrans = lines[idx].translation.takeIf { it.isNotBlank() } ?: ""
            val nextText = if (idx + 1 < lines.size) lines[idx + 1].text else ""

            val lightFields = try {
                AodLyricDisplayPolicy.parseLyricSnapshotFields(
                    JSONObject(lastLyricJson.trim().ifEmpty { "{}" })
                )
            } catch (_: Throwable) {
                AodLyricDisplayPolicy.LyricSnapshotFields()
            }
            val resolved = AodLyricDisplayPolicy.resolveCachedLineDisplay(
                currentText = currentText,
                lineTranslation = currentTrans,
                nextLineText = nextText,
                lightMain = lightFields.l,
                lightTranslation = lightFields.s,
                immersiveLyric = cfgImmersiveLyric,
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
            val useImmersiveFade = cfgImmersiveLyric &&
                mainChanged &&
                shouldDisplayLyric() &&
                HookUtils.isScreenInteractive(context)
            if (useImmersiveFade) {
                crossfadeImmersiveLineChange(displayMain, displaySecond, displayHasSecond)
            } else {
                if (cfgImmersiveLyric && mainChanged) {
                    cancelImmersiveLineFade()
                }
                applyLyricContentImmediate(displayMain, displaySecond, displayHasSecond)
            }
            finalizeLyricDisplayAfterContentUpdate()
        }
    }

    private fun applyLyricContentImmediate(main: String, second: String, hasSecond: Boolean) {
        currentMainText = main
        currentSecondText = second
        hasSecondLine = hasSecond

        if (cfgImmersiveLyric) {
            rebuildImmersiveLayouts()
            resizeKeepingBottom(computeLyricWidthPx())
        } else {
            val layout = buildMainLayout(main.ifBlank { " " })
            mainStaticLayout = layout
            resizeKeepingBottom(computeContentHeightPx(layout, hasSecond))
        }
        invalidate()
    }

    private fun cancelImmersiveLineFade(resetAlpha: Boolean = true) {
        immersiveFadeAnimator?.cancel()
        immersiveFadeAnimator = null
        if (resetAlpha) immersiveContentAlpha = 1f
    }

    /** 沉浸歌词切行：先淡出再换词淡入，总时长 250ms。 */
    private fun crossfadeImmersiveLineChange(main: String, second: String, hasSecond: Boolean) {
        cancelImmersiveLineFade(resetAlpha = false)

        fun startFadeIn() {
            immersiveFadeAnimator = ValueAnimator.ofFloat(immersiveContentAlpha, 1f).apply {
                duration = (immersiveFadeInMs * (1f - immersiveContentAlpha)).toLong()
                    .coerceIn(40L, immersiveFadeInMs)
                interpolator = immersiveFadeInterpolator
                addUpdateListener {
                    immersiveContentAlpha = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        immersiveFadeAnimator = null
                        immersiveContentAlpha = 1f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        immersiveFadeAnimator = null
                    }
                })
                start()
            }
        }

        fun swapAndFadeIn() {
            applyLyricContentImmediate(main, second, hasSecond)
            immersiveContentAlpha = 0f
            startFadeIn()
        }

        if (immersiveContentAlpha <= 0.05f) {
            swapAndFadeIn()
            return
        }

        immersiveFadeAnimator = ValueAnimator.ofFloat(immersiveContentAlpha, 0f).apply {
            duration = (immersiveFadeOutMs * immersiveContentAlpha).toLong()
                .coerceIn(40L, immersiveFadeOutMs)
            interpolator = immersiveFadeInterpolator
            addUpdateListener {
                immersiveContentAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    immersiveFadeAnimator = null
                    swapAndFadeIn()
                }

                override fun onAnimationCancel(animation: Animator) {
                    immersiveFadeAnimator = null
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

    /** 仅 trackKey 变化时清快照；标题 Session/Provider 常不一致，不能每轮询 purge。 */
    private fun detectTrackOrSongChange(): Boolean {
        val mediaTitle = readCurrentMediaTitle()
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        val trackChanged = AodLyricDisplayPolicy.shouldResetLyricForTrackKeyChange(
            lastKnownTrackKey,
            trackKey,
        )
        if (trackChanged) {
            staleProviderLyricSuppressed = false
            purgeDisplayedLyrics(resetProviderSnapshot = false)
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
        if (!AodLyricDisplayPolicy.isSameSongLyricPayload(old, neu)) return false
        val mediaTitle = readCurrentMediaTitle().trim().ifBlank { lastSongTitle.trim() }
        val oldTitle = old.optString("title", "").trim()
        val neuTitle = neu.optString("title", "").trim()
        if (oldTitle.isNotBlank() && mediaTitle.isNotBlank() &&
            !TrackLyricGate.titlesMatch(oldTitle, mediaTitle)
        ) {
            return false
        }
        if (neuTitle.isNotBlank() && mediaTitle.isNotBlank() &&
            !TrackLyricGate.titlesMatch(neuTitle, mediaTitle)
        ) {
            return false
        }
        return true
    }

    private fun isKeyguardLocked(): Boolean {
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
