package com.leowalk.musiclockscreen.xposed

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
import android.view.Gravity
import android.view.View
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
 * - 固定位置：背景底边按屏幕高度百分比定位，内容增高时向上延展
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

    // ============================================================
    // 渐变遮罩（专辑下半主色调 + 半透明黑，自下而上消散）
    // ============================================================
    private var fogTintColor: Int? = null
    /** 是否绘制渐变遮罩背景（歌词文字不受此影响） */
    private var showFogBackground = false
    private var fogBuildGeneration = 0
    /** 歌词背景底边锚点（父容器 y），锁定后高度变化只向上延展 */
    private var fixedAnchorBottomInParent: Int? = null

    // ============================================================
    // 配置
    // ============================================================
    private var cfgShowLyric: Boolean = true
    private var cfgLyricSize: Float = 20f
    private var cfgSwapLyric: Boolean = true
    /** 歌词区域宽度：占专辑宽度的百分比（默认 100 = 与专辑同宽） */
    private var cfgLyricWidth: Float = 100f
    /** 歌词背景底边微调（dp）：正值下移，负值上移 */
    private var cfgLyricBgOffsetY: Float = 0f
    /** 歌词背景底边占屏幕高度百分比 */
    private var cfgLyricBgAnchorY: Float = 62f

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
        val screenWidth = resources.displayMetrics.widthPixels

        if (!shouldDisplayLyric()) {
            setMeasuredDimension(0, 0)
            return
        }

        val mainText = currentMainText.ifBlank { " " }
        val layout = buildMainLayout(mainText)

        mainStaticLayout = layout

        val secondHeight = if (hasSecondLine) {
            val sfm = secondPaint.fontMetrics
            sfm.bottom - sfm.top
        } else 0f
        val textHeight = layout.height.toFloat() +
            (if (hasSecondLine) lineGapPx + secondHeight else 0f)

        // View 宽度 = 专辑宽度 × 宽度百分比；高度自适应歌词显示高度
        val lyricWidth = computeLyricWidthPx()
        val desiredHeight = (textHeight + vPaddingPx * 2).toInt()

        setMeasuredDimension(
            resolveSizeAndState(lyricWidth, widthMeasureSpec, 0),
            resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        )

        updateVerticalAlignment(desiredHeight)
    }

    /** 背景底边按屏幕高度百分比定位；底边锁定，内容增高时向上延展。 */
    private fun updateVerticalAlignment(desiredHeight: Int) {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        val parentView = parent as? View ?: return

        if (fixedAnchorBottomInParent == null) {
            val parentLoc = IntArray(2)
            parentView.getLocationOnScreen(parentLoc)
            fixedAnchorBottomInParent = computeLyricAnchorBottomInParent(parentLoc[1])
        }

        val density = resources.displayMetrics.widthPixels / 360f
        val offsetPx = cfgLyricBgOffsetY * density
        val newTop = (fixedAnchorBottomInParent!! - desiredHeight + offsetPx).toInt()
        applyTopMargin(lp, newTop)
    }

    /** 歌词背景底边锚点（父容器 y）：屏幕高度 × 用户设定百分比。 */
    private fun computeLyricAnchorBottomInParent(parentTopOnScreen: Int): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        val anchorScreenY = (screenHeight * cfgLyricBgAnchorY / 100f).toInt()
        return anchorScreenY - parentTopOnScreen
    }

    private fun clearFixedPosition() {
        fixedAnchorBottomInParent = null
    }

    private fun applyTopMargin(lp: FrameLayout.LayoutParams, top: Int) {
        if (lp.topMargin == top) return
        animate().cancel()
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        lp.topMargin = top
        layoutParams = lp
    }

    /** 歌词区域宽度（px）：专辑宽度 × 用户可调的宽度百分比 */
    private fun computeLyricWidthPx(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val sizePercent = ConfigReader.albumSize(context)
        val albumWidth = screenWidth * sizePercent / 100f
        return (albumWidth * cfgLyricWidth / 100f).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        if (!shouldDisplayLyric()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. 渐变半透明黑色遮罩（专辑下半主色调）
        drawFogBackground(canvas, w, h)

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
        val h = height.toFloat()
        // 内容底部对齐到 view 底部内边距之上
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
            // 基线取 contentBottom - sfm.bottom，使第二行文字底部贴合内容底部，避免被父容器裁剪
            val secondBaseline = contentBottom - sfm.bottom
            drawTextCentered(canvas, secondText, w / 2f, secondBaseline, secondPaint, contentWidth.toInt())
        }
    }

    /**
     * 居中绘制单行文本，超长则加省略号
     */
    private fun drawTextCentered(canvas: Canvas, text: String, centerX: Float, y: Float, paint: Paint, maxWidthPx: Int) {
        var displayText = text.ifBlank { " " }
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT

        val textWidth = paint.measureText(displayText)
        val x: Float

        if (textWidth <= maxWidthPx) {
            // 没超宽，直接居中
            x = centerX - textWidth / 2f
        } else {
            // 超长，加省略号后居中
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

    /**
     * 渐变遮罩：取专辑下半主色调，与黑色混合后自下而上半透明消散。
     */
    private fun drawFogBackground(canvas: Canvas, w: Float, h: Float) {
        if (!showFogBackground) return
        val tint = fogTintColor ?: return

        val cornerRadius = ConfigReader.albumCorner(context) * resources.displayMetrics.density
        val rect = RectF(0f, 0f, w, h)
        val path = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(path)
        val gradient = LinearGradient(
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
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(rect, paint)
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
    }

    /** 切歌 / 换封面：先隐藏渐变遮罩，等壁纸专辑更新后再生成。 */
    fun onWallpaperAlbumPending() {
        fogBuildGeneration++
        showFogBackground = false
        clearFogCaches()
        if (visibility == VISIBLE) {
            invalidate()
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
                    showFogBackground = true
                    invalidate()
                    if (ownsAlbumCopy && !album.isRecycled) album.recycle()
                }
            } catch (_: Throwable) {
                if (ownsAlbumCopy && !album.isRecycled) album.recycle()
            }
        }.start()
    }

    /** 渐变遮罩是否已生成（用于锁屏时补渲染）。 */
    fun isFogBackgroundReady(): Boolean {
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
        fogBuildGeneration++
        showFogBackground = false
        clearFixedPosition()
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
        clearFixedPosition()
        animate().cancel()
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        alpha = 0f
        visibility = GONE
    }

    /** 重新进入锁屏：按当前状态刷新可见性 */
    fun onKeyguardShown() {
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
        if (visibility == VISIBLE) {
            dataDirty = true
            lastVersionsCheck = 0
            startPolling()
            refreshNow()
        } else {
            stopPolling()
            alpha = 0f
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
            val uri = Uri.parse(CONFIG_URI)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idxShow = cursor.getColumnIndex("show_lyric")
                val idxSize = cursor.getColumnIndex("lyric_size")
                val idxSwap = cursor.getColumnIndex("swap_lyric")
                val idxWidth = cursor.getColumnIndex("lyric_width")
                val idxBgOffsetY = cursor.getColumnIndex("lyric_bg_offset_y")
                val idxBgAnchorY = cursor.getColumnIndex("lyric_bg_anchor_y")

                if (idxShow >= 0) cfgShowLyric = cursor.getInt(idxShow) != 0
                if (idxSize >= 0) cfgLyricSize = cursor.getFloat(idxSize)
                if (idxSwap >= 0) cfgSwapLyric = cursor.getInt(idxSwap) != 0
                if (idxWidth >= 0) cfgLyricWidth = cursor.getFloat(idxWidth)
                var positionChanged = false
                if (idxBgOffsetY >= 0) {
                    val newOffset = cursor.getFloat(idxBgOffsetY)
                    if (newOffset != cfgLyricBgOffsetY) positionChanged = true
                    cfgLyricBgOffsetY = newOffset
                }
                if (idxBgAnchorY >= 0) {
                    val newAnchor = cursor.getFloat(idxBgAnchorY)
                    if (newAnchor != cfgLyricBgAnchorY) positionChanged = true
                    cfgLyricBgAnchorY = newAnchor
                }
                if (positionChanged) clearFixedPosition()

                cursor.close()
                applyLyricStyle()
                updateVisibilityState()
            }
        } catch (e: Throwable) {
            logE("applyLyricConfig error", e)
        }
    }

    private fun applyLyricStyle() {
        val density = resources.displayMetrics.density

        mainPaint.textSize = cfgLyricSize * density
        secondPaint.textSize = cfgLyricSize * 0.8f * density

        mainPaint.color = Color.WHITE
        secondPaint.color = Color.argb(140, 255, 255, 255)

        mainPaint.setShadowLayer(10f, 0f, 3f, Color.argb(230, 0, 0, 0))
        secondPaint.setShadowLayer(7f, 1f, 3f, Color.argb(210, 0, 0, 0))

        val lp = layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = 0
            layoutParams = lp
        }

        requestLayout()
        invalidate()
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
            hasLyric &&
            isPlaying &&
            hasDisplayableText()
    }

    private fun updateVisibilityState() {
        if (shouldDisplayLyric()) {
            animate().cancel()
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            if (visibility != View.VISIBLE) visibility = View.VISIBLE
            alpha = 1f
            requestLayout()
            invalidate()
        } else {
            alpha = 0f
            setLayerType(View.LAYER_TYPE_NONE, null)
            if (!isMusicLockscreenActive() || !hasLyric) {
                clearLyricDisplay()
            }
            visibility = View.GONE
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
    }

    private fun readAndUpdate() {
        try {
            val now = SystemClock.elapsedRealtime()
            if (!dataDirty && now - lastVersionsCheck < 5000) {
                refreshCurrentLineFromCache()
                return
            }
            dataDirty = false
            lastVersionsCheck = now

            val uri = Uri.parse(PROVIDER_URI)
            var newVLyric = -1
            var newVLyricFd = -1
            try {
                val vb = context.contentResolver.call(uri, "versions", null, null)
                if (vb != null) {
                    newVLyric = vb.getInt("lyric", -1)
                    newVLyricFd = vb.getInt("lyricfd", -1)
                    if (newVLyric == lastLyricVersion && newVLyricFd == lastLyricFdVersion) {
                        if (!hasValidLyricLines(JSONObject(lastLyricJson))) {
                            cachedCtx = null
                            cachedLines = null
                            if (hasLyric) {
                                hasLyric = false
                                clearLyricDisplay()
                                updateVisibilityState()
                            }
                            return
                        }
                        refreshCurrentLineFromCache()
                        return
                    }
                }
            } catch (_: Throwable) {}

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

            if (cachedLines != null && cachedLines!!.isNotEmpty() && isPlaying) {
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
                } else if (idx >= 0 && idx + 1 < lines!!.size) {
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
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(
                android.content.ComponentName(context,
                    "com.leowalk.musiclockscreen.NotificationListenerServiceKt")
            )
            for (controller in sessions) {
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
            }

            val interval = if (isPlaying && shouldDisplayLyric()) 200L else 1000L
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
            if (pos < 0) return

            var idx = findCurrentLineIndex(lines, pos)
            if (idx < 0) idx = 0

            val currentText = lines[idx].text
            val currentTrans = lines[idx].translation.takeIf { it.isNotBlank() } ?: ""
            val prevText = if (idx > 0) lines[idx - 1].text else ""
            val nextText = if (idx + 1 < lines.size) lines[idx + 1].text else ""

            val newMain = currentText.ifBlank { " " }
            // 有翻译优先显示翻译，无翻译才显示下一句（翻译参与互换，下一句不互换）
            val newSecond = if (currentTrans.isNotEmpty()) currentTrans else nextText
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
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions: List<android.media.session.MediaController> = msm.getActiveSessions(
                android.content.ComponentName(context, "com.leowalk.musiclockscreen.NotificationListenerServiceKt")
            )

            for (controller in sessions) {
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

        android.util.Log.i("MusicLockScreen_Lyric",
            "setLyricLines main=[$rawMain] second=[$rawSecond] hasSecond=$hasSecond isTranslation=$isTranslation")

        applySwapIfNeeded()
    }

    /**
     * 根据互换开关和翻译有无，决定实际显示的主行/副行。
     * 主行行数变化时触发向上滚动刷新。
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

        android.util.Log.i("MusicLockScreen_Lyric",
            "swapResult hasTrans=$hasTrans => main=[$displayMain] second=[$displaySecond]")

        val mainChanged = currentMainText != displayMain
        val secondChanged = currentSecondText != displaySecond
        val hasSecondChanged = hasSecondLine != displayHasSecond

        if (mainChanged || secondChanged || hasSecondChanged) {
            currentMainText = displayMain
            currentSecondText = displaySecond
            hasSecondLine = displayHasSecond

            // 直接构建新布局，无滚动动画，歌词原地更新
            mainStaticLayout = buildMainLayout(displayMain.ifBlank { " " })

            requestLayout()
            invalidate()
            updateVisibilityState()
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
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .build()
    }

    // ============================================================
    // 工具
    // ============================================================
    private fun readCurrentMediaTitle(): String {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(
                android.content.ComponentName(context, "com.leowalk.musiclockscreen.NotificationListenerServiceKt")
            )
            for (controller in sessions) {
                val title = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                if (!title.isNullOrBlank()) return title
            }
            ""
        } catch (_: Throwable) {
            ""
        }
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
        android.util.Log.i("MusicLockScreen_Lyric", msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e("MusicLockScreen_Lyric", msg, e)
    }
}
