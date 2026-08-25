package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 模糊背景：壁纸模糊强度与暗色遮罩浓度。 */
class BlurBackgroundActivity : BaseScrollingActivity() {

    override fun titleText() = "模糊背景"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "壁纸模糊"))

        card.addView(M3.sliderRow(
            this, "模糊强度", 10f, 200f, ModuleConfig.blurRadius,
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.blurRadius = v
            ModuleConfig.push(this)
        })

        card.addView(M3.sliderRow(
            this, "暗色遮罩浓度", 0f, 255f, ModuleConfig.darkOverlay.toFloat(),
            { "${it.toInt()}" }
        ) { v ->
            ModuleConfig.darkOverlay = v.toInt()
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, card))

        list.addView(M3.card(this, M3.tipContent(this,
            "模糊作用于进入音乐锁屏时新生成的壁纸，修改后需重启系统界面或重新开关音乐锁屏生效。")))
    }
}