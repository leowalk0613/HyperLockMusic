package com.leowalk.musiclockscreen

import android.widget.LinearLayout
import com.leowalk.musiclockscreen.xposed.MagazineOtherSettingsPolicy

/** 其他设置：壁纸模糊、媒体控件、简洁时钟、息屏缩放、锁屏常亮等。 */
class OtherSettingsActivity : BaseScrollingActivity() {

    private var clockOptionsBlock: LinearLayout? = null

    override fun titleText() = "其他设置"

    override fun buildContent(list: LinearLayout) {
        val mag = ModuleConfig.isMagazineMode

        val blurCard = M3.cardContent(this)
        blurCard.addView(M3.title(this, if (mag) "画报页壁纸模糊" else "壁纸模糊"))
        blurCard.addView(M3.sliderRow(
            this, "模糊强度", 10f, 200f, ModuleConfig.editBlurRadius,
            { "${it.toInt()}" }
        ) { v ->
            ModuleConfig.editBlurRadius = v
            ModuleConfig.push(this)
        })
        blurCard.addView(M3.sliderRow(
            this, "暗色遮罩浓度", 0f, 255f, ModuleConfig.editDarkOverlay.toFloat(),
            { "${it.toInt()}" }
        ) { v ->
            ModuleConfig.editDarkOverlay = v.toInt()
            ModuleConfig.push(this)
        })
        if (mag) {
            blurCard.addView(M3.tipContent(this, "画报模式下本项只改右划页烘焙，不影响普通锁屏壁纸模糊。"))
        }
        list.addView(M3.card(this, blurCard))

        if (MagazineOtherSettingsPolicy.showLockscreenAssistSection(mag)) {
            val miscCard = M3.cardContent(this)
            miscCard.addView(M3.title(this, "锁屏辅助"))

            if (MagazineOtherSettingsPolicy.showMinimalClock(mag)) {
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
            }

            if (MagazineOtherSettingsPolicy.showDisableWallpaperScale(mag)) {
                miscCard.addView(M3.switchRow(
                    this,
                    "禁用息屏壁纸缩放",
                    "音乐锁屏息屏时去掉 HyperOS 壁纸缩放动画，仅保留压暗",
                    ModuleConfig.disableWallpaperScale
                ) { checked ->
                    ModuleConfig.disableWallpaperScale = checked
                    ModuleConfig.push(this)
                })
            }

            if (MagazineOtherSettingsPolicy.showKeepLockScreenOn(mag)) {
                miscCard.addView(M3.switchRow(
                    this, "保持锁屏常亮",
                    if (mag) "画报页忽略系统自动息屏；手动关屏仍生效"
                    else "音乐锁屏时忽略系统自动息屏；手动关屏仍生效",
                    ModuleConfig.editKeepLockScreenOn
                ) { checked ->
                    ModuleConfig.editKeepLockScreenOn = checked
                    ModuleConfig.push(this)
                })
            }
            list.addView(M3.card(this, miscCard))
        }

        if (MagazineOtherSettingsPolicy.showAodFullMedia(mag)) {
            val mediaCard = M3.cardContent(this)
            mediaCard.addView(M3.title(this, "AOD 显示"))
            mediaCard.addView(M3.switchRow(
                this,
                "AOD 完整媒体控件",
                "息屏显示时保持媒体卡片展开，并实时更新进度条与时间",
                ModuleConfig.aodFullMedia
            ) { checked ->
                ModuleConfig.aodFullMedia = checked
                ModuleConfig.push(this)
            })
            list.addView(M3.card(this, mediaCard))
        }

        if (MagazineOtherSettingsPolicy.showTitleBracket(mag)) {
            val titleCard = M3.cardContent(this)
            titleCard.addView(M3.title(this, "歌名括号处理"))

            val labels = listOf("默认", "缩小", "隐藏", "分行")
            val modes = listOf(
                ModuleConfig.TITLE_BRACKET_DEFAULT,
                ModuleConfig.TITLE_BRACKET_SHRINK,
                ModuleConfig.TITLE_BRACKET_HIDE,
                ModuleConfig.TITLE_BRACKET_LINE
            )
            val current = modes.indexOf(ModuleConfig.editTitleBracketMode).coerceAtLeast(0)
            titleCard.addView(M3.segmentGroup(this, labels, current, 4) { index ->
                ModuleConfig.editTitleBracketMode = modes[index]
                ModuleConfig.push(this)
            })
            list.addView(M3.card(this, titleCard))
        }

        list.addView(M3.card(this, M3.tipContent(this,
            if (mag) {
                "画报模式：本页模糊 / 常亮 / 括号均为画报独立档案，与普通锁屏互不影响。\n\n" +
                    "模糊只改画报页壁纸烘焙；常亮作用于右划自建页；括号作用于画报底栏标题。\n\n" +
                    "修改后可重新进入画报页查看效果。"
            } else {
                "模糊：壁纸用金字塔降采样 + box blur 烘焙（滑杆直接控制力度），锁屏再叠 MiBlur 遮罩；解锁清遮罩不影响桌面。改完请重新开关音乐锁屏。\n\n" +
                    "禁用息屏壁纸缩放对大专辑、沉浸封面与仅歌词模式均生效。\n\n" +
                    "AOD 完整媒体控件需重启系统界面后生效。\n\n" +
                    "歌名括号：默认原样显示；缩小置于标题右侧；隐藏去除括号；分行副标题叠在标题下方。\n\n" +
                    "修改后需重启系统界面或重新开关音乐锁屏生效。"
            })))

        if (MagazineOtherSettingsPolicy.showMinimalClock(mag)) {
            refreshClockOptions()
        }
    }

    private fun refreshClockOptions() {
        M3.setControlsEnabled(clockOptionsBlock, ModuleConfig.minimalClock)
    }
}
