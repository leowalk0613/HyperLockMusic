package com.leowalk.musiclockscreen.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 音乐锁屏用小时钟：屏顶约 10% 处，时间与日期同一行、同字号。
 * 文字样式 / MiBlur 透色 / 近白底深色字，对齐 [LockscreenLyricView]。
 */
class MusicMinimalClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val TAG_VIEW = "music_minimal_clock_overlay"
        private const val DEFAULT_TEXT_SP = 30f
        private const val DEFAULT_TOP_PERCENT = 10f
        private const val GAP = "  "
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)

    private var lineText: String = "--:--"
    private var albumTint: Int? = null

    private var miBlurActive = false
    private var miBlurOnLight = false
    private var miBlurBlendKey = 0

    private var cachedMiSansBold: Typeface? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (visibility != VISIBLE) return
            refreshTimeText()
            invalidate()
            scheduleNextTick()
        }
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTimeText()
            invalidate()
        }
    }
    private var receiverRegistered = false

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        applyTypeface()
        applyTextSize()
        refreshTimeText()
    }

    fun attachLayoutParams(screenHeight: Int = resources.displayMetrics.heightPixels): FrameLayout.LayoutParams {
        val top = (screenHeight * topPercent() / 100f).toInt().coerceAtLeast(0)
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = top
        }
    }

    fun setAlbumTint(color: Int?) {
        albumTint = color
        syncMiBlur()
        invalidate()
    }

    fun onWallpaperUpdated() {
        syncMiBlur()
        invalidate()
    }

    fun showForMusicLockscreen() {
        if (!WallpaperController.isShowing()) {
            hideForMusicLockscreenOff()
            return
        }
        if (!LockscreenNotificationController.shouldShowKeyguardOverlays()) {
            visibility = GONE
            stopTicking()
            clearMiBlur()
            return
        }
        if (HookUtils.isBouncerShowing(this)) {
            visibility = INVISIBLE
            stopTicking()
            clearMiBlur()
            return
        }
        applyStyleFromConfig()
        visibility = VISIBLE
        refreshTimeText()
        syncMiBlur()
        startTicking()
        invalidate()
    }

    /** 配置变更后刷新字号 / 高度（开关仍由 Controller 控制显隐） */
    fun applyStyleFromConfig() {
        applyTextSize()
        (layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            val want = (resources.displayMetrics.heightPixels * topPercent() / 100f).toInt()
            if (lp.topMargin != want) {
                lp.topMargin = want
                layoutParams = lp
            }
        }
        requestLayout()
        invalidate()
    }

    fun hideForMusicLockscreenOff() {
        stopTicking()
        clearMiBlur()
        visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerTimeReceiver()
        if (visibility == VISIBLE) startTicking()
    }

    override fun onDetachedFromWindow() {
        stopTicking()
        unregisterTimeReceiver()
        clearMiBlur()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        applyTextSize()
        val dens = resources.displayMetrics.density
        val padV = 6f * dens
        val fm = textPaint.fontMetrics
        val h = padV * 2 + (fm.bottom - fm.top)
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(w, h.toInt().coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val dens = resources.displayMetrics.density
        val padV = 6f * dens
        val fm = textPaint.fontMetrics
        val baseline = padV - fm.top
        canvas.drawText(lineText, width / 2f, baseline, textPaint)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            refreshTimeText()
            syncMiBlur()
            startTicking()
        } else {
            stopTicking()
            clearMiBlur()
        }
    }

    private fun refreshTimeText() {
        val now = Date()
        lineText = timeFormat.format(now) + GAP + dateFormat.format(now)
    }

    private fun scheduleNextTick() {
        handler.removeCallbacks(tickRunnable)
        val cal = Calendar.getInstance()
        val delay = (60_000L - (cal.get(Calendar.SECOND) * 1000L + cal.get(Calendar.MILLISECOND)))
            .coerceIn(500L, 60_000L)
        handler.postDelayed(tickRunnable, delay)
    }

    private fun startTicking() {
        handler.removeCallbacks(tickRunnable)
        scheduleNextTick()
    }

    private fun stopTicking() {
        handler.removeCallbacks(tickRunnable)
    }

    private fun registerTimeReceiver() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(timeReceiver, filter)
            receiverRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun unregisterTimeReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (_: Throwable) {
        }
        receiverRegistered = false
    }

    private fun applyTextSize() {
        textPaint.textSize = textSizeSp() * resources.displayMetrics.density
    }

    private fun textSizeSp(): Float {
        return try {
            ConfigReader.minimalClockSize(context)
        } catch (_: Throwable) {
            DEFAULT_TEXT_SP
        }
    }

    private fun topPercent(): Float {
        return try {
            ConfigReader.minimalClockTopY(context)
        } catch (_: Throwable) {
            DEFAULT_TOP_PERCENT
        }
    }

    private fun applyTypeface() {
        textPaint.typeface = resolveMiSans()
        textPaint.isFakeBoldText = false
    }

    private fun syncMiBlur() {
        if (visibility != VISIBLE || !HyperMiBlurHelper.isSupported(context)) {
            clearMiBlur()
            applyTextColors()
            return
        }
        val bgRef = contrastBackgroundColor()
        val tint = boostAlbumTint(albumTint ?: bgRef)
        val onLight = isNearWhiteBackground(bgRef)
        val blurAlphas = MinimalClockTextStylePolicy.miBlurAlphas(onLight)
        val blend = MinimalClockTextStylePolicy.miBlurBlendRgb(onLight, tint)
        val primary = if (onLight) {
            MinimalClockTextStylePolicy.rgb(22, 22, 24)
        } else {
            MinimalClockTextStylePolicy.rgb(255, 255, 255)
        }
        val over = if (onLight) {
            MinimalClockTextStylePolicy.argb(140, 0, 0, 0)
        } else {
            MinimalClockTextStylePolicy.argb(110, 255, 255, 255)
        }
        val blendKey = blend xor bgRef xor (if (onLight) 0xA1 else 0xA2)
        if (miBlurActive && blendKey == miBlurBlendKey && onLight == miBlurOnLight) {
            applyTextColors()
            return
        }
        val ok = HyperMiBlurHelper.applyTextBlend(
            view = this,
            blendColor = blend,
            primaryColor = primary,
            colorDark = onLight,
            enablePassBlurOnSelf = true,
            passBlurRadius = (40f * resources.displayMetrics.density).toInt().coerceIn(24, 80),
            blendAlpha = blurAlphas.blendAlpha,
            labAlpha = blurAlphas.labAlpha,
            overColor = over
        )
        if (ok) {
            miBlurActive = true
            miBlurOnLight = onLight
            miBlurBlendKey = blendKey
        } else {
            clearMiBlur()
        }
        applyTextColors()
    }

    private fun clearMiBlur() {
        if (miBlurActive || miBlurBlendKey != 0) {
            HyperMiBlurHelper.clearTextBlend(this)
        }
        miBlurActive = false
        miBlurOnLight = false
        miBlurBlendKey = 0
    }

    private fun applyTextColors() {
        val bgRef = contrastBackgroundColor()
        val onLight = isNearWhiteBackground(bgRef)
        val textAlpha = MinimalClockTextStylePolicy.CLOCK_TEXT_ALPHA
        val shadow = MinimalClockTextStylePolicy.shadowLayer(onLight)
        if (miBlurActive) {
            textPaint.color = MinimalClockTextStylePolicy.readableTextRgb(
                onLight,
                boostAlbumTint(albumTint ?: bgRef),
            )
            textPaint.alpha = textAlpha
            textPaint.setShadowLayer(shadow.radius, 0f, shadow.dy, shadow.colorArgb)
            return
        }
        val tint = boostAlbumTint(albumTint ?: bgRef)
        textPaint.color = MinimalClockTextStylePolicy.readableTextRgb(onLight, tint)
        textPaint.alpha = textAlpha
        textPaint.setShadowLayer(shadow.radius, 0f, shadow.dy, shadow.colorArgb)
    }

    private fun contrastBackgroundColor(): Int {
        sampleWallpaperBehind()?.let { return it }
        return albumTint ?: Color.WHITE
    }

    private fun sampleWallpaperBehind(): Int? {
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
                (topPercent() / 100f * screenH).toInt().coerceIn(0, screenH - 1)
            }
            val centerX = screenW / 2
            val y = ((centerY.toFloat() / screenH) * bmp.height).toInt()
                .coerceIn(0, bmp.height - 1)
            val x = ((centerX.toFloat() / screenW) * bmp.width).toInt()
                .coerceIn(0, bmp.width - 1)
            val band = (bmp.height / 40).coerceAtLeast(2)
            val y0 = (y - band).coerceAtLeast(0)
            val y1 = (y + band).coerceAtMost(bmp.height - 1)
            val x0 = (x - bmp.width / 6).coerceAtLeast(0)
            val x1 = (x + bmp.width / 6).coerceAtMost(bmp.width - 1)
            val stepX = ((x1 - x0) / 10).coerceAtLeast(1)
            val stepY = ((y1 - y0) / 5).coerceAtLeast(1)
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

    private fun colorLuminance(color: Int): Float {
        return (0.2126f * Color.red(color) +
            0.7152f * Color.green(color) +
            0.0722f * Color.blue(color)) / 255f
    }

    private fun isNearWhiteBackground(color: Int): Boolean {
        if (colorLuminance(color) < 0.88f) return false
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1] < 0.18f
    }

    private fun boostAlbumTint(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.35f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.12f).coerceIn(0.35f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun resolveMiSans(): Typeface {
        cachedMiSansBold?.let { return it }
        for (path in MinimalClockTextStylePolicy.CLOCK_TYPEFACE_PATHS) {
            try {
                val file = java.io.File(path)
                if (!file.exists()) continue
                val tf = Typeface.createFromFile(file)
                cachedMiSansBold = tf
                return tf
            } catch (_: Throwable) {
            }
        }
        val fallback = if (MinimalClockTextStylePolicy.clockTypefaceFallbackBold()) {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        cachedMiSansBold = fallback
        return fallback
    }
}
