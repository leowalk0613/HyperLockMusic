package com.leowalk.musiclockscreen

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leowalk.musiclockscreen.xposed.MagazineOtherSettingsPolicy
import com.leowalk.musiclockscreen.xposed.TitleBracketKeepWordsPolicy

/** 其他设置：壁纸模糊、媒体控件、简洁时钟、息屏缩放、锁屏常亮等。 */
class OtherSettingsActivity : BaseScrollingActivity() {

    private var clockOptionsBlock: LinearLayout? = null
    private var keepWordsHeader: TextView? = null
    private var keepWordsPanel: LinearLayout? = null
    private var keepWordsList: LinearLayout? = null
    private var keepWordsExpanded: Boolean = false

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
            mediaCard.addView(M3.title(this, "媒体控件"))
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

            titleCard.addView(TextView(this).apply {
                keepWordsHeader = this
                setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_ROW_TITLE_SP)
                setTextColor(
                    M3.attrColor(
                        this@OtherSettingsActivity,
                        com.google.android.material.R.attr.colorOnSurface,
                        0xFFE6E1E5.toInt(),
                    ),
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = M3.dp(this@OtherSettingsActivity, 14f) }
                val ripple = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId)
                setPadding(0, M3.dp(this@OtherSettingsActivity, 8f), 0, M3.dp(this@OtherSettingsActivity, 8f))
                setOnClickListener {
                    keepWordsExpanded = !keepWordsExpanded
                    applyKeepWordsFoldState()
                }
            })
            titleCard.addView(TextView(this).apply {
                text = "点上方展开。开启的词不受隐藏 / 缩小 / 分行影响，括号原样留在主标题；自定义词可删除。"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
                setTextColor(
                    M3.attrColor(
                        this@OtherSettingsActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0xFFCAC4D0.toInt(),
                    ),
                )
            })

            keepWordsPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            keepWordsList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = M3.dp(this@OtherSettingsActivity, 4f) }
            }
            keepWordsPanel!!.addView(keepWordsList)
            keepWordsPanel!!.addView(MaterialButton(this).apply {
                text = "添加自定义词"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = M3.dp(this@OtherSettingsActivity, 8f) }
                setOnClickListener { showAddKeepWordDialog() }
            })
            titleCard.addView(keepWordsPanel)
            refreshKeepWordsList()
            applyKeepWordsFoldState()
            list.addView(M3.card(this, titleCard))
        }

        list.addView(M3.card(this, M3.tipContent(this,
            if (mag) {
                "画报模式：模糊 / 常亮 / 括号显示模式为画报独立档案；「免处理词汇」与普通锁屏互通。\n\n" +
                    "开启的词在隐藏 / 缩小 / 分行下仍留在主标题括号内。\n\n" +
                    "修改后可重新进入画报页查看效果。"
            } else {
                "模糊：壁纸用金字塔降采样 + box blur 烘焙（滑杆直接控制力度），锁屏再叠 MiBlur 遮罩；解锁清遮罩不影响桌面。改完请重新开关音乐锁屏。\n\n" +
                    "禁用息屏壁纸缩放对大专辑、沉浸封面与仅歌词模式均生效。\n\n" +
                    "AOD 完整媒体控件需重启系统界面后生效。\n\n" +
                    "歌名括号：默认原样显示；缩小置于标题右侧；隐藏去除括号；分行时主标题单行，有括号才显示副标题。\n\n" +
                    "免处理词汇（内置 / 自定义均可开关）不受上述三种模式影响，括号原样留在主标题；词库与画报共用。\n\n" +
                    "修改后需重启系统界面或重新开关音乐锁屏生效。"
            })))

        if (MagazineOtherSettingsPolicy.showMinimalClock(mag)) {
            refreshClockOptions()
        }
    }

    private fun refreshClockOptions() {
        M3.setControlsEnabled(clockOptionsBlock, ModuleConfig.minimalClock)
    }

    private fun applyKeepWordsFoldState() {
        keepWordsPanel?.visibility = if (keepWordsExpanded) View.VISIBLE else View.GONE
        keepWordsHeader?.text = TitleBracketKeepWordsPolicy.foldHeaderLabel(
            ModuleConfig.getTitleBracketKeepEntries(),
            keepWordsExpanded,
        )
    }

    private fun refreshKeepWordsList() {
        val host = keepWordsList ?: return
        host.removeAllViews()
        ModuleConfig.getTitleBracketKeepEntries().forEach { entry ->
            host.addView(keepWordRow(entry))
        }
        applyKeepWordsFoldState()
    }

    private fun keepWordRow(entry: TitleBracketKeepWordsPolicy.Entry): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = M3.dp(this@OtherSettingsActivity, 4f) }
        }
        val label = if (entry.builtin) "${entry.word}（内置）" else entry.word
        col.addView(
            M3.switchRow(
                this,
                label,
                if (entry.builtin) "关闭后按普通括号处理" else "关闭后按普通括号处理",
                entry.enabled,
            ) { checked ->
                ModuleConfig.setTitleBracketKeepWordEnabled(entry.word, checked)
                ModuleConfig.push(this)
                applyKeepWordsFoldState()
            },
        )
        if (!entry.builtin) {
            col.addView(
                MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                ).apply {
                    text = "删除「${entry.word}」"
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = M3.dp(this@OtherSettingsActivity, 0f)
                        bottomMargin = M3.dp(this@OtherSettingsActivity, 8f)
                    }
                    setOnClickListener { confirmDeleteKeepWord(entry.word) }
                },
            )
        }
        return col
    }

    private fun confirmDeleteKeepWord(word: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除自定义词")
            .setMessage("确定删除「$word」？两边设置页会同步移除。")
            .setPositiveButton("删除") { _, _ ->
                ModuleConfig.removeTitleBracketKeepWord(word)
                ModuleConfig.push(this)
                refreshKeepWordsList()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddKeepWordDialog() {
        val pad = M3.dp(this, 20f)
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, M3.dp(this@OtherSettingsActivity, 8f), pad, M3.dp(this@OtherSettingsActivity, 4f))
        }
        val input = EditText(this).apply {
            hint = "例如 Remix / 现场"
            setSingleLine()
            setTextColor(
                M3.attrColor(
                    this@OtherSettingsActivity,
                    com.google.android.material.R.attr.colorOnSurface,
                    0xFFE6E1E5.toInt(),
                ),
            )
            setHintTextColor(
                M3.attrColor(
                    this@OtherSettingsActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt(),
                ),
            )
        }
        dialogView.addView(input)
        val err = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFB4AB.toInt())
            setPadding(0, M3.dp(this@OtherSettingsActivity, 4f), 0, 0)
        }
        dialogView.addView(err)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("添加免处理词")
            .setMessage("开启后括号内全文匹配（忽略大小写）不受隐藏 / 缩小 / 分行影响。词库两边互通。")
            .setView(dialogView)
            .setPositiveButton("添加", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString()
                when {
                    raw.trim().isEmpty() -> err.text = "请输入词汇"
                    TitleBracketKeepWordsPolicy.isDefaultWord(raw) ->
                        err.text = "已是内置词，请直接开关"
                    !ModuleConfig.addTitleBracketKeepWord(raw) ->
                        err.text = "该词已在列表中"
                    else -> {
                        ModuleConfig.push(this)
                        keepWordsExpanded = true
                        refreshKeepWordsList()
                        dialog.dismiss()
                        Toast.makeText(this, "已添加，两边设置页同步", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }
}
