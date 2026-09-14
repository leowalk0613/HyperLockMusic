package com.leowalk.musiclockscreen.xposed

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
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
 * 媒体控件标题：括号缩小 / 隐藏 / 分行。
 *
 * 分行：主标题单行；有括号才显示副标题；无括号保持系统单行歌名。
 */
object MediaTitleSubtitleHook {

    private const val TAG = "HyperLockMusic_MediaSubtitle"
    private const val TITLE_ROW_TAG = "music_lockscreen_media_title_row"
    private const val LINE_SUBTITLE_VIEW_TAG = "music_lockscreen_media_line_subtitle"

    private const val SUBTITLE_SIZE_RATIO = 0.72f
    private const val SUBTITLE_ALPHA = 140
    private const val RAW_ARTIST_TAG = 0x7f140001
    private const val BASE_TITLE_SIZE_TAG = 0x7f140002
    private const val LINE_APPLIED_TAG = 0x7f140003
    private const val ARTIST_TOP_MARGIN_TAG = 0x7f140004

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
                classLoader,
            )
            val viewHolderClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
                false,
                classLoader,
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
        artistText?.let { applyArtistEndFadeMode(it) }

        titleText.text = when (mode) {
            "shrink" -> {
                if (sub.isEmpty()) rawTitle
                else buildSpannableTitle(main, sub, titleText.currentTextColor)
            }
            "hide" -> {
                when {
                    sub.isEmpty() -> rawTitle
                    main.isEmpty() -> rawTitle
                    else -> main
                }
            }
            else -> rawTitle
        }
    }

    /**
     * 分行：主标题固定单行；有括号才显示副标题；无括号拆掉包装恢复系统单行。
     */
    private fun applyLineSubtitle(
        titleText: TextView,
        artistText: TextView?,
        main: String,
        sub: String,
    ) {
        restoreArtistText(artistText)

        if (!MediaTitleLineLayoutPolicy.shouldShowSubtitle(sub)) {
            unwrapTitleRow(titleText)
            configureSingleLineTitle(titleText)
            titleText.text = main.ifEmpty { titleText.text }
            artistText?.let { applyArtistEndFadeMode(it) }
            return
        }

        val displayMain = main.ifEmpty { titleText.text?.toString().orEmpty() }
        if (displayMain.isEmpty()) return

        val (_, subTv) = ensureTitleRow(titleText)
        val baseSize = rememberBaseSize(titleText)
        val subPx = MediaTitleLineLayoutPolicy.resolveSubPx(
            baseSize,
            artistText?.textSize,
            titleText.resources.displayMetrics.density,
        )
        val titleColor = titleText.currentTextColor
        val subTop = dp(titleText.context, MediaTitleLineLayoutPolicy.SUB_TOP_MARGIN_DP)
        val appliedKey = "$displayMain|$sub|$baseSize|$subPx|$titleColor|$subTop"

        if (titleText.getTag(LINE_APPLIED_TAG) == appliedKey &&
            titleText.text?.toString() == displayMain &&
            subTv.visibility == View.VISIBLE &&
            subTv.text?.toString() == sub
        ) {
            artistText?.let {
                applyArtistEndFadeMode(it)
                tightenArtistGap(it, subPx)
            }
            return
        }

        configureSingleLineTitle(titleText)
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize)
        titleText.includeFontPadding = false
        titleText.setPadding(0, 0, 0, 0)
        titleText.setLineSpacing(0f, 1f)
        titleText.text = displayMain

        subTv.visibility = View.VISIBLE
        configureSingleLineTitle(subTv)
        subTv.includeFontPadding = false
        subTv.setPadding(0, 0, 0, 0)
        subTv.setLineSpacing(0f, 1f)
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, subPx.toFloat())
        subTv.setTextColor(MediaTitleLineLayoutPolicy.subtitleColor(titleColor))
        subTv.text = sub
        val slp = (subTv.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        slp.topMargin = subTop
        subTv.layoutParams = slp
        subTv.requestLayout()
        (titleText.parent as? View)?.requestLayout()

        artistText?.let {
            applyArtistEndFadeMode(it)
            tightenArtistGap(it, subPx)
        }
        titleText.setTag(LINE_APPLIED_TAG, appliedKey)
    }

    private fun configureSingleLineTitle(tv: TextView) {
        tv.setSingleLine(true)
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.setHorizontallyScrolling(false)
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
            gravity = Gravity.START or Gravity.TOP
        }

        outerParent.removeView(titleText)
        titleText.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
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
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(context, MediaTitleLineLayoutPolicy.SUB_TOP_MARGIN_DP)
            }
            typeface = titleText.typeface
            gravity = titleText.gravity
            visibility = View.GONE
        }
    }

    private fun unwrapTitleRow(titleText: TextView) {
        val row = titleText.parent as? LinearLayout ?: return
        if (row.tag != TITLE_ROW_TAG) return

        val outerParent = row.parent as? ViewGroup ?: return
        val rowLp = row.layoutParams
        val rowIndex = outerParent.indexOfChild(row)

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
        configureSingleLineTitle(titleText)

        outerParent.removeView(row)
        outerParent.addView(titleText, rowIndex, rowLp)
    }

    private fun applyArtistEndFadeMode(artistText: TextView) {
        artistText.maxLines = 1
        artistText.ellipsize = null
        artistText.setHorizontallyScrolling(false)
        artistText.includeFontPadding = false
    }

    private fun rememberBaseSize(titleText: TextView): Float {
        val cached = titleText.getTag(BASE_TITLE_SIZE_TAG) as? Float
        if (cached != null && cached > 0f) return cached
        val size = titleText.textSize
        titleText.setTag(BASE_TITLE_SIZE_TAG, size)
        return size
    }

    private fun restoreArtistText(artistText: TextView?) {
        if (artistText == null) return
        restoreArtistMargin(artistText)
        readRawArtist(artistText)?.let { artistText.text = it }
    }

    private fun tightenArtistGap(artistText: TextView, subPx: Int) {
        val lp = artistText.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (artistText.getTag(ARTIST_TOP_MARGIN_TAG) == null) {
            artistText.setTag(ARTIST_TOP_MARGIN_TAG, lp.topMargin)
        }
        val base = artistText.getTag(ARTIST_TOP_MARGIN_TAG) as Int
        val target = MediaTitleLineLayoutPolicy.tightenedArtistTopMargin(base, subPx)
        if (lp.topMargin != target) {
            lp.topMargin = target
            artistText.layoutParams = lp
        }
    }

    private fun restoreArtistMargin(artistText: TextView?) {
        if (artistText == null) return
        val original = artistText.getTag(ARTIST_TOP_MARGIN_TAG) as? Int ?: return
        val lp = artistText.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.topMargin != original) {
            lp.topMargin = original
            artistText.layoutParams = lp
        }
        artistText.setTag(ARTIST_TOP_MARGIN_TAG, null)
    }

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

    private fun dp(context: android.content.Context, v: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v,
            context.resources.displayMetrics,
        ).toInt()
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
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        ss.setSpan(
            ForegroundColorSpan(
                Color.argb(
                    SUBTITLE_ALPHA,
                    Color.red(titleColor),
                    Color.green(titleColor),
                    Color.blue(titleColor),
                ),
            ),
            subStart,
            full.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return ss
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
