package com.leowalk.musiclockscreen

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.slider.Slider

/** 专辑封面：大封面开关、大小、位置、圆角。 */
class AlbumStyleActivity : BaseScrollingActivity() {

    private var sizeSlider: Slider? = null
    private var cornerSlider: Slider? = null
    private var sizeRow: LinearLayout? = null
    private var cornerRow: LinearLayout? = null

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

        card.addView(M3.switchRow(
            this, "沉浸专辑", "大图羽化融入取色背景；隐藏歌词时自动显示",
            ModuleConfig.immersiveAlbum
        ) { checked ->
            ModuleConfig.immersiveAlbum = checked
            ModuleConfig.push(this)
            updateImmersiveControls(checked)
        })

        val sizeRowView = M3.sliderRow(
            this, "专辑图大小", 20f, 90f, ModuleConfig.albumSize.coerceIn(20f, 90f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.albumSize = v
            ModuleConfig.push(this)
        }
        sizeRow = sizeRowView
        sizeSlider = sizeRowView.getChildAt(1) as? Slider
        card.addView(sizeRowView)

        card.addView(M3.sliderRow(
            this, "底边位置", 30f, 80f, migrateAlbumAnchor(ModuleConfig.albumAnchorY),
            { "${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.albumAnchorY = v
            ModuleConfig.push(this)
        })

        val cornerRowView = M3.sliderRow(
            this, "专辑图圆角", 0f, 60f, ModuleConfig.albumCorner.coerceIn(0f, 60f),
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.albumCorner = v
            ModuleConfig.push(this)
        }
        cornerRow = cornerRowView
        cornerSlider = cornerRowView.getChildAt(1) as? Slider
        card.addView(cornerRowView)

        card.addView(M3.switchRow(
            this, "让专辑图显示更清晰",
            "锁屏先显示系统封面，后台按歌曲 ID 拉取网络官方高清图替换前景专辑；模糊背景仍用系统封面。目前仅支持网易云音乐",
            ModuleConfig.albumNetworkHd
        ) { checked ->
            ModuleConfig.albumNetworkHd = checked
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, card))

        list.addView(M3.card(this, M3.tipContent(this,
            "底边位置 = 专辑底边在屏幕高度上的百分比。沉浸专辑开启后大小与圆角不可用。")))

        updateImmersiveControls(ModuleConfig.immersiveAlbum)
    }

    private fun updateImmersiveControls(immersive: Boolean) {
        val alpha = if (immersive) 0.4f else 1f
        sizeRow?.alpha = alpha
        cornerRow?.alpha = alpha
        sizeSlider?.isEnabled = !immersive
        cornerSlider?.isEnabled = !immersive
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
