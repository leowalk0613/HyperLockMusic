package com.leowalk.musiclockscreen

import android.view.View
import android.widget.LinearLayout

/** 歌词样式：歌词开关、字号、屏幕高度位置。 */
class LyricStyleActivity : BaseScrollingActivity() {

    override fun titleText() = "歌词样式"

    override fun buildContent(list: LinearLayout) {
        val show = M3.cardContent(this)
        show.addView(M3.title(this, "歌词显示"))

        show.addView(M3.switchRow(this, "显示歌词", null, ModuleConfig.showLyric) { checked ->
            ModuleConfig.showLyric = checked
            ModuleConfig.push(this)
        })

        show.addView(M3.switchRow(
            this, "沉浸歌词", "仅显示当前行大字，隐藏方形专辑；区域与专辑区块一致",
            ModuleConfig.immersiveLyric
        ) { checked ->
            ModuleConfig.immersiveLyric = checked
            ModuleConfig.push(this)
        })

        show.addView(M3.switchRow(
            this, "隐藏歌词背景", "不绘制雾状渐变背景，仅显示文字",
            ModuleConfig.lyricHideBackground
        ) { checked ->
            ModuleConfig.lyricHideBackground = checked
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "歌词字号", 12f, 40f, ModuleConfig.lyricSize.coerceIn(12f, 40f),
            { "${it.toInt()} sp" }
        ) { v ->
            ModuleConfig.lyricSize = v
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "歌词区域宽度", 30f, 100f, ModuleConfig.lyricWidth.coerceIn(30f, 100f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.lyricWidth = v
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "底边位置", 30f, 80f, ModuleConfig.lyricBgAnchorY.coerceIn(30f, 80f),
            { "${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.lyricBgAnchorY = v
            ModuleConfig.push(this)
        })

        show.addView(M3.switchRow(this, "歌词翻译互换", "在有翻译歌词时优先显示翻译", ModuleConfig.swapLyric) { checked ->
            ModuleConfig.swapLyric = checked
            ModuleConfig.push(this)
        })

        val alignLabels = listOf("靠左", "居中", "靠右")
        val alignModes = listOf(
            ModuleConfig.LYRIC_ALIGN_LEFT,
            ModuleConfig.LYRIC_ALIGN_CENTER,
            ModuleConfig.LYRIC_ALIGN_RIGHT
        )
        val alignIndex = alignModes.indexOf(ModuleConfig.lyricAlign).coerceAtLeast(0)
        show.addView(M3.segmentGroup(this, alignLabels, alignIndex, 3) { index ->
            ModuleConfig.lyricAlign = alignModes[index]
            ModuleConfig.push(this)
        })

        list.addView(M3.card(this, show))

        list.addView(M3.card(this, M3.tipContent(this,
            "沉浸歌词开启时，显示区域与专辑封面区块一致（大小、底边位置见专辑封面设置）。" +
                "底边位置滑块仅在非沉浸模式下生效。")))
    }
}
