package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 其他设置：壁纸模糊、简洁时钟、息屏缩放、锁屏常亮等。 */
class OtherSettingsActivity : BaseScrollingActivity() {

    private var clockOptionsBlock: LinearLayout? = null

    override fun titleText() = "其他设置"

    override fun buildContent(list: LinearLayout) {
        val blurCard = M3.cardContent(this)
        blurCard.addView(M3.title(this, "壁纸模糊"))
        blurCard.addView(M3.sliderRow(
            this, "模糊强度", 10f, 200f, ModuleConfig.blurRadius,
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.blurRadius = v
            ModuleConfig.push(this)
        })
        blurCard.addView(M3.sliderRow(
            this, "暗色遮罩浓度", 0f, 255f, ModuleConfig.darkOverlay.toFloat(),
            { "${it.toInt()}" }
        ) { v ->
            ModuleConfig.darkOverlay = v.toInt()
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, blurCard))

        val miscCard = M3.cardContent(this)
        miscCard.addView(M3.title(this, "锁屏辅助"))

        miscCard.addView(M3.switchRow(
            this, "简洁时钟",
            "隐藏系统大时钟，顶部显示一行时间日期",
            ModuleConfig.minimalClock
        ) { checked ->
            ModuleConfig.minimalClock = checked
            ModuleConfig.push(this)
            refreshClockOptions()
        })

        clockOptionsBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockOptionsBlock!!.addView(M3.sliderRow(
            this, "时钟字号", 16f, 48f,
            ModuleConfig.minimalClockSize.coerceIn(16f, 48f),
            { "${it.toInt()} sp" }
        ) { v ->
            ModuleConfig.minimalClockSize = v
            ModuleConfig.push(this)
        })
        clockOptionsBlock!!.addView(M3.sliderRow(
            this, "时钟高度", 2f, 25f,
            ModuleConfig.minimalClockTopY.coerceIn(2f, 25f),
            { "顶边 ${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.minimalClockTopY = v
            ModuleConfig.push(this)
        })
        miscCard.addView(clockOptionsBlock)

        miscCard.addView(M3.switchRow(
            this,
            "禁用息屏壁纸缩放",
            "音乐锁屏息屏时去掉 HyperOS 壁纸缩放动画，仅保留压暗",
            ModuleConfig.disableWallpaperScale
        ) { checked ->
            ModuleConfig.disableWallpaperScale = checked
            ModuleConfig.push(this)
        })

        miscCard.addView(M3.switchRow(
            this, "保持锁屏常亮", "音乐锁屏时忽略系统自动息屏；手动关屏仍生效",
            ModuleConfig.keepLockScreenOn
        ) { checked ->
            ModuleConfig.keepLockScreenOn = checked
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, miscCard))

        list.addView(M3.card(this, M3.tipContent(this,
            "模糊强度作用于进入音乐锁屏时新生成的壁纸。\n\n" +
                "禁用息屏壁纸缩放对大专辑、沉浸封面与仅歌词模式均生效。\n\n" +
                "修改后需重启系统界面或重新开关音乐锁屏生效。")))

        refreshClockOptions()
    }

    private fun refreshClockOptions() {
        M3.setControlsEnabled(clockOptionsBlock, ModuleConfig.minimalClock)
    }
}
