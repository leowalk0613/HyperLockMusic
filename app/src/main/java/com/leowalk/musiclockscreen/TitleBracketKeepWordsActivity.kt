package com.leowalk.musiclockscreen

import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leowalk.musiclockscreen.xposed.TitleBracketKeepWordsPolicy

/**
 * 免处理词汇独立页：内置 / 自定义可勾选启用；自定义词左侧打叉删除。
 * 普通锁屏与画报共用同一词库。
 */
class TitleBracketKeepWordsActivity : BaseScrollingActivity() {

    private var listHost: LinearLayout? = null

    override fun titleText() = "免处理词汇"

    override fun buildContent(list: LinearLayout) {
        list.addView(
            M3.card(
                this,
                M3.tipContent(
                    this,
                    "勾选启用后：仅当歌名里出现的括号全部是免处理整词（如只有 (LIVE)）时，" +
                        "隐藏 / 缩小 / 分行都不会动它；与其它括号混用则全部按普通处理。" +
                        "词库与普通锁屏、画报共用。",
                ),
            ),
        )

        val card = M3.cardContent(this)
        card.addView(M3.title(this, "词库"))
        listHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(listHost)
        card.addView(
            MaterialButton(this).apply {
                text = "添加自定义词"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = M3.dp(this@TitleBracketKeepWordsActivity, 8f) }
                setOnClickListener { showAddDialog() }
            },
        )
        list.addView(M3.card(this, card))
        refreshList()
    }

    private fun refreshList() {
        val host = listHost ?: return
        host.removeAllViews()
        ModuleConfig.getTitleBracketKeepEntries().forEach { entry ->
            host.addView(wordRow(entry))
        }
    }

    private fun wordRow(entry: TitleBracketKeepWordsPolicy.Entry): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = M3.dp(this@TitleBracketKeepWordsActivity, 2f)
                bottomMargin = M3.dp(this@TitleBracketKeepWordsActivity, 2f)
            }
            minimumHeight = M3.dp(this@TitleBracketKeepWordsActivity, 44f)
        }

        val clearSize = M3.dp(this, 40f)
        if (!entry.builtin) {
            row.addView(
                ImageView(this).apply {
                    setImageResource(androidx.appcompat.R.drawable.abc_ic_clear_material)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        M3.attrColor(
                            this@TitleBracketKeepWordsActivity,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0xFFCAC4D0.toInt(),
                        ),
                    )
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = "删除"
                    isClickable = true
                    isFocusable = true
                    val ripple = TypedValue()
                    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                    if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId)
                    setOnClickListener {
                        ModuleConfig.removeTitleBracketKeepWord(entry.word)
                        ModuleConfig.push(this@TitleBracketKeepWordsActivity)
                        refreshList()
                    }
                },
                LinearLayout.LayoutParams(clearSize, clearSize),
            )
        } else {
            // 与自定义行对齐：左侧占位
            row.addView(android.view.View(this), LinearLayout.LayoutParams(clearSize, clearSize))
        }

        row.addView(
            TextView(this).apply {
                text = if (entry.builtin) "${entry.word}（内置）" else entry.word
                setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_ROW_TITLE_SP)
                setTextColor(
                    M3.attrColor(
                        this@TitleBracketKeepWordsActivity,
                        com.google.android.material.R.attr.colorOnSurface,
                        0xFFE6E1E5.toInt(),
                    ),
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = M3.dp(this@TitleBracketKeepWordsActivity, 4f)
                marginEnd = M3.dp(this@TitleBracketKeepWordsActivity, 8f)
            },
        )

        row.addView(
            CheckBox(this).apply {
                isChecked = entry.enabled
                setOnCheckedChangeListener { _, checked ->
                    ModuleConfig.setTitleBracketKeepWordEnabled(entry.word, checked)
                    ModuleConfig.push(this@TitleBracketKeepWordsActivity)
                }
            },
        )
        return row
    }

    private fun showAddDialog() {
        val pad = M3.dp(this, 20f)
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, M3.dp(this@TitleBracketKeepWordsActivity, 8f), pad, M3.dp(this@TitleBracketKeepWordsActivity, 4f))
        }
        val input = EditText(this).apply {
            hint = "例如 Remix / 现场"
            setSingleLine()
            setTextColor(
                M3.attrColor(
                    this@TitleBracketKeepWordsActivity,
                    com.google.android.material.R.attr.colorOnSurface,
                    0xFFE6E1E5.toInt(),
                ),
            )
            setHintTextColor(
                M3.attrColor(
                    this@TitleBracketKeepWordsActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt(),
                ),
            )
        }
        dialogView.addView(input)
        val err = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFB4AB.toInt())
            setPadding(0, M3.dp(this@TitleBracketKeepWordsActivity, 4f), 0, 0)
        }
        dialogView.addView(err)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("添加自定义词")
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
                        err.text = "已是内置词，请直接勾选"
                    !ModuleConfig.addTitleBracketKeepWord(raw) ->
                        err.text = "该词已在列表中"
                    else -> {
                        ModuleConfig.push(this)
                        refreshList()
                        dialog.dismiss()
                        Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }
}
