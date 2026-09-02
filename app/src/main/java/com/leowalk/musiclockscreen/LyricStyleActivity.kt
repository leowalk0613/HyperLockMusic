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
            "歌词功能为 LyricFocus 的外部渲染功能；在 LyricFocus 中开启后，" +
                "歌词将推送到本模块，锁屏才会显示。\n\n" +
                "主界面「歌词」总开关关闭后，整个歌词功能不可用；下方「显示歌词」仅控制锁屏是否展示。")))

        val card = M3.cardContent(this)
        card.addView(M3.title(this, "锁屏显示"))

        card.addView(M3.switchRow(
            this,
            "显示歌词",
            "仅控制锁屏是否展示歌词；总开关在主界面",
            ModuleConfig.showLyric,
        ) { checked ->
            ModuleConfig.showLyric = checked
            ModuleConfig.push(this)
            refreshModeUi()
        }.also { showLyricRow = it })

        card.addView(sectionLabel("歌词样式（二选一）"))
        val styleIndex = if (ModuleConfig.immersiveLyric) 1 else 0
        styleSegment = M3.segmentGroup(this, listOf("普通歌词", "沉浸歌词"), styleIndex, 2) { index ->
            ModuleConfig.immersiveLyric = index == 1
            // 沉浸歌词自带无雾状底；切回普通不强制改 hideBg（封面绑定另管）
            if (index == 1) {
                ModuleConfig.lyricHideBackground = true
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
            this, "歌词字号", 12f, 40f, ModuleConfig.lyricSize.coerceIn(12f, 40f),
            { "${it.toInt()} sp" }
        ) { v ->
            ModuleConfig.lyricSize = v
            ModuleConfig.push(this)
        })
        normalOnlyBlock!!.addView(M3.sliderRow(
            this, "歌词区域宽度", 30f, 100f, ModuleConfig.lyricWidth.coerceIn(30f, 100f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.lyricWidth = v
            ModuleConfig.push(this)
        })
        normalOnlyBlock!!.addView(M3.sliderRow(
            this, "底边位置", 30f, 80f, ModuleConfig.lyricBgAnchorY.coerceIn(30f, 80f),
            { "${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.lyricBgAnchorY = v
            ModuleConfig.push(this)
        })
        normalOnlyBlock!!.addView(M3.switchRow(
            this, "隐藏歌词背景", "不绘制雾状渐变；沉浸歌词模式下本项无效",
            ModuleConfig.lyricHideBackground
        ) { checked ->
            ModuleConfig.lyricHideBackground = checked
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
        val alignIndex = alignModes.indexOf(ModuleConfig.lyricAlign).coerceAtLeast(0)
        immersiveOnlyBlock!!.addView(M3.segmentGroup(this, alignLabels, alignIndex, 3) { index ->
            ModuleConfig.lyricAlign = alignModes[index]
            ModuleConfig.push(this)
        })
        immersiveOnlyBlock!!.addView(TextView(this).apply {
            text = "区块大小/底边请到「专辑封面」调整（与大专辑共用）。"
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
            ModuleConfig.swapLyric
        ) { checked ->
            ModuleConfig.swapLyric = checked
            ModuleConfig.push(this)
        })
        card.addView(sharedBlock)

        list.addView(M3.card(this, card))
        list.addView(M3.card(this, M3.tipContent(this,
            "绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                "灰显项表示当前歌词样式下不生效。")))

        refreshModeUi()
    }

    private fun refreshModeUi() {
        val enabled = ModuleConfig.lyricEnabled
        val show = ModuleConfig.showLyric
        val immersive = ModuleConfig.immersiveLyric
        M3.setControlsEnabled(showLyricRow, enabled)
        M3.setControlsEnabled(styleSegment, enabled && show)
        M3.setControlsEnabled(stylePreview, enabled && show)
        M3.setControlsEnabled(sharedBlock, enabled && show)
        M3.setControlsEnabled(normalOnlyBlock, enabled && show && !immersive)
        M3.setControlsEnabled(immersiveOnlyBlock, enabled && show && immersive)
        modeHint?.text = when {
            !enabled -> "歌词功能已在主界面关闭，样式设置暂不生效。"
            !show -> "显示歌词已关闭，样式设置暂不生效。"
            immersive -> "当前：沉浸歌词。字号固定；宽度/底边用专辑设置；对齐可用；隐藏背景无效。"
            else -> "当前：普通歌词。字号/宽度/底边/隐藏背景可用；对齐仅沉浸歌词生效。"
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
