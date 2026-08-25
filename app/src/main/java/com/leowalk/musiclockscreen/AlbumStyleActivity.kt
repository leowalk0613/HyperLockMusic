package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 专辑封面：大封面开关、大小、位置、圆角。 */
class AlbumStyleActivity : BaseScrollingActivity() {

    override fun titleText() = "专辑封面"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "封面显示"))

        card.addView(M3.switchRow(
            this, "显示大专辑封面", "在壁纸上叠加整张专辑封面",
            ModuleConfig.showBigAlbum
        ) { checked ->
            ModuleConfig.showBigAlbum = checked
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "专辑图大小", 20f, 90f, ModuleConfig.albumSize,
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.albumSize = v
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "专辑图位置（上下）", -200f, 200f, ModuleConfig.albumOffsetY,
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.albumOffsetY = v
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "专辑图圆角", 0f, 60f, ModuleConfig.albumCorner,
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.albumCorner = v
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, card))

        list.addView(M3.card(this, M3.tipContent(this,
            "大小按屏宽百分比取值，负数位置表示向上偏移。")))
    }
}