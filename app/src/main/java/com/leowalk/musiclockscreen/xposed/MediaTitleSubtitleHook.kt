package com.leowalk.musiclockscreen.xposed

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
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
    private const val LINE_SUBTITLE_SIZE_RATIO = 0.6f
    private const val LINE_SUBTITLE_ALPHA = 120
    private const val RAW_ARTIST_TAG = 0x7f140001

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
                    // setInfoText 每次重设文本为纯净内容，失效歌手原始缓存
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

        if (mode == "line") {
            // 分行模式：标题显示主标题，副标题合到歌手后面（更小、可截断、歌手优先）
            val artistText = artistTextField.get(holder) as? TextView
            applyLineSubtitle(titleText, artistText, main, sub)
            return
        }

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
     * 分行模式：标题只显示主标题；副标题合进歌手文本之后，字号比歌手小、颜色更淡。
     * 歌手始终在前，超宽时从末尾截断（优先保留歌手）。不改变任何布局层级。
     */
    private fun applyLineSubtitle(
        titleText: TextView,
        artistText: TextView?,
        main: String,
        sub: String
    ) {
        titleText.text = main.ifEmpty { titleText.text }

        if (artistText == null) return
        val artistRaw = readRawArtist(artistText) ?: return
        if (artistRaw.isBlank()) return

        if (sub.isEmpty()) {
            artistText.text = artistRaw
            return
        }

        val artistColor = artistText.currentTextColor
        val ss = SpannableString("$artistRaw   $sub")
        val subStart = ss.length - sub.length
        ss.setSpan(
            RelativeSizeSpan(LINE_SUBTITLE_SIZE_RATIO),
            subStart,
            ss.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        ss.setSpan(
            ForegroundColorSpan(
                Color.argb(
                    LINE_SUBTITLE_ALPHA,
                    Color.red(artistColor),
                    Color.green(artistColor),
                    Color.blue(artistColor)
                )
            ),
            subStart,
            ss.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        artistText.text = ss
    }

    /** 读取歌手的原始文本。首次（setInfoText 后）歌手是纯净的，用 tag 缓存原始值，
     * 避免重复回调把已追加的副标题再次拼接。 */
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

    /** 清空歌手原始缓存（内容变化时调用）。 */
    fun invalidateArtistCache(artistText: TextView) {
        artistText.setTag(RAW_ARTIST_TAG, null)
    }

    private fun dp(context: android.content.Context, v: Int): Int {
        val density = context.resources.displayMetrics.density
        return (v * density).toInt()
    }

    /**
     * 主标题在前、副标题在后；单行省略时从末尾截断，优先保留主标题。
     */
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

    /** 还原旧版双 TextView 包裹，避免布局/约束异常 */
    private fun unwrapTitleRow(titleText: TextView) {
        val row = titleText.parent as? LinearLayout ?: return
        if (row.tag != TITLE_ROW_TAG) return

        val outerParent = row.parent as? ViewGroup ?: return
        val rowLp = row.layoutParams
        val rowIndex = outerParent.indexOfChild(row)

        row.findViewWithTag<View>(SUBTITLE_VIEW_TAG)?.let { row.removeView(it) }
        row.removeView(titleText)

        val titleConstraintId = row.id
        if (titleConstraintId != View.NO_ID) {
            titleText.id = titleConstraintId
        }

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
