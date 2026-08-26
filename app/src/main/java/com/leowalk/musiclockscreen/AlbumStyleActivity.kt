package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 专辑封面：大封面开关、大小、位置、圆角。 */
class AlbumStyleActivity : BaseScrollingActivity() {

    override fun titleText() = "专辑封面"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "封面显示"))

        card.addView(M3.switchRow(
            this, "显示大专辑封面", "锁屏 overlay，仅音乐锁屏时可见",
            ModuleConfig.showBigAlbum
        ) { checked ->
            ModuleConfig.showBigAlbum = checked
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "专辑图大小", 20f, 90f, ModuleConfig.albumSize.coerceIn(20f, 90f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.albumSize = v
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "底边位置", 30f, 80f, migrateAlbumAnchor(ModuleConfig.albumAnchorY),
            { "${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.albumAnchorY = v
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "专辑图圆角", 0f, 60f, ModuleConfig.albumCorner.coerceIn(0f, 60f),
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.albumCorner = v
            ModuleConfig.push(this)
        })

        card.addView(M3.switchRow(
            this, "让专辑图显示更清晰",
            "先显示系统封面，后台只替换前景专辑为网易云高清；模糊背景始终用系统封面",
            ModuleConfig.albumSrEnhance
        ) { checked ->
            ModuleConfig.albumSrEnhance = checked
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, card))

        list.addView(M3.card(this, M3.tipContent(this,
            "底边位置 = 专辑底边在屏幕高度上的百分比。数值越大越靠下。")))
    }

    /** 旧版 dp 间距（>100）→ 默认 55% 屏高 */
    private fun migrateAlbumAnchor(raw: Float): Float {
        if (raw > 100f || raw < 0f) {
            ModuleConfig.albumAnchorY = 55f
            ModuleConfig.push(this)
            return 55f
        }
        return raw.coerceIn(30f, 80f)
    }
}
