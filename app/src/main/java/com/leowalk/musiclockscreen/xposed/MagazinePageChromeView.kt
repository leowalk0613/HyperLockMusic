package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.leowalk.musiclockscreen.R

/**
 * 画报页底栏：歌名偏上；控制行歌词/退出贴两端，中间切歌组固定间距居中。
 * 整带背后套 HyperChanger 同款系统柔光玻璃（PassWindowBlur + MiBackgroundBlur）。
 */
class MagazinePageChromeView(context: Context) : FrameLayout(context) {

    interface Callbacks {
        fun onSkipPrevious()
        fun onPlayPause()
        fun onSkipNext()
        fun onToggleLyric()
        fun onExitMagazine()
    }

    private val density = resources.displayMetrics.density
    private val glassLayer: ImageView
    private val contentColumn: LinearLayout
    private val songRow: LinearLayout
    private val controlsRow: LinearLayout
    private val albumArtWrap: FrameLayout
    private val albumArtView: ImageView
    private val infoCol: LinearLayout
    private val titleView: EndFadeTextView
    private val subtitleView: EndFadeTextView
    private val artistView: EndFadeTextView
    private val prevBtn: ImageButton
    private val playPauseBtn: ImageButton
    private val nextBtn: ImageButton
    private val lyricBtn: ImageButton
    private val exitBtn: ImageButton

    private var callbacks: Callbacks? = null
    private var albumTint: Int? = null
    private var contrastBackground: Int? = null
    private var bracketMode: String = "default"
    private var miBlurRefreshGen = 0
    private var lastLaidOutW = 0
    private var lastLaidOutH = 0
    /** 歌曲信息是否已套上稳定样式；为 true 时禁止再用 solid 刷回去造成闪烁。 */
    private var infoMiBlurActive = false
    private var softGlassActive = false
    private var softGlassRetryGen = 0
    private var lastTitleKey: String = ""
    private var lastAlbumArtKey: String = ""

    private val softGlassRetryDelaysMs = longArrayOf(0L, 16L, 48L, 120L, 280L, 600L, 1200L)

    private val enhanceMiBlurRunnable = Runnable {
        if (!isAttachedToWindow) return@Runnable
        scheduleSoftGlassApply()
        if (applyTrueMiBlur()) {
            infoMiBlurActive = true
            alpha = 1f
        } else if (!infoMiBlurActive) {
            // 仅在尚未稳定时落回实色；清掉可能半套上的 blend
            paintSolidReadable(clearBlur = true)
            scheduleSoftGlassApply()
        }
    }

    init {
        clipChildren = false
        clipToPadding = false

        glassLayer = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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
                    setColor(MagazinePageSoftGlassPolicy.seedDrawableColor())
                    cornerRadius = MagazinePageSoftGlassPolicy.cornerRadiusPx(density)
                },
            )
        }
        val glassLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            leftMargin = dp(MagazinePageSoftGlassPolicy.INSET_HORIZONTAL_DP)
            rightMargin = dp(MagazinePageSoftGlassPolicy.INSET_HORIZONTAL_DP)
            topMargin = dp(MagazinePageSoftGlassPolicy.INSET_TOP_DP)
            bottomMargin = dp(MagazinePageSoftGlassPolicy.INSET_BOTTOM_DP)
        }
        addView(glassLayer, glassLp)

        contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            val hPad = dp(MagazinePageChromePolicy.CHROME_HORIZONTAL_PAD_DP)
            setPadding(
                hPad,
                dp(MagazinePageChromePolicy.CONTENT_PAD_TOP_DP),
                hPad,
                dp(MagazinePageChromePolicy.CONTENT_PAD_BOTTOM_DP),
            )
        }
        addView(
            contentColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )

        songRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        albumArtView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val radius = MagazinePageChromePolicy.INFO_ALBUM_CORNER_DP * density
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
        albumArtWrap = FrameLayout(context).apply {
            visibility = GONE
            clipToOutline = false
            clipChildren = false
            elevation = MagazinePageChromePolicy.INFO_ALBUM_ELEVATION_DP * density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val r = MagazinePageChromePolicy.INFO_ALBUM_CORNER_DP * density
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                albumArtView,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }
        songRow.addView(albumArtWrap, albumArtLayoutParams())

        infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            clipChildren = false
            clipToPadding = false
        }

        titleView = EndFadeTextView(context).apply {
            typeface = MiSansTypefaces.bold()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, MagazinePageChromePolicy.INFO_TITLE_SP)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            includeFontPadding = false
        }
        infoCol.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        subtitleView = EndFadeTextView(context).apply {
            typeface = MiSansTypefaces.medium()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, MagazinePageChromePolicy.INFO_SUBTITLE_SP)
            setTextColor(Color.argb(160, 255, 255, 255))
            gravity = Gravity.START
            includeFontPadding = false
            visibility = GONE
        }
        infoCol.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(MagazinePageChromePolicy.INFO_SUBTITLE_GAP_DP)
            },
        )

        artistView = EndFadeTextView(context).apply {
            typeface = MiSansTypefaces.medium()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, MagazinePageChromePolicy.INFO_ARTIST_SP)
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.START
            includeFontPadding = false
        }
        infoCol.addView(
            artistView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(MagazinePageChromePolicy.INFO_TITLE_ARTIST_GAP_DP)
            },
        )
        songRow.addView(
            infoCol,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                weight = 1f
            },
        )
        contentColumn.addView(
            songRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(MagazinePageChromePolicy.INFO_TO_CONTROLS_GAP_DP)
            },
        )

        // 关闭 | 上一曲 | 播放 | 下一曲 | 歌词；播放略大，四段空隙等权均分
        controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        lyricBtn = mediaButton(R.drawable.ic_btn_show_lyric, "显示歌词") { callbacks?.onToggleLyric() }
        prevBtn = mediaButton(R.drawable.ic_media_prev, "上一曲") { callbacks?.onSkipPrevious() }
        playPauseBtn = mediaButton(R.drawable.ic_media_play, "播放/暂停") { callbacks?.onPlayPause() }
        nextBtn = mediaButton(R.drawable.ic_media_next, "下一曲") { callbacks?.onSkipNext() }
        exitBtn = mediaButton(R.drawable.ic_magazine_exit, "退出音乐画报") { callbacks?.onExitMagazine() }

        val normal = MagazinePageChromePolicy.CONTROL_BTN_SIZE_DP
        val play = MagazinePageChromePolicy.PLAY_BTN_SIZE_DP
        val gapMin = MagazinePageChromePolicy.CONTROL_GAP_MIN_DP
        val entries = listOf(
            exitBtn to normal,
            prevBtn to normal,
            playPauseBtn to play,
            nextBtn to normal,
            lyricBtn to normal,
        )
        entries.forEachIndexed { index, (button, sizeDp) ->
            if (index > 0) {
                controlsRow.addView(Space(context), flexGapLp(gapMin))
            }
            controlsRow.addView(button, fixedBtnLp(sizeDp))
        }
        contentColumn.addView(
            controlsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        // 歌曲信息始终可见：先实色，再静默增强 MiBlur（禁止实色↔模糊来回刷）
        alpha = 1f
        if (!MagazinePageSoftGlassPolicy.chromeEmbedsGlassLayer()) {
            glassLayer.visibility = GONE
        }
        setTrackInfo(null, null, "default")
        setPlaying(false)
        setLyricVisible(true, featureEnabled = true)
    }

    fun setCallbacks(cb: Callbacks?) {
        callbacks = cb
    }

    fun setTrackInfo(title: String?, artist: String?, titleBracketMode: String) {
        bracketMode = titleBracketMode
        val (main, sub, showLine) = MagazinePageChromePolicy.resolveTitleDisplay(title, titleBracketMode)
        artistView.text = MagazinePageChromePolicy.displayArtist(artist)

        if (MagazinePageChromePolicy.shouldApplyInlineShrink(titleBracketMode, sub)) {
            titleView.text = buildShrinkSpannable(main, sub)
            subtitleView.visibility = GONE
            subtitleView.text = ""
        } else {
            titleView.text = main
            if (showLine && sub.isNotEmpty()) {
                subtitleView.text = sub
                subtitleView.visibility = VISIBLE
            } else {
                subtitleView.text = ""
                subtitleView.visibility = GONE
            }
        }
        val key = "$main\u0000${artistView.text}\u0000$titleBracketMode"
        val titleChanged = key != lastTitleKey
        lastTitleKey = key
        relayoutAlbumArt()
        // 切歌：先实色保证歌名本体可见；同曲已稳定则不再刷，避免 2s 轮询闪烁
        if (titleChanged || !infoMiBlurActive) {
            paintSolidReadable(clearBlur = true)
            scheduleMiBlurEnhance()
        }
    }

    /** 歌名左侧小封面：圆角 + elevation 阴影；无图时隐藏，文字整块仍居中。 */
    fun setAlbumArt(bitmap: Bitmap?) {
        val art = bitmap?.takeIf { !it.isRecycled }
        if (art == null) {
            lastAlbumArtKey = ""
            albumArtView.setImageDrawable(null)
            albumArtWrap.visibility = GONE
            return
        }
        val key = "${art.width}x${art.height}@${System.identityHashCode(art)}"
        if (key != lastAlbumArtKey || albumArtWrap.visibility != VISIBLE) {
            lastAlbumArtKey = key
            albumArtView.setImageBitmap(art)
            albumArtView.invalidateOutline()
            albumArtWrap.invalidateOutline()
        }
        albumArtWrap.visibility = VISIBLE
        relayoutAlbumArt()
    }

    private fun relayoutAlbumArt() {
        val lp = albumArtLayoutParams()
        if (albumArtWrap.layoutParams?.width != lp.width) {
            albumArtWrap.layoutParams = lp
        }
        val albumW = if (albumArtWrap.visibility == VISIBLE) lp.width else 0
        val gap = if (albumArtWrap.visibility == VISIBLE) {
            dp(MagazinePageChromePolicy.INFO_ALBUM_GAP_DP)
        } else {
            0
        }
        val areaW = if (contentColumn.width > 0) {
            (contentColumn.width - contentColumn.paddingLeft - contentColumn.paddingRight)
                .coerceAtLeast(1)
        } else {
            (resources.displayMetrics.widthPixels -
                contentColumn.paddingLeft - contentColumn.paddingRight)
                .coerceAtLeast(1)
        }
        val infoMax = MagazinePageChromePolicy.infoRowMaxWidthPx(areaW)
        val songLp = songRow.layoutParams as? LinearLayout.LayoutParams
        if (songLp != null && (songLp.width != infoMax || songLp.gravity != Gravity.CENTER_HORIZONTAL)) {
            songLp.width = infoMax
            songLp.gravity = Gravity.CENTER_HORIZONTAL
            songRow.post {
                if (songRow.layoutParams !== songLp) return@post
                songRow.layoutParams = songLp
            }
        }
        val controlsMax = MagazinePageChromePolicy.controlsRowMaxWidthPx(areaW)
        val controlsLp = controlsRow.layoutParams as? LinearLayout.LayoutParams
        if (controlsLp != null &&
            (controlsLp.width != controlsMax || controlsLp.gravity != Gravity.CENTER_HORIZONTAL)
        ) {
            controlsLp.width = controlsMax
            controlsLp.gravity = Gravity.CENTER_HORIZONTAL
            controlsRow.post {
                if (controlsRow.layoutParams !== controlsLp) return@post
                controlsRow.layoutParams = controlsLp
            }
        }
        val maxText = (infoMax - albumW - gap).coerceAtLeast(dp(80))
        // 信息列固定可用宽，文字 MATCH_PARENT，超长才能稳定触发末尾渐隐
        val infoLp = infoCol.layoutParams as? LinearLayout.LayoutParams
        if (infoLp != null && infoLp.width != maxText) {
            infoLp.width = maxText
            infoLp.weight = 0f
            infoCol.layoutParams = infoLp
        }
        titleView.maxWidth = maxText
        subtitleView.maxWidth = maxText
        artistView.maxWidth = maxText
        titleView.invalidate()
        subtitleView.invalidate()
        artistView.invalidate()
    }

    private fun albumArtLayoutParams(): LinearLayout.LayoutParams {
        val size = MagazinePageChromePolicy.infoAlbumArtSizePx(
            density = density,
            scaledDensity = resources.displayMetrics.scaledDensity,
        )
        return LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginEnd = dp(MagazinePageChromePolicy.INFO_ALBUM_GAP_DP)
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        val res = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
        playPauseBtn.setImageDrawable(drawable(res))
        playPauseBtn.contentDescription = if (isPlaying) "暂停" else "播放"
        scheduleMiBlurEnhance()
    }

    fun setLyricVisible(showing: Boolean, featureEnabled: Boolean) {
        lyricBtn.isEnabled = featureEnabled
        lyricBtn.isSelected = featureEnabled && showing
        lyricBtn.alpha = MagazinePageChromePolicy.lyricButtonAlpha(featureEnabled, showing)
        lyricBtn.contentDescription = when {
            !featureEnabled -> "歌词功能已关闭"
            showing -> "隐藏歌词"
            else -> "显示歌词"
        }
        scheduleMiBlurEnhance()
    }

    fun setAlbumTint(color: Int?) {
        if (albumTint == color) return
        val prevLight = styleBgRef()?.let { MagazinePageTextStylePolicy.isLightBackground(it) }
        albumTint = color
        val nextLight = styleBgRef()?.let { MagazinePageTextStylePolicy.isLightBackground(it) }
        if (prevLight != nextLight || !infoMiBlurActive) {
            paintSolidReadable(clearBlur = true)
        }
        scheduleMiBlurEnhance()
    }

    /**
     * 与歌词对齐：壁纸对比色（亮度判定）优先于专辑取色。
     */
    fun setContrastBackground(color: Int?) {
        if (contrastBackground == color) return
        val prevLight = styleBgRef()?.let { MagazinePageTextStylePolicy.isLightBackground(it) }
        contrastBackground = color
        val nextLight = styleBgRef()?.let { MagazinePageTextStylePolicy.isLightBackground(it) }
        if (prevLight != nextLight || !infoMiBlurActive) {
            paintSolidReadable(clearBlur = true)
        }
        scheduleMiBlurEnhance()
    }

    private fun styleBgRef(): Int? = contrastBackground ?: albumTint

    /** @deprecated 保留调用点；内部改为防抖增强，避免闪。 */
    fun requestStableMiBlur() = scheduleMiBlurEnhance()

    fun requestMiBlurRefresh() = scheduleMiBlurEnhance()

    /** 歌词 MiBlur 就绪后调用，使底栏样式跟一次（不先刷实色）。 */
    fun syncStyleWithLyric() {
        scheduleMiBlurEnhance()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        paintSolidReadable(clearBlur = true)
        scheduleMiBlurEnhance()
    }

    override fun onDetachedFromWindow() {
        miBlurRefreshGen++
        softGlassRetryGen++
        removeCallbacks(enhanceMiBlurRunnable)
        clearAllMiBlur()
        clearSoftGlassBackdropLayer()
        infoMiBlurActive = false
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val w = r - l
        val h = b - t
        if (changed || w != lastLaidOutW || h != lastLaidOutH) {
            lastLaidOutW = w
            lastLaidOutH = h
            if (isAttachedToWindow && w > 0 && h > 0) {
                relayoutAlbumArt()
                scheduleMiBlurEnhance()
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) scheduleMiBlurEnhance()
    }

    /** 实色可读字：先清 MiBlur，避免「只剩阴影」。 */
    private fun paintSolidReadable(clearBlur: Boolean) {
        if (clearBlur) {
            clearAllMiBlur()
            infoMiBlurActive = false
        }
        applyFallbackAll()
        animate().cancel()
        alpha = 1f
    }

    private fun scheduleMiBlurEnhance() {
        if (!isAttachedToWindow) return
        val gen = ++miBlurRefreshGen
        removeCallbacks(enhanceMiBlurRunnable)
        // 短防抖：合并 setTrackInfo / tint / lyric sync / layout
        postDelayed({
            if (gen != miBlurRefreshGen || !isAttachedToWindow) return@postDelayed
            enhanceMiBlurRunnable.run()
            // 合成器晚到时再试两次，但不回刷实色
            postDelayed({
                if (gen != miBlurRefreshGen || !isAttachedToWindow) return@postDelayed
                if (!infoMiBlurActive) enhanceMiBlurRunnable.run()
            }, 120L)
            postDelayed({
                if (gen != miBlurRefreshGen || !isAttachedToWindow) return@postDelayed
                if (!infoMiBlurActive) enhanceMiBlurRunnable.run()
            }, 400L)
        }, 32L)
    }

    private fun buildShrinkSpannable(main: String, sub: String): CharSequence {
        val full = if (main.isEmpty()) "($sub)" else "$main ($sub)"
        val start = full.lastIndexOf("($sub)")
        if (start < 0) return full
        val sp = SpannableString(full)
        val end = full.length
        sp.setSpan(RelativeSizeSpan(0.72f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // 不写死白色 ForegroundColorSpan：颜色交给 MiBlur / fallback，避免浅底看不见
        return sp
    }

    private fun mediaButton(iconRes: Int, desc: String, click: OnClickListener): ImageButton {
        return ImageButton(context).apply {
            background = null
            setImageDrawable(drawable(iconRes))
            contentDescription = desc
            // 素材已裁透明边：铺满触控框，间距按可见图案算
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
            setOnClickListener(click)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    private fun flexGapLp(minDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(minDp), 1).apply { weight = 1f }
    }

    private fun fixedBtnLp(sizeDp: Int, marginEndDp: Int = 0): LinearLayout.LayoutParams {
        val size = dp(sizeDp)
        return LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (marginEndDp > 0) marginEnd = dp(marginEndDp)
        }
    }

    private fun drawable(res: Int): Drawable? =
        try {
            ContextCompat.getDrawable(context, res)
        } catch (_: Throwable) {
            null
        }

    private fun dp(v: Int): Int = (v * density).toInt()

    private fun infoTextTargets(): List<TextView> = buildList {
        add(titleView)
        if (subtitleView.visibility == VISIBLE) add(subtitleView)
        add(artistView)
    }

    private fun miBlurTargets(): List<View> = buildList {
        addAll(infoTextTargets())
        add(prevBtn)
        add(playPauseBtn)
        add(nextBtn)
        add(lyricBtn)
        add(exitBtn)
    }

    /**
     * 套真·MiBlur。
     * 浅底：歌曲信息 TextView 只用实色深字（MiBlur+阴影在 TextView 上常只剩光晕）；
     * 深底：再套 member blend，且清零 shadow。
     * 柔光玻璃已由 [glassLayer] 承担 PassWindowBlur 时，字/钮不再自开 pass blur，避免双层冲突。
     */
    private fun applyTrueMiBlur(): Boolean {
        if (!isAttachedToWindow) return false
        val bgRef = contrastBackground ?: albumTint ?: Color.rgb(40, 40, 44)
        val tint = boostTint(albumTint ?: bgRef)
        val onLight = MagazinePageTextStylePolicy.isLightBackground(bgRef)
        val passOnSelf = MagazinePageMiBlurPolicy.enablePassWindowBlur() &&
            !softGlassActive &&
            !(MagazinePageSoftGlassPolicy.enabled() && !MagazinePageSoftGlassPolicy.chromeEmbedsGlassLayer())

        // 浅底：实色最稳，避免「只见阴影」
        if (onLight || !HyperMiBlurHelper.isSupported(context)) {
            for (v in infoTextTargets()) {
                try {
                    HyperMiBlurHelper.clearTextBlend(v)
                } catch (_: Throwable) {
                }
                applyFallbackColors(v, tint, onLight = true)
            }
            // 按钮仍可试 MiBlur
            if (HyperMiBlurHelper.isSupported(context)) {
                applyButtonMiBlur(onLight = true, tint = tint, bgRef = bgRef, passOnSelf = passOnSelf)
            }
            return true
        }

        val blend = MagazinePageTextStylePolicy.miBlurBlendRgb(onLight = false, tint)
        val primary = MagazinePageTextStylePolicy.miBlurPrimaryRgb(onLight = false)
        val over = MagazinePageTextStylePolicy.miBlurOverArgb(onLight = false)
        val alphas = MagazinePageTextStylePolicy.miBlurAlphas(onLight = false)
        val radius = (48f * density).toInt().coerceIn(32, 96)

        var infoOk = true
        for (v in infoTextTargets()) {
            val ok = HyperMiBlurHelper.applyTextBlend(
                view = v,
                blendColor = blend,
                primaryColor = primary,
                colorDark = false,
                enablePassBlurOnSelf = passOnSelf,
                sampleSiblingContent = MagazinePageMiBlurPolicy.sampleSiblingContent(),
                passBlurRadius = radius,
                blendAlpha = alphas.blendAlpha,
                labAlpha = alphas.labAlpha,
                overColor = over,
            )
            if (!ok) {
                infoOk = false
                applyFallbackColors(v, tint, onLight = false)
                continue
            }
            applyInfoTextAppearance(v, onLight = false, primary)
        }
        applyButtonMiBlur(onLight = false, tint = tint, bgRef = bgRef, passOnSelf = passOnSelf)
        return infoOk
    }

    private fun applyButtonMiBlur(
        onLight: Boolean,
        tint: Int,
        bgRef: Int,
        passOnSelf: Boolean,
    ) {
        val blend = MagazinePageTextStylePolicy.miBlurBlendRgb(onLight, tint)
        val primary = MagazinePageTextStylePolicy.miBlurPrimaryRgb(onLight)
        val over = MagazinePageTextStylePolicy.miBlurOverArgb(onLight)
        val alphas = MagazinePageTextStylePolicy.miBlurAlphas(onLight)
        val radius = (48f * density).toInt().coerceIn(32, 96)
        for (v in listOf(prevBtn, playPauseBtn, nextBtn, lyricBtn, exitBtn)) {
            val ok = HyperMiBlurHelper.applyTextBlend(
                view = v,
                blendColor = blend,
                primaryColor = primary,
                colorDark = onLight,
                enablePassBlurOnSelf = passOnSelf,
                sampleSiblingContent = MagazinePageMiBlurPolicy.sampleSiblingContent(),
                passBlurRadius = radius,
                blendAlpha = alphas.blendAlpha,
                labAlpha = alphas.labAlpha,
                overColor = over,
            )
            if (ok) v.clearColorFilter()
        }
    }

    /** HyperChanger 同款两段：Legacy backdrop + MiGlassCompat（禁止平替）。 */
    private fun scheduleSoftGlassApply() {
        if (!MagazinePageSoftGlassPolicy.enabled() ||
            !MagazinePageSoftGlassPolicy.chromeEmbedsGlassLayer()
        ) {
            // 玻璃由 Activity 与壁纸同级挂载
            return
        }
        val gen = ++softGlassRetryGen
        for (i in softGlassRetryDelaysMs.indices) {
            val delay = softGlassRetryDelaysMs[i]
            postDelayed({
                if (gen != softGlassRetryGen || !isAttachedToWindow) return@postDelayed
                if (width <= 0 || height <= 0) return@postDelayed
                if (applySoftGlassBackdropLayer()) {
                    // 已套上：仍再补一次，等合成器晚到
                    if (i == 0) {
                        postDelayed({
                            if (gen == softGlassRetryGen && isAttachedToWindow) {
                                applySoftGlassBackdropLayer()
                            }
                        }, 90L)
                    }
                    return@postDelayed
                }
            }, delay)
        }
    }

    /**
     * @return true 仅当 Legacy + 系统 MiGlass 两段都成功（与 HyperChanger 一致）。
     */
    private fun applySoftGlassBackdropLayer(): Boolean {
        if (!MagazinePageSoftGlassPolicy.enabled()) {
            clearSoftGlassBackdropLayer()
            return false
        }
        glassLayer.visibility = VISIBLE
        val result = HyperOsSoftGlass.applyToImageView(
            glassLayer,
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
        softGlassActive = result.success
        glassLayer.invalidateOutline()
        return result.success
    }

    private fun clearSoftGlassBackdropLayer() {
        softGlassActive = false
        try {
            HyperOsSoftGlass.clear(glassLayer)
        } catch (_: Throwable) {
        }
    }

    private fun applyInfoTextAppearance(v: TextView, onLight: Boolean, primary: Int) {
        // 必须用不透明色；MiBlur 生效时禁止 setShadowLayer，否则只剩光晕不见字
        val color = when {
            v === subtitleView || v === artistView ->
                Color.argb(
                    if (onLight) 230 else 200,
                    Color.red(primary),
                    Color.green(primary),
                    Color.blue(primary),
                )
            else -> Color.argb(255, Color.red(primary), Color.green(primary), Color.blue(primary))
        }
        v.setTextColor(color)
        v.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun applyFallbackAll() {
        val bgRef = contrastBackground ?: albumTint ?: Color.rgb(40, 40, 44)
        val tint = boostTint(albumTint ?: bgRef)
        val onLight = MagazinePageTextStylePolicy.isLightBackground(bgRef)
        for (v in infoTextTargets()) {
            applyFallbackColors(v, tint, onLight)
        }
        for (v in listOf(prevBtn, playPauseBtn, nextBtn, lyricBtn, exitBtn)) {
            v.clearColorFilter()
        }
    }

    private fun clearAllMiBlur() {
        for (v in miBlurTargets()) {
            try {
                HyperMiBlurHelper.clearTextBlend(v)
            } catch (_: Throwable) {
            }
        }
        infoMiBlurActive = false
    }

    private fun applyFallbackColors(v: View, tint: Int, onLight: Boolean) {
        val c = MagazinePageTextStylePolicy.fallbackReadableRgb(onLight, tint)
        if (v is TextView) {
            val solid = if (v === subtitleView || v === artistView) {
                MagazinePageTextStylePolicy.fallbackSecondaryArgb(onLight, c)
            } else {
                Color.argb(255, Color.red(c), Color.green(c), Color.blue(c))
            }
            v.setTextColor(solid)
            // 轻阴影；浅底不用大白光晕（易被看成「只有阴影」）
            if (onLight) {
                v.setShadowLayer(1.5f * density, 0f, 0.8f * density, Color.argb(36, 255, 255, 255))
            } else {
                v.setShadowLayer(2.5f * density, 0f, 1.2f * density, Color.argb(110, 0, 0, 0))
            }
        } else if (v is ImageButton) {
            v.clearColorFilter()
        }
    }

    private fun boostTint(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(0.35f, 0.92f)
        return Color.HSVToColor(hsv)
    }

    private fun blendTextColor(base: Int, tint: Int, weight: Float): Int {
        val w = weight.coerceIn(0f, 1f)
        val r = (Color.red(base) * (1f - w) + Color.red(tint) * w).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * (1f - w) + Color.green(tint) * w).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * (1f - w) + Color.blue(tint) * w).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
