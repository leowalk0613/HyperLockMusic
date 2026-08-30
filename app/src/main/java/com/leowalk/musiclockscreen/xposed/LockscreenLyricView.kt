package com.leowalk.musiclockscreen.xposed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.graphics.*
import android.media.session.MediaSessionManager
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

    // ============================================================
    // 配置
    // ============================================================
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
        if (!shouldDisplayLyric()) {
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

        // 1. 渐变遮罩（非沉浸模式；沉浸歌词直接叠在壁纸上）
        if (!cfgImmersiveLyric) {
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
        onTrackMayHaveChanged()
        if (visibility == VISIBLE) {
            invalidate()
        }
    }

    /** 切歌时强制重拉歌词（AOD 下 observer 可能不触发）。 */
    fun onTrackMayHaveChanged() {
        dataDirty = true
        cachedLines = null
        cachedCtx = null
        lastVersionsCheck = 0
        lastLyricVersion = -1
        lastLyricFdVersion = -1
        lastKnownTrackKey = null
        if (isMusicLockscreenActive() && isKeyguardLocked()) {
            startPolling()
            handler.post { readAndUpdate() }
        }
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
                    if (cfgImmersiveLyric) {
                        showFogBackground = false
                        applyImmersiveTextColors()
                    } else {
                        showFogBackground = true
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
        fogBuildGeneration++
        showFogBackground = false
        clearFogCaches()
        cachedLines = null
        cachedCtx = null
        lastLyricJson = "{}"
        lastLyricVersion = -1
        lastLyricFdVersion = -1
        lastSongTitle = ""
        hasLyric = false
        clearLyricDisplay()
        alpha = 0f
        visibility = GONE
        invalidate()
    }

    /** 解锁离开锁屏：仅隐藏，保留数据供再次锁屏恢复 */
    fun onLeftKeyguard() {
        cancelImmersiveLineFade()
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
        applyLyricConfig()
        startPolling()
        refreshNow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPolling()
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
            }
            GONE -> {
                // 音乐锁屏 + 锁屏/AOD 时保持轮询，否则切歌后歌词不会刷新
                if (!isMusicLockscreenActive() || !isKeyguardLocked()) {
                    stopPolling()
                }
                alpha = 0f
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
                    handler.post { readAndUpdate() }
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
                val idxShow = cursor.getColumnIndex("show_lyric")
                val idxSize = cursor.getColumnIndex("lyric_size")
                val idxSwap = cursor.getColumnIndex("swap_lyric")
                val idxWidth = cursor.getColumnIndex("lyric_width")
                val idxBgAnchorY = cursor.getColumnIndex("lyric_bg_anchor_y")
                val idxImmersive = cursor.getColumnIndex("immersive_lyric")
                val idxHideBg = cursor.getColumnIndex("lyric_hide_background")
                val idxAlign = cursor.getColumnIndex("lyric_align")

                if (idxShow >= 0) cfgShowLyric = cursor.getInt(idxShow) != 0
                if (idxSize >= 0) cfgLyricSize = cursor.getFloat(idxSize)
                if (idxSwap >= 0) cfgSwapLyric = cursor.getInt(idxSwap) != 0
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
                if (styleChanged) {
                    mainStaticLayout = null
                    immersiveSecondStaticLayout = null
                    invalidate()
                }
                updateVisibilityState()
                MusicLockscreenManager.showAlbumOverlay()
                KeepScreenController.sync()
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
            applyImmersiveTextColors()
        } else {
            mainPaint.textSize = cfgLyricSize * density
            secondPaint.textSize = cfgLyricSize * 0.8f * density
            mainPaint.typeface = Typeface.DEFAULT_BOLD
            mainPaint.isFakeBoldText = false
            secondPaint.typeface = Typeface.DEFAULT
            secondPaint.isFakeBoldText = false
            mainPaint.color = Color.WHITE
            secondPaint.color = Color.argb(140, 255, 255, 255)
        }

        mainPaint.setShadowLayer(10f, 0f, 3f, Color.argb(230, 0, 0, 0))
        secondPaint.setShadowLayer(7f, 1f, 3f, Color.argb(210, 0, 0, 0))

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

    /** 沉浸歌词：白字混入专辑主色；翻译同步略染色 */
    private fun applyImmersiveTextColors() {
        val tint = boostAlbumTint(fogTintColor ?: Color.WHITE)
        val mainColor = blendTextColor(Color.WHITE, tint, immersiveTintWeight)
        mainPaint.color = mainColor
        secondPaint.color = Color.argb(
            160,
            Color.red(mainColor),
            Color.green(mainColor),
            Color.blue(mainColor)
        )
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
            cfgShowLyric &&
            isKeyguardLocked() &&
            !isBouncerShowing() &&
            !shadeOpen &&
            LockscreenNotificationController.shouldShowKeyguardOverlays() &&
            hasLyric &&
            isPlaying &&
            hasDisplayableText()
    }

    /** 沉浸歌词模式且当前有歌词正在显示（占用专辑区块）。 */
    fun isImmersiveLyricDisplayActive(): Boolean {
        return cfgImmersiveLyric && shouldDisplayLyric()
    }

    private fun updateVisibilityState() {
        if (shouldDisplayLyric()) {
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
            } else {
                alpha = 1f
                elevation = 48f * resources.displayMetrics.density
                translationZ = elevation
                invalidate()
            }
            try {
                bringToFront()
            } catch (_: Throwable) {
            }
            if (cfgImmersiveLyric) {
                MusicLockscreenManager.showAlbumOverlay()
            }
        } else {
            alpha = 0f
            setLayerType(View.LAYER_TYPE_NONE, null)
            if (!isMusicLockscreenActive() || !hasLyric) {
                clearLyricDisplay()
            }
            visibility = View.GONE
            if (cfgImmersiveLyric) {
                MusicLockscreenManager.showAlbumOverlay()
                MediaFollowController.requestReflow()
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

            // 1. FD 版本变化 → 读全量
            var fdRead = false
            if (oldVLyricFd != newVLyricFd) {
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
                        lastLyricJson = String(bos.toByteArray(), Charsets.UTF_8)
                        fdRead = true
                        try {
                            val jo = JSONObject(lastLyricJson)
                            val t = jo.optString("title", "")
                            if (t.isNotBlank()) lastSongTitle = t
                            if (!hasValidLyricLines(jo)) {
                                cachedCtx = null
                                cachedLines = null
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (e: Throwable) {
                    logE("read lyric_fd error", e)
                }
            }

            // 2. 轻量版本变化 → 合并 ctx
            // 当 fd 版本未变化，或 fd 已被清空（切到纯音乐/无歌词）时，也必须处理轻量歌词，
            // 否则旧歌词 JSON 会残留、锁屏继续显示上一首歌的歌词。
            if (oldVLyric != newVLyric && (oldVLyricFd == newVLyricFd || !fdRead)) {
                try {
                    val lb = context.contentResolver.call(uri, "lyric", null, null)
                    val j = lb?.getString("n")
                    if (j != null) {
                        try {
                            val old = JSONObject(lastLyricJson)
                            val neu = JSONObject(j)
                            val emptyPush = !neu.has("l") && !neu.has("s")
                                    && !neu.has("title") && !neu.has("ctx")
                            if (emptyPush) {
                                lastLyricJson = "{}"
                                cachedCtx = null
                                cachedLines = null
                                lastSongTitle = ""
                            } else {
                                val newTitle = neu.optString("title", "")
                                val lightEmpty = neu.optString("l", "").trim().isEmpty() &&
                                    neu.optString("s", "").trim().isEmpty() &&
                                    !neu.has("ctx")
                                val songChanged = newTitle.isNotBlank() &&
                                    lastSongTitle.isNotBlank() &&
                                    newTitle != lastSongTitle

                                if (songChanged || lightEmpty) {
                                    cachedCtx = null
                                    cachedLines = null
                                    if (newTitle.isNotBlank()) lastSongTitle = newTitle
                                    lastLyricJson = neu.toString()
                                } else if (!neu.has("ctx") && old.has("ctx")) {
                                    neu.put("ctx", old.get("ctx"))
                                    lastLyricJson = neu.toString()
                                } else {
                                    lastLyricJson = neu.toString()
                                    if (newTitle.isNotBlank()) lastSongTitle = newTitle
                                }
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
            val lo = JSONObject(lastLyricJson)
            val l = lo.optString("l", "") ?: ""
            val s = lo.optString("s", "") ?: ""
            val ctx = lo.optJSONObject("ctx")

            updatePlayingState()

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
            val newHasLyric = (hasLines || hasLight) && hasValidLyricLines(lo)
            if (newHasLyric != hasLyric) {
                hasLyric = newHasLyric
                if (!newHasLyric) {
                    clearLyricDisplay()
                }
                updateVisibilityState()
                requestLayout()
            } else if (!newHasLyric) {
                clearLyricDisplay()
                updateVisibilityState()
            }

            if (cachedLines != null && cachedLines!!.isNotEmpty()) {
                refreshCurrentLineFromCache()
                return
            }

            val lines = cachedLines
            val useCache = lines != null && lines!!.isNotEmpty()

            // 主行 / 副行统一判定：
            // - 有全量歌词时：副行优先取当前行的翻译（r 字段，明确是翻译才参与互换），
            //   无翻译则副行 = 下一句歌词（永远不互换）
            // - 轻量歌词（无全量字节）：无 r 可用，无法确认 s 是否翻译，保守不互换
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
                newMain = l.ifBlank { " " }
                newSecond = s
                newSecondIsTranslation = false
            }

            setLyricLines(newMain, newSecond, newSecond.isNotBlank(), newSecondIsTranslation)
        } catch (e: Throwable) {
            logE("applyLyricFromJson error", e)
        }
    }

    private fun updatePlayingState() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayingCheck < 1000) return
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

            updateVisibilityState()

            val onKeyguard = isKeyguardLocked()
            val isBouncer = isBouncerShowing()
            if (isMusicLockscreenActive() && onKeyguard && !isBouncer) {
                refreshNow()
                KeepScreenController.sync()
            } else if (isMusicLockscreenActive() && onKeyguard) {
                // 歌词暂不可见（如 INVISIBLE 定位中）仍拉取数据，AOD 切歌不丢
                readAndUpdate()
            }

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
            val lines = cachedLines
            if (lines == null || lines.isEmpty()) {
                return
            }

            val pos = getCurrentPosition()
            var idx = if (pos >= 0) findCurrentLineIndex(lines, pos) else 0
            if (idx < 0) idx = 0

            val currentText = lines[idx].text
            val currentTrans = lines[idx].translation.takeIf { it.isNotBlank() } ?: ""
            val prevText = if (idx > 0) lines[idx - 1].text else ""
            val nextText = if (idx + 1 < lines.size) lines[idx + 1].text else ""

            val newMain = currentText.ifBlank { " " }
            val newSecond = if (currentTrans.isNotEmpty()) {
                currentTrans
            } else if (!cfgImmersiveLyric) {
                nextText
            } else {
                ""
            }
            val newHasSecond = newSecond.isNotBlank()
            val isTranslationLine = currentTrans.isNotEmpty()

            setLyricLines(newMain, newSecond, newHasSecond, isTranslation = isTranslationLine)
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
            for (controller in getMediaControllers()) {
                val state = controller.playbackState
                if (state != null) {
                    val playing = state.state == PlaybackState.STATE_PLAYING
                    if (playing || isPlaying) {
                        val now = SystemClock.elapsedRealtime()
                        val delta = now - posBaseTime
                        if (delta > 2000 || !isPlaying) {
                            val p = state.position
                            isPlaying = playing
                            posBase = p
                            posBaseTime = now
                            return p
                        } else {
                            return posBase + delta
                        }
                    }
                }
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
        // 判断是否有翻译：第二行是翻译且非空
        val hasTrans = secondIsTranslation && rawHasSecond && rawSecondText.isNotBlank()

        val (displayMain, displaySecond, displayHasSecond) = if (cfgSwapLyric && hasTrans) {
            // 有翻译且开关开启：互换 — 主行显示翻译，副行显示原文
            Triple(rawSecondText, rawMainText, true)
        } else {
            // 无翻译、开关关闭、或第二行是下一句歌词：正常显示
            Triple(rawMainText, rawSecondText, rawHasSecond)
        }

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

    /** AOD 下 NotificationListener 组件可能拿不到会话，回退 getActiveSessions(null) */
    private fun getMediaControllers(): List<android.media.session.MediaController> {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = android.content.ComponentName(
                context, "com.leowalk.musiclockscreen.NotificationListenerServiceKt"
            )
            val withComponent = try {
                msm.getActiveSessions(component)
            } catch (_: Throwable) {
                emptyList()
            }
            if (withComponent.isNotEmpty()) {
                withComponent
            } else {
                try {
                    msm.getActiveSessions(null)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** 标题或 trackKey 变化时强制重拉歌词（AOD observer 常不可靠） */
    private fun detectTrackOrSongChange(): Boolean {
        val mediaTitle = readCurrentMediaTitle()
        val trackKey = AlbumArtResolver.getCachedTrackKey()
        var changed = false
        if (mediaTitle.isNotBlank() && lastSongTitle.isNotBlank() && mediaTitle != lastSongTitle) {
            changed = true
        }
        if (trackKey != null && lastKnownTrackKey != null && trackKey != lastKnownTrackKey) {
            changed = true
        }
        if (changed) {
            cancelImmersiveLineFade()
            cachedLines = null
            cachedCtx = null
            dataDirty = true
            lastLyricJson = "{}"
            mainStaticLayout = null
            immersiveSecondStaticLayout = null
            lastLyricVersion = -1
            lastLyricFdVersion = -1
        }
        if (mediaTitle.isNotBlank()) lastSongTitle = mediaTitle
        if (trackKey != null) lastKnownTrackKey = trackKey
        return changed
    }

    private fun hasValidLyricLines(json: JSONObject): Boolean {
        val l = json.optString("l", "").trim()
        val s = json.optString("s", "").trim()
        if (l.isNotEmpty() || s.isNotEmpty()) return true
        val ctx = json.optJSONObject("ctx") ?: return false
        val linesArr = ctx.optJSONArray("lines") ?: return false
        for (i in 0 until linesArr.length()) {
            val t = linesArr.optJSONObject(i)?.optString("t", "")?.trim().orEmpty()
            if (t.isNotEmpty()) return true
        }
        return false
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
        return try {
            val root = rootView
            if (root == null) return false
            val res = context.resources
            val id = res.getIdentifier("keyguard_bouncer_container", "id", context.packageName)
            if (id == 0) return false
            val v = root.findViewById<View>(id)
            v != null && v.visibility == View.VISIBLE && v.isShown
        } catch (e: Throwable) {
            false
        }
    }

    private fun logI(msg: String) {
        android.util.Log.i("HyperLockMusic_Lyric", msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e("HyperLockMusic_Lyric", msg, e)
    }
}
