package com.leowalk.musiclockscreen

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * Material3 卡片化 UI 工厂：全面移植自 AodChange 项目的 M3.java。
 */
object M3 {

    fun card(ctx: Context, content: View): MaterialCardView {
        val card = MaterialCardView(ctx)
        card.radius = dp(ctx, 16f).toFloat()
        card.cardElevation = 0f
        card.setCardBackgroundColor(attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFF26252B.toInt()))
        card.strokeWidth = 0
        card.isClickable = false
        card.addView(content)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(ctx, 16f)
        card.layoutParams = lp
        return card
    }

    fun cardContent(ctx: Context): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 12f), dp(ctx, 16f), dp(ctx, 12f))
        }
    }

    fun title(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt()))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(ctx, 10f) }
        }
    }

    /**
     * 开关行：标题 + 可选说明 + 右侧 MaterialSwitch。
     */
    fun switchRow(ctx: Context, name: String, desc: String?, checked: Boolean,
                  onChanged: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.gravity = Gravity.CENTER_VERTICAL

        val textCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(ctx).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt()))
        })
        if (!desc.isNullOrEmpty()) {
            textCol.addView(TextView(ctx).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
                setPadding(0, dp(ctx, 2f), 0, 0)
            })
        }
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sw = MaterialSwitch(ctx)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, c -> onChanged(c) }
        row.addView(sw)

        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(ctx, 4f); bottomMargin = dp(ctx, 8f) }
        return row
    }

    /**
     * 滑块行：标题 + 右侧当前值 + 下方 Slider。
     */
    fun sliderRow(ctx: Context, name: String, valueFrom: Float, valueTo: Float, value: Float,
                  formatter: (Float) -> String, onChanged: (Float) -> Unit): LinearLayout {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(ctx).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt()))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueTv = TextView(ctx).apply {
            text = formatter(value)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt()))
            typeface = Typeface.DEFAULT_BOLD
        }
        head.addView(valueTv)
        col.addView(head)

        val slider = Slider(ctx)
        slider.valueFrom = valueFrom
        slider.valueTo = valueTo
        slider.value = value
        slider.stepSize = 1f
        slider.addOnChangeListener { _, v, fromUser ->
            valueTv.text = formatter(v)
            if (fromUser) onChanged(v)
        }
        col.addView(slider)

        col.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(ctx, 4f); bottomMargin = dp(ctx, 8f) }
        return col
    }

    /**
     * 可点击入口行：渐变色背景卡片 + 标题 + 说明。
     */
    fun clickRow(ctx: Context, name: String, desc: String?, listener: View.OnClickListener): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        row.gravity = Gravity.CENTER_VERTICAL

        val textCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(ctx).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt()))
            typeface = Typeface.DEFAULT_BOLD
        })
        if (!desc.isNullOrEmpty()) {
            textCol.addView(TextView(ctx).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
                setPadding(0, dp(ctx, 3f), 0, 0)
            })
        }
        row.addView(textCol)

        row.setPadding(dp(ctx, 12f), dp(ctx, 10f), dp(ctx, 12f), dp(ctx, 10f))
        val bg = GradientDrawable().apply {
            setColor(attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930.toInt()))
            cornerRadius = dp(ctx, 12f).toFloat()
        }
        row.background = bg
        if (listener != null) row.setOnClickListener(listener)
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(ctx, 12f) }
        return row
    }

    fun tipContent(ctx: Context, text: String): LinearLayout {
        val ll = cardContent(ctx)
        ll.addView(TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
            setLineSpacing(dp(ctx, 2f).toFloat(), 1f)
        })
        return ll
    }

    fun segmentButton(ctx: Context, label: String, checked: Boolean): com.google.android.material.button.MaterialButton {
        val btn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        btn.text = label
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        btn.isCheckable = true
        btn.isAllCaps = false
        btn.id = View.generateViewId()
        btn.isChecked = checked
        btn.maxLines = 1
        btn.ellipsize = null
        btn.minWidth = 0
        btn.minimumWidth = 0
        btn.minHeight = dp(ctx, 36f)
        btn.minimumHeight = dp(ctx, 36f)
        btn.insetTop = 0
        btn.insetBottom = 0
        val hPad = dp(ctx, 4f)
        btn.setPadding(hPad, dp(ctx, 6f), hPad, dp(ctx, 6f))
        val checkedBg = attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt())
        val checkedFg = attrColor(ctx, com.google.android.material.R.attr.colorOnPrimary, 0xFF000000.toInt())
        val uncheckedBg = attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930.toInt())
        val uncheckedFg = attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt())
        btn.backgroundTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(checkedBg, uncheckedBg)
        )
        btn.setTextColor(android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(checkedFg, uncheckedFg)
        ))
        btn.strokeWidth = 0
        return btn
    }

    /**
     * 样式效果图并排预览：标题在上、缩略图在下，等分宽度，高度上限避免撑满设置页。
     */
    fun stylePreviewRow(
        ctx: Context,
        labels: List<String>,
        @DrawableRes drawableIds: IntArray,
        maxHeightDp: Float = 200f,
    ): LinearLayout {
        require(labels.size == drawableIds.size && labels.isNotEmpty())
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 10f) }
        }
        val gap = dp(ctx, 8f)
        val maxH = dp(ctx, maxHeightDp)
        for (i in labels.indices) {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = gap
                }
            }
            col.addView(TextView(ctx).apply {
                text = labels[i]
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(ctx, 6f) }
            })
            val frame = MaterialCardView(ctx).apply {
                radius = dp(ctx, 12f).toFloat()
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(
                    attrColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF2B2930.toInt())
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            frame.addView(ImageView(ctx).apply {
                setImageResource(drawableIds[i])
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                maxHeight = maxH
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                contentDescription = labels[i]
            })
            col.addView(frame)
            row.addView(col)
        }
        return row
    }

    /**
     * 分段单选按钮组：每行 columns 个，跨行单选。
     * 同一行内等分宽度，缩小内边距以保证短文案完整显示。
     */
    fun segmentGroup(ctx: Context, labels: List<String>, selectedIndex: Int, columns: Int,
                     onSelect: (Int) -> Unit): LinearLayout {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val buttons = mutableListOf<com.google.android.material.button.MaterialButton>()
        val rows = (labels.size + columns - 1) / columns
        for (r in 0 until rows) {
            val group = com.google.android.material.button.MaterialButtonToggleGroup(ctx).apply {
                isSingleSelection = true
                isSelectionRequired = true
            }
            val end = minOf((r + 1) * columns, labels.size)
            for (i in r * columns until end) {
                val btn = segmentButton(ctx, labels[i], i == selectedIndex)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                group.addView(btn, lp)
                buttons.add(btn)
            }
            group.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                for (i in buttons.indices) {
                    if (buttons[i].id == checkedId) {
                        onSelect(i)
                        for (j in buttons.indices) buttons[j].isChecked = j == i
                        break
                    }
                }
            }
            group.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(ctx, 4f) }
            root.addView(group)
        }
        return root
    }

    /**
     * 取色器对话框：预设色板 + RGB 滑块 + HEX 输入。回调返回 RGB 颜色值。
     */
    fun colorPicker(ctx: Context, title: String, initial: Int, onPicked: (Int) -> Unit) {
        val cur = intArrayOf(initial and 0xFFFFFF)
        val bars = arrayOfNulls<SeekBar>(3)

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20f), dp(ctx, 16f), dp(ctx, 20f), 0)
        }

        val psize = dp(ctx, 48f)
        val preview = View(ctx).apply {
            background = colorDot(ctx, cur[0], psize)
        }
        col.addView(preview, LinearLayout.LayoutParams(psize, psize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(ctx, 12f)
        })

        val presets = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFF5F5F5.toInt(), 0xFFFFF8E1.toInt(), 0xFFECEFF1.toInt(),
            0xFFB0BEC5.toInt(), 0xFFB3E5FC.toInt(), 0xFF81D4FA.toInt(), 0xFF80DEEA.toInt(),
            0xFFA5D6A7.toInt(), 0xFFC5E1A5.toInt(), 0xFFE6EE9C.toInt(), 0xFFFFF59D.toInt(),
            0xFFFFCCBC.toInt(), 0xFFFFAB91.toInt(), 0xFFF48FB1.toInt(), 0xFFF8BBD0.toInt(),
            0xFFE1BEE7.toInt(), 0xFFCE93D8.toInt(), 0xFFB39DDB.toInt(), 0xFFFFE082.toInt(),
            0xFFFFCC80.toInt(), 0xFF90CAF9.toInt(), 0xFF80CBC4.toInt(), 0xFFF06292.toInt(),
            0xFF000000.toInt(), 0xFF37474F.toInt(), 0xFF455A64.toInt(), 0xFF546E7A.toInt(),
            0xFF607D8B.toInt(), 0xFF9E9E9E.toInt(), 0xFFBDBDBD.toInt(), 0xFFCFD8DC.toInt()
        )
        val perRow = 8
        val dotSize = dp(ctx, 26f)
        for (r in 0 until (presets.size + perRow - 1) / perRow) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (c in 0 until perRow) {
                val idx = r * perRow + c
                if (idx >= presets.size) break
                val pc = presets[idx]
                val dot = View(ctx).apply {
                    background = colorDot(ctx, pc, dotSize)
                    setOnClickListener {
                        cur[0] = pc
                        preview.background = colorDot(ctx, pc, psize)
                        bars[0]?.progress = Color.red(pc)
                        bars[1]?.progress = Color.green(pc)
                        bars[2]?.progress = Color.blue(pc)
                    }
                }
                row.addView(dot, LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    rightMargin = dp(ctx, 6f)
                    bottomMargin = dp(ctx, 6f)
                })
            }
            col.addView(row)
        }

        val names = arrayOf("红", "绿", "蓝")
        for (i in 0 until 3) {
            val fi = i
            val sb = SeekBar(ctx).apply {
                max = 255
                progress = when (fi) {
                    0 -> Color.red(cur[0]); 1 -> Color.green(cur[0]); else -> Color.blue(cur[0])
                }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val rr = if (fi == 0) progress else Color.red(cur[0])
                        val gg = if (fi == 1) progress else Color.green(cur[0])
                        val bb = if (fi == 2) progress else Color.blue(cur[0])
                        cur[0] = Color.rgb(rr, gg, bb)
                        preview.background = colorDot(ctx, cur[0], psize)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bars[fi] = sb
            col.addView(sb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(ctx, 4f) })
        }

        val hexRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val hex = EditText(ctx).apply {
            setText(String.format("#%06X", 0xFFFFFF and cur[0]))
            isSingleLine = true
            setTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFE6E1E5.toInt()))
            setHintTextColor(attrColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
        }
        hexRow.addView(hex, LinearLayout.LayoutParams(dp(ctx, 150f), ViewGroup.LayoutParams.WRAP_CONTENT))
        val hexBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "应用"
            setOnClickListener {
                try {
                    var t = hex.text.toString().trim()
                    if (t.startsWith("#")) t = t.substring(1)
                    val color = (t.toLong(16) and 0xFFFFFFL).toInt()
                    cur[0] = color
                    preview.background = colorDot(ctx, color, psize)
                    bars[0]?.progress = Color.red(color)
                    bars[1]?.progress = Color.green(color)
                    bars[2]?.progress = Color.blue(color)
                } catch (ignored: Exception) {}
            }
        }
        hexRow.addView(hexBtn)
        col.addView(hexRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(ctx, 8f) })

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(col)
            .setPositiveButton("确定") { _, _ -> onPicked(cur[0]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun colorDot(ctx: Context, color: Int, size: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(0xFF000000.toInt() or color)
            cornerRadius = size / 2f
        }
    }

    fun dp(ctx: Context, v: Float): Int {
        return (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun attrColor(ctx: Context, attr: Int, fallback: Int): Int {
        return MaterialColors.getColor(ctx, attr, fallback)
    }

    /**
     * 按模式启用/禁用一整块设置：半透明 + 子控件不可点，区分「当前模式不用」。
     */
    fun setControlsEnabled(root: View?, enabled: Boolean) {
        if (root == null) return
        root.alpha = if (enabled) 1f else 0.38f
        root.isEnabled = enabled
        when (root) {
            is Slider -> root.isEnabled = enabled
            is MaterialSwitch -> root.isEnabled = enabled
            is com.google.android.material.button.MaterialButton -> root.isEnabled = enabled
            is com.google.android.material.button.MaterialButtonToggleGroup -> {
                root.isEnabled = enabled
                for (i in 0 until root.childCount) {
                    root.getChildAt(i).isEnabled = enabled
                }
            }
            is ViewGroup -> {
                for (i in 0 until root.childCount) {
                    setControlsEnabled(root.getChildAt(i), enabled)
                }
            }
        }
    }
}