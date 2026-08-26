package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 歌词样式：歌词开关、字号。 */
class LyricStyleActivity : BaseScrollingActivity() {

    override fun titleText() = "歌词样式"

    override fun buildContent(list: LinearLayout) {
        val show = M3.cardContent(this)
        show.addView(M3.title(this, "歌词显示"))

        show.addView(M3.switchRow(this, "显示歌词", null, ModuleConfig.showLyric) { checked ->
            ModuleConfig.showLyric = checked
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "歌词字号", 12f, 40f, ModuleConfig.lyricSize,
            { "${it.toInt()} sp" }
        ) { v ->
            ModuleConfig.lyricSize = v
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "歌词区域宽度", 50f, 130f, ModuleConfig.lyricWidth,
            { "${it.toInt()}%" }
        ) { v ->
            ModuleConfig.lyricWidth = v
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "歌词背景高度", 35f, 85f, ModuleConfig.lyricBgAnchorY,
            { "${it.toInt()}%" }
        ) { v ->
            ModuleConfig.lyricBgAnchorY = v
            ModuleConfig.push(this)
        })

        show.addView(M3.sliderRow(
            this, "歌词背景高度微调", -40f, 40f, ModuleConfig.lyricBgOffsetY,
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.lyricBgOffsetY = v
            ModuleConfig.push(this)
        })

        show.addView(M3.switchRow(this, "歌词翻译互换", "在有翻译歌词时优先显示翻译", ModuleConfig.swapLyric) { checked ->
            ModuleConfig.swapLyric = checked
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, show))

        list.addView(M3.card(this, M3.tipContent(this,
            "修改后需重启系统界面或重新开关音乐锁屏生效。")))
    }
}
