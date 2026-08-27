package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 媒体控件：AOD 展开与标题括号样式。 */
class MediaTitleActivity : BaseScrollingActivity() {

    override fun titleText() = "媒体控件"

    override fun buildContent(list: LinearLayout) {
        val aodCard = M3.cardContent(this)
        aodCard.addView(M3.title(this, "AOD 显示"))
        aodCard.addView(M3.switchRow(
            this,
            "AOD 完整媒体控件",
            "息屏显示时保持媒体卡片展开，并实时更新进度条与时间",
            ModuleConfig.aodFullMedia
        ) { checked ->
            ModuleConfig.aodFullMedia = checked
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, aodCard))

        val card = M3.cardContent(this)
        card.addView(M3.title(this, "歌名括号处理"))

        val labels = listOf("默认", "缩小", "隐藏", "分行")
        val modes = listOf(
            ModuleConfig.TITLE_BRACKET_DEFAULT,
            ModuleConfig.TITLE_BRACKET_SHRINK,
            ModuleConfig.TITLE_BRACKET_HIDE,
            ModuleConfig.TITLE_BRACKET_LINE
        )
        val current = modes.indexOf(ModuleConfig.titleBracketMode).coerceAtLeast(0)
        card.addView(M3.segmentGroup(this, labels, current, 4) { index ->
            ModuleConfig.titleBracketMode = modes[index]
            ModuleConfig.push(this)
        })
        list.addView(M3.card(this, card))

        list.addView(M3.card(this, M3.tipContent(this,
            "AOD / 迷你播放器选项需重启系统界面后生效。\n\n" +
                "默认：原样显示括号内容。缩小：括号内容缩小并置于标题右侧。隐藏：去除括号内容。" +
                "分行：括号副标题叠在标题下方；主标题固定字号，显示不全用省略号。")))
    }
}
