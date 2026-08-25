package com.leowalk.musiclockscreen

import android.widget.LinearLayout

/** 媒体标题：歌名括号内容显示方式。 */
class MediaTitleActivity : BaseScrollingActivity() {

    override fun titleText() = "媒体标题"

    override fun buildContent(list: LinearLayout) {
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
            "默认：原样显示。缩小：括号内容缩小并置于标题右侧。隐藏：去除括号内容。分行：括号副标题叠在标题下方，主副字号合计约等于原标题，宽度不足时同步缩小。")))
    }
}