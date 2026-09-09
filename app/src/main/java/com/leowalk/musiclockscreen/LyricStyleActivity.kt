package com.leowalk.musiclockscreen

import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

/** 歌词样式：普通 / 沉浸互斥；按模式灰显无效项。 */
class LyricStyleActivity : BaseScrollingActivity() {

    private var styleSegment: LinearLayout? = null
    private var stylePreview: LinearLayout? = null
    private var normalOnlyBlock: LinearLayout? = null
    private var immersiveOnlyBlock: LinearLayout? = null
    private var sharedBlock: LinearLayout? = null
    private var modeHint: TextView? = null

    private var showLyricRow: LinearLayout? = null

    override fun titleText() = "歌词样式"

    override fun buildContent(list: LinearLayout) {
        list.addView(M3.card(this, M3.tipContent(this,
            if (ModuleConfig.isMagazineMode) {
                "当前为「画报模式」：下方设置只作用于锁屏右划自建页歌词，与普通锁屏歌词档案相互独立。\n\n" +
                    "歌词功能为 LyricFocus 的外部渲染；主界面「歌词」总开关关闭后两边均不可用。"
            } else {
                "歌词功能为 LyricFocus 的外部渲染功能；在 LyricFocus 中开启后，" +
                    "歌词将推送到本模块，锁屏才会显示。\n\n" +
                    "主界面「歌词」总开关关闭后，整个歌词功能不可用；下方「显示歌词」仅控制普通锁屏是否展示。\n" +
                    "与画报页歌词档案相互独立。"
            })))

        val card = M3.cardContent(this)
        card.addView(M3.title(this, if (ModuleConfig.isMagazineMode) "画报页显示" else "锁屏显示"))

        card.addView(M3.switchRow(
            this,
            "显示歌词",
            if (ModuleConfig.isMagazineMode) "仅控制画报页是否展示歌词；总开关在主界面"
            else "仅控制普通锁屏是否展示歌词；总开关在主界面",
            ModuleConfig.editShowLyric,
        ) { checked ->
            ModuleConfig.editShowLyric = checked
            ModuleConfig.push(this)
            refreshModeUi()
        }.also { showLyricRow = it })

        card.addView(sectionLabel("歌词样式（二选一）"))
        val styleIndex = if (ModuleConfig.editImmersiveLyric) 1 else 0
        styleSegment = M3.segmentGroup(this, listOf("普通歌词", "沉浸歌词"), styleIndex, 2) { index ->
            ModuleConfig.editImmersiveLyric = index == 1
            if (index == 1) {
                ModuleConfig.editLyricHideBackground = true
            }
            ModuleConfig.push(this)
            refreshModeUi()
        }
        card.addView(styleSegment)
        stylePreview = M3.stylePreviewRow(
            this,
            listOf("普通歌词", "沉浸歌词"),
            intArrayOf(R.drawable.preview_lyric_normal, R.drawable.preview_lyric_immersive),
        )
        card.addView(stylePreview)

        modeHint = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
            setTextColor(
                M3.attrColor(
                    this@LyricStyleActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt()
                )
            )
            setPadding(0, 0, 0, M3.dp(this@LyricStyleActivity, 8f))
        }
        card.addView(modeHint)

        // 普通歌词专用
        normalOnlyBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        normalOnlyBlock!!.addView(sectionLabel("普通歌词专用"))
        normalOnlyBlock!!.addView(M3.sliderRow(
            this, "歌词字号", 12f, 40f, ModuleConfig.editLyricSize.coerceIn(12f, 40f),
            { "${it.toInt()} sp" }
        ) { v ->
            ModuleConfig.editLyricSize = v
            ModuleConfig.push(this)
        })
        normalOnlyBlock!!.addView(M3.sliderRow(
            this, "歌词区域宽度", 30f, 100f, ModuleConfig.editLyricWidth.coerceIn(30f, 100f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.editLyricWidth = v
            ModuleConfig.push(this)
        })
        val lyricBottomMax = if (ModuleConfig.isMagazineMode) {
            val dm = resources.displayMetrics
            com.leowalk.musiclockscreen.xposed.MagazinePageChromePolicy.lyricBottomAnchorMaxPercent(
                screenHeightPx = dm.heightPixels,
                density = dm.density,
                scaledDensity = dm.scaledDensity,
            )
        } else {
            95f
        }
        normalOnlyBlock!!.addView(M3.sliderRow(
            this,
            "底边位置",
            10f,
            lyricBottomMax,
            ModuleConfig.editLyricBgAnchorY.coerceIn(10f, lyricBottomMax),
            { "${it.toInt()}% 屏高" },
        ) { v ->
            ModuleConfig.editLyricBgAnchorY = v.coerceIn(10f, lyricBottomMax)
            ModuleConfig.push(this)
        })
        normalOnlyBlock!!.addView(M3.switchRow(
            this, "隐藏歌词背景", "不绘制雾状渐变；沉浸歌词模式下本项无效",
            ModuleConfig.editLyricHideBackground
        ) { checked ->
            ModuleConfig.editLyricHideBackground = checked
            ModuleConfig.push(this)
        })
        card.addView(normalOnlyBlock)

        // 沉浸歌词专用
        immersiveOnlyBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        immersiveOnlyBlock!!.addView(sectionLabel("沉浸歌词专用"))
        val alignLabels = listOf("靠左", "居中", "靠右")
        val alignModes = listOf(
            ModuleConfig.LYRIC_ALIGN_LEFT,
            ModuleConfig.LYRIC_ALIGN_CENTER,
            ModuleConfig.LYRIC_ALIGN_RIGHT
        )
        val alignIndex = alignModes.indexOf(ModuleConfig.editLyricAlign).coerceAtLeast(0)
        immersiveOnlyBlock!!.addView(M3.segmentGroup(this, alignLabels, alignIndex, 3) { index ->
            ModuleConfig.editLyricAlign = alignModes[index]
            ModuleConfig.push(this)
        })
        immersiveOnlyBlock!!.addView(M3.switchRow(
            this,
            "三行上滑",
            "仅沉浸歌词：上一句/当前/下一句，当前行居中并显示翻译；邻行更透，上下裁切。AOD 退回单行。共用切行动画在关闭本项时对所有模式生效",
            ModuleConfig.editImmersiveLyricStack,
        ) { checked ->
            ModuleConfig.editImmersiveLyricStack = checked
            ModuleConfig.push(this)
        })
        immersiveOnlyBlock!!.addView(TextView(this).apply {
            text = if (ModuleConfig.isMagazineMode) {
                "区块大小/底边请到「专辑封面」调整（画报页大专辑档案）。"
            } else {
                "区块大小/底边请到「专辑封面」调整（与大专辑共用）。"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
            setTextColor(
                M3.attrColor(
                    this@LyricStyleActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt()
                )
            )
            setPadding(0, M3.dp(this@LyricStyleActivity, 4f), 0, M3.dp(this@LyricStyleActivity, 4f))
        })
        card.addView(immersiveOnlyBlock)

        // 共用
        sharedBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sharedBlock!!.addView(sectionLabel("共用"))
        sharedBlock!!.addView(M3.switchRow(
            this, "歌词翻译互换", "有翻译时优先显示翻译",
            ModuleConfig.editSwapLyric
        ) { checked ->
            ModuleConfig.editSwapLyric = checked
            ModuleConfig.push(this)
        })
        sharedBlock!!.addView(sectionLabel("切行动画（AOD 不生效）"))
        val transitionLabels = listOf("淡入淡出", "左滑", "右滑", "上滑", "下滑")
        val transitionModes = listOf(
            ModuleConfig.LYRIC_TRANSITION_FADE,
            ModuleConfig.LYRIC_TRANSITION_SLIDE_LEFT,
            ModuleConfig.LYRIC_TRANSITION_SLIDE_RIGHT,
            ModuleConfig.LYRIC_TRANSITION_SLIDE_UP,
            ModuleConfig.LYRIC_TRANSITION_SLIDE_DOWN,
        )
        val transitionIndex = transitionModes.indexOf(ModuleConfig.editLyricTransition).coerceAtLeast(0)
        sharedBlock!!.addView(M3.segmentGroup(this, transitionLabels, transitionIndex, 3) { index ->
            ModuleConfig.editLyricTransition = transitionModes[index]
            ModuleConfig.push(this)
        })
        card.addView(sharedBlock)

        list.addView(M3.card(this, card))
        list.addView(M3.card(this, M3.tipContent(this,
            if (ModuleConfig.isMagazineMode) {
                "画报页绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                    "在「专辑封面」切换样式时会套用默认歌词样式；本页与普通锁屏档案互不影响。\n" +
                    "「三行上滑」为沉浸独立开关。"
            } else {
                "绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                    "「三行上滑」为沉浸独立开关；下方切行动画在未开三行上滑时对普通与沉浸通用。AOD 均不播动画。\n" +
                    "灰显项表示当前歌词样式下不生效。"
            })))

        refreshModeUi()
    }

    private fun refreshModeUi() {
        val enabled = ModuleConfig.lyricEnabled
        val show = ModuleConfig.editShowLyric
        val immersive = ModuleConfig.editImmersiveLyric
        M3.setControlsEnabled(showLyricRow, enabled)
        M3.setControlsEnabled(styleSegment, enabled && show)
        M3.setControlsEnabled(stylePreview, enabled && show)
        M3.setControlsEnabled(sharedBlock, enabled && show)
        M3.setControlsEnabled(normalOnlyBlock, enabled && show && !immersive)
        M3.setControlsEnabled(immersiveOnlyBlock, enabled && show && immersive)
        modeHint?.text = when {
            !enabled -> "歌词功能已在主界面关闭，样式设置暂不生效。"
            !show -> "显示歌词已关闭，样式设置暂不生效。"
            immersive -> "当前：沉浸歌词。可开三行上滑；关闭后切行动画生效。"
            else -> "当前：普通歌词。切行动画通用；三行上滑仅沉浸可用。"
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
            setTextColor(
                M3.attrColor(
                    this@LyricStyleActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt()
                )
            )
            setPadding(0, M3.dp(this@LyricStyleActivity, 8f), 0, M3.dp(this@LyricStyleActivity, 4f))
        }
    }
}
