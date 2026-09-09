package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.text.Layout
import android.text.TextPaint
import android.util.AttributeSet
import android.widget.TextView

/**
 * 单行歌曲信息（歌名 / 括号副标题 / 歌手）：超宽不画省略号，
 * 末尾用 [LyricTextFadeTruncate] 与歌词同款渐隐。
 *
 * 注意：中文等可断行文字在 maxLines=1 时，行宽常≈布局宽（多余字掉到第 2 行被裁掉），
 * 不能只靠 lineWidth>layoutWidth 判定，须用全文期望宽 / 可见末下标。
 */
internal class EndFadeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextView(context, attrs, defStyleAttr) {

    private val endFadeMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // 勿用 isSingleLine=true：会打开横向滚动，导致 layout 宽=全文宽、渐隐判定失效
        maxLines = 1
        ellipsize = null
        setHorizontallyScrolling(false)
    }

    override fun onDraw(canvas: Canvas) {
        val layout = this.layout
        val cs = text
        if (layout == null || layout.lineCount <= 0 || width <= 0 || height <= 0 || cs.isNullOrEmpty()) {
            super.onDraw(canvas)
            return
        }
        val contentLeft = compoundPaddingLeft.toFloat()
        val contentRight = (width - compoundPaddingRight).toFloat().coerceAtLeast(contentLeft + 1f)
        val contentW = contentRight - contentLeft
        if (!needsEndFade(layout, cs, contentW, paint)) {
            super.onDraw(canvas)
            return
        }

        val line = 0
        val start = layout.getLineStart(line)
        var end = layout.getLineEnd(line)
        if (end > start && end <= cs.length && cs[end - 1] == '\n') end--
        val fadeW = LyricTextFadeTruncate.resolveFadeWidthPx(
            text = cs,
            lineStart = start,
            lineEnd = end.coerceAtLeast(start),
            textSizePx = textSize,
        ) { s, e -> paint.measureText(cs, s, e) }
        val rtl = layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT
        val geo = LyricTextFadeTruncate.endFadeGeometry(
            lineLeft = contentLeft,
            lineRight = contentRight,
            layoutWidth = width.toFloat(),
            fadeWidthPx = fadeW.coerceAtLeast(textSize * LyricTextFadeTruncate.MIN_FADE_EM),
            rtl = rtl,
        )

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        try {
            super.onDraw(canvas)
            endFadeMaskPaint.shader = LinearGradient(
                geo.fadeOpaqueX, 0f, geo.fadeTransparentX, 0f,
                intArrayOf(Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            endFadeMaskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), endFadeMaskPaint)
        } finally {
            endFadeMaskPaint.xfermode = null
            endFadeMaskPaint.shader = null
            canvas.restoreToCount(layer)
        }
    }

    companion object {
        fun needsEndFade(
            layout: Layout,
            text: CharSequence,
            contentWidthPx: Float,
            paint: TextPaint,
        ): Boolean {
            if (contentWidthPx <= 1f || text.isEmpty()) return false
            val trimmedEnd = LyricTextFadeTruncate.trimTrailingWhitespaceEnd(text, 0, text.length)
            if (trimmedEnd <= 0) return false

            // 1) 全文单行期望宽超过可视宽（最可靠）
            val desired = try {
                Layout.getDesiredWidth(text, 0, trimmedEnd, paint)
            } catch (_: Throwable) {
                paint.measureText(text, 0, trimmedEnd)
            }
            if (LyricTextFadeTruncate.needsEndFade(desired, contentWidthPx)) return true

            // 2) 可断行语言：第 1 行末还没到全文 → 被 maxLines 裁掉
            val visibleEnd = LyricTextFadeTruncate.trimTrailingWhitespaceEnd(
                text,
                0,
                layout.getLineEnd(0).coerceIn(0, text.length),
            )
            if (visibleEnd < trimmedEnd) return true

            // 3) 布局产生了多行（仅画第 1 行）
            if (layout.lineCount > 1) return true

            // 4) 不可断长词：行宽超出布局框
            val lineW = layout.getLineWidth(0)
            val layoutW = layout.width.toFloat().coerceAtLeast(1f)
            return LyricTextFadeTruncate.needsEndFadeForLineWidth(lineW, layoutW) ||
                LyricTextFadeTruncate.needsEndFade(lineW, contentWidthPx)
        }
    }
}
