package com.leowalk.musiclockscreen.xposed

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field

/**
 * 媒体控件标题：括号内容缩小显示在标题右侧（同一 TextView，主标题优先完整显示）。
 */
object MediaTitleSubtitleHook {

    private const val TAG = "MusicLockScreen_MediaSubtitle"
    private const val TITLE_ROW_TAG = "music_lockscreen_media_title_row"
    private const val SUBTITLE_VIEW_TAG = "music_lockscreen_media_subtitle"
    private const val LINE_SUBTITLE_VIEW_TAG = "music_lockscreen_media_line_subtitle"

    private const val SUBTITLE_SIZE_RATIO = 0.72f
    private const val SUBTITLE_ALPHA = 140
    private const val LINE_SUBTITLE_ALPHA = 120
    /** 副标题保持较小；主标题可突破原单行高度限制。 */
    private const val LINE_MAIN_SIZE_RATIO = 1.0f
    private const val LINE_SUB_SIZE_RATIO = 0.32f
    private const val LINE_TITLE_OVER_ARTIST = 1.15f
    private const val LINE_MIN_SCALE = 0.78f
    private const val RAW_ARTIST_TAG = 0x7f140001
    private const val BASE_TITLE_SIZE_TAG = 0x7f140002
    private const val LINE_APPLIED_TAG = 0x7f140003

    private var module: XposedModule? = null
    private var mediaDataField: Field? = null
    private var mediaMetadataField: Field? = null
    private var holderField: Field? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module

        try {
            val vcClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                false,
                classLoader
            )
            val viewHolderClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
                false,
                classLoader
            )

            mediaDataField = vcClass.getDeclaredField("mediaData").apply { isAccessible = true }
            mediaMetadataField = vcClass.getDeclaredField("mediaMataData").apply { isAccessible = true }
            holderField = vcClass.getDeclaredField("holder").apply { isAccessible = true }

            val titleTextField = viewHolderClass.getDeclaredField("titleText").apply { isAccessible = true }
            val artistTextField = viewHolderClass.getDeclaredField("artistText").apply { isAccessible = true }

            val setInfoText = vcClass.getDeclaredMethod("setInfoText")
            module.hook(setInfoText).intercept { chain ->
                val result = chain.proceed()
                try {
                    val h = holderField?.get(chain.thisObject)
                    (artistTextField.get(h) as? TextView)?.let { invalidateArtistCache(it) }
                    applyInlineSubtitle(chain.thisObject, titleTextField, artistTextField)
                } catch (e: Throwable) {
                    logE("after setInfoText error", e)
                }
                result
            }

            val updateForegroundColors = vcClass.getDeclaredMethod("updateForegroundColors")
            module.hook(updateForegroundColors).intercept { chain ->
                val result = chain.proceed()
                try {
                    applyInlineSubtitle(chain.thisObject, titleTextField, artistTextField)
                } catch (e: Throwable) {
                    logE("after updateForegroundColors error", e)
                }
                result
            }

            logI("MediaTitleSubtitleHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun applyInlineSubtitle(controller: Any, titleTextField: Field, artistTextField: Field) {
        val holder = holderField?.get(controller) ?: return
        val titleText = titleTextField.get(holder) as? TextView ?: return

        val rawTitle = readRawTitle(controller) ?: titleText.text?.toString()
        if (rawTitle.isNullOrBlank()) {
            return
        }

        val mode = ConfigReader.titleBracketMode(titleText.context)
        val (main, sub) = TitleBracketHelper.splitBrackets(rawTitle)
        val artistText = artistTextField.get(holder) as? TextView

        if (mode == "line") {
            applyLineSubtitle(titleText, artistText, main, sub)
            return
        }

        restoreArtistText(artistText)
        unwrapTitleRow(titleText)

        titleText.text = when (mode) {
            "shrink" -> {
                if (sub.isEmpty()) {
                    rawTitle
                } else {
                    buildSpannableTitle(main, sub, titleText.currentTextColor)
                }
            }
            "hide" -> {
                if (sub.isEmpty() || main.isEmpty()) rawTitle else main
            }
            else -> rawTitle
        }
    }

    /**
     * 分行：主标题在上、括号副标题在下（垂直 LinearLayout）。
     * 主标题用接近原标题字号（可高于歌手、可突破原单行高度）；副标题保持较小；行距尽量紧。
     */
    private fun applyLineSubtitle(
        titleText: TextView,
        artistText: TextView?,
        main: String,
        sub: String
    ) {
        restoreArtistText(artistText)

        if (sub.isEmpty()) {
            unwrapTitleRow(titleText)
            titleText.text = main.ifEmpty { titleText.text }
            return
        }

        val displayMain = main.ifEmpty { titleText.text?.toString().orEmpty() }
        if (displayMain.isEmpty()) return

        val (row, subTv) = ensureTitleRow(titleText)
        val baseSize = rememberBaseSize(titleText)
        val titleColor = titleText.currentTextColor
        val availWidth = when {
            row.width > 0 -> row.width - row.paddingLeft - row.paddingRight
            titleText.width > 0 -> titleText.width
            else -> 0
        }
        val scale = computeLineScale(displayMain, sub, baseSize, availWidth)
        val artistSize = artistText?.textSize?.takeIf { it > 0f } ?: (baseSize * 0.72f)
        // 标题：原字号（宽度不足才缩小），且至少比歌手大一截；副标题固定比例，不随标题再放大
        val mainPx = maxOf(baseSize * LINE_MAIN_SIZE_RATIO * scale, artistSize * LINE_TITLE_OVER_ARTIST)
        val subPx = baseSize * LINE_SUB_SIZE_RATIO
        val appliedKey = "$displayMain|$sub|$mainPx|$subPx|$titleColor"

        // 仅当实际显示内容已正确时跳过，避免 setInfoText 冲掉文本后被误判为已应用
        if (titleText.getTag(LINE_APPLIED_TAG) == appliedKey &&
            titleText.text?.toString() == displayMain &&
            subTv.text?.toString() == sub
        ) {
            return
        }

        titleText.maxLines = 1
        titleText.ellipsize = TextUtils.TruncateAt.END
        titleText.includeFontPadding = false
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mainPx)
        titleText.text = displayMain

        subTv.visibility = View.VISIBLE
        subTv.maxLines = 1
        subTv.ellipsize = TextUtils.TruncateAt.END
        subTv.includeFontPadding = false
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, subPx)
        subTv.setTextColor(
            Color.argb(
                LINE_SUBTITLE_ALPHA,
                Color.red(titleColor),
                Color.green(titleColor),
                Color.blue(titleColor)
            )
        )
        subTv.text = sub
        titleText.setTag(LINE_APPLIED_TAG, appliedKey)

        if (availWidth <= 0) {
            row.post {
                if ((titleText.parent as? View)?.tag != TITLE_ROW_TAG) return@post
                applyLineSubtitle(titleText, artistText, main, sub)
            }
        }
    }

    private fun computeLineScale(
        main: String,
        sub: String,
        baseSize: Float,
        maxWidth: Int
    ): Float {
        if (maxWidth <= 0) return 1f
        val paint = TextPaint()
        var scale = 1f
        while (scale > LINE_MIN_SCALE) {
            paint.textSize = baseSize * LINE_MAIN_SIZE_RATIO * scale
            val mainOk = paint.measureText(main) <= maxWidth
            paint.textSize = baseSize * LINE_SUB_SIZE_RATIO * scale
            val subOk = paint.measureText(sub) <= maxWidth
            if (mainOk && subOk) return scale
            scale -= 0.02f
        }
        return LINE_MIN_SCALE
    }

    private fun rememberBaseSize(titleText: TextView): Float {
        val cached = titleText.getTag(BASE_TITLE_SIZE_TAG) as? Float
        if (cached != null && cached > 0f) return cached
        // 若已缩小过，不要把缩小后的字号当成基准
        val size = titleText.textSize
        titleText.setTag(BASE_TITLE_SIZE_TAG, size)
        return size
    }

    private fun ensureTitleRow(titleText: TextView): Pair<LinearLayout, TextView> {
        val parent = titleText.parent
        if (parent is LinearLayout && parent.tag == TITLE_ROW_TAG) {
            val subTv = parent.findViewWithTag(LINE_SUBTITLE_VIEW_TAG) as? TextView
                ?: createLineSubtitleView(titleText).also { parent.addView(it) }
            return parent to subTv
        }

        val outerParent = titleText.parent as? ViewGroup ?: run {
            logE("ensureTitleRow: titleText has no parent")
            val fallback = LinearLayout(titleText.context).apply { tag = TITLE_ROW_TAG }
            return fallback to createLineSubtitleView(titleText)
        }

        val titleLp = titleText.layoutParams
        val titleIndex = outerParent.indexOfChild(titleText)
        val titleId = titleText.id

        if (titleText.getTag(BASE_TITLE_SIZE_TAG) == null) {
            titleText.setTag(BASE_TITLE_SIZE_TAG, titleText.textSize)
        }

        val row = LinearLayout(titleText.context).apply {
            tag = TITLE_ROW_TAG
            orientation = LinearLayout.VERTICAL
            layoutParams = titleLp
            if (titleId != View.NO_ID) {
                id = titleId
            }
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        outerParent.removeView(titleText)
        titleText.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleText.maxLines = 1
        titleText.ellipsize = TextUtils.TruncateAt.END
        titleText.includeFontPadding = false
        // 交给 row 持有约束 id，title 用 NO_ID 避免重复
        titleText.id = View.NO_ID

        val subTv = createLineSubtitleView(titleText)
        row.addView(titleText)
        row.addView(subTv)
        outerParent.addView(row, titleIndex)
        return row to subTv
    }

    private fun createLineSubtitleView(titleText: TextView): TextView {
        return TextView(titleText.context).apply {
            tag = LINE_SUBTITLE_VIEW_TAG
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // 负边距收紧主副间距
                topMargin = -dp(context, 4)
            }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            typeface = titleText.typeface
            gravity = titleText.gravity
            visibility = View.GONE
        }
    }

    private fun restoreArtistText(artistText: TextView?) {
        if (artistText == null) return
        readRawArtist(artistText)?.let { artistText.text = it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readRawArtist(artistText: TextView): String? {
        val cached = artistText.getTag(RAW_ARTIST_TAG) as? String
        if (!cached.isNullOrBlank()) return cached

        val raw = artistText.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (raw != null) {
            artistText.setTag(RAW_ARTIST_TAG, raw)
        }
        return raw
    }

    fun invalidateArtistCache(artistText: TextView) {
        artistText.setTag(RAW_ARTIST_TAG, null)
    }

    private fun dp(context: android.content.Context, v: Int): Int {
        val density = context.resources.displayMetrics.density
        return (v * density).toInt()
    }

    private fun buildSpannableTitle(main: String, sub: String, titleColor: Int): CharSequence {
        val suffix = "($sub)"
        val full = if (main.isEmpty()) suffix else "$main $suffix"
        val subStart = if (main.isEmpty()) 0 else main.length + 1

        val ss = SpannableString(full)
        ss.setSpan(
            RelativeSizeSpan(SUBTITLE_SIZE_RATIO),
            subStart,
            full.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        ss.setSpan(
            ForegroundColorSpan(
                Color.argb(
                    SUBTITLE_ALPHA,
                    Color.red(titleColor),
                    Color.green(titleColor),
                    Color.blue(titleColor)
                )
            ),
            subStart,
            full.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return ss
    }

    private fun unwrapTitleRow(titleText: TextView) {
        val row = titleText.parent as? LinearLayout ?: return
        if (row.tag != TITLE_ROW_TAG) return

        val outerParent = row.parent as? ViewGroup ?: return
        val rowLp = row.layoutParams
        val rowIndex = outerParent.indexOfChild(row)

        row.findViewWithTag<View>(SUBTITLE_VIEW_TAG)?.let { row.removeView(it) }
        row.findViewWithTag<View>(LINE_SUBTITLE_VIEW_TAG)?.let { row.removeView(it) }
        row.removeView(titleText)

        val titleConstraintId = row.id
        if (titleConstraintId != View.NO_ID) {
            titleText.id = titleConstraintId
        }

        (titleText.getTag(BASE_TITLE_SIZE_TAG) as? Float)?.let { base ->
            titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, base)
        }
        titleText.setTag(BASE_TITLE_SIZE_TAG, null)
        titleText.setTag(LINE_APPLIED_TAG, null)
        titleText.includeFontPadding = true
        titleText.maxLines = 1

        outerParent.removeView(row)
        outerParent.addView(titleText, rowIndex, rowLp)
    }

    private fun readRawTitle(controller: Any): String? {
        return try {
            val metadata = mediaMetadataField?.get(controller) as? android.media.MediaMetadata
            if (metadata != null) {
                val fromMeta = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                if (!fromMeta.isNullOrBlank()) return fromMeta.trim()
            }
            val mediaData = mediaDataField?.get(controller)
            if (mediaData != null) {
                val songField = mediaData.javaClass.getDeclaredField("song").apply { isAccessible = true }
                val song = songField.get(mediaData) as? CharSequence
                if (!song.isNullOrBlank()) return song.toString().trim()
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            module?.log(android.util.Log.ERROR, TAG, msg, e)
        } else {
            module?.log(android.util.Log.ERROR, TAG, msg)
        }
    }
}
