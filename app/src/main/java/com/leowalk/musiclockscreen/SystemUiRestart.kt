package com.leowalk.musiclockscreen

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import kotlin.concurrent.thread

/**
 * 重启 SystemUI 界面：让模块改动全部生效。
 * 依次尝试 root 方式，任一成功即结束。
 */
object SystemUiRestart {

    fun restart(ctx: Context) {
        thread {
            try {
                runCommand(arrayOf("su", "-c", "killall com.android.systemui"))
            } catch (ignored: Exception) {}
            // 兜底：以 crash 方式强制拉起，同样能重启 SystemUI 进程
            try {
                runCommand(arrayOf("su", "-c", "am crash com.android.systemui"))
            } catch (ignored: Exception) {}
        }
    }

    private fun runCommand(cmd: Array<String>) {
        try {
            val p = Runtime.getRuntime().exec(cmd)
            p.waitFor()
        } catch (ignored: Exception) {}
    }

    /** 工具栏上的重启 SystemUI 图标按钮，供 MainActivity 使用。 */
    fun buildAction(ctx: Context, onConfirm: () -> Unit): View {
        val iconSize = M3.dp(ctx, 24f)
        val pad = M3.dp(ctx, 12f)
        val primary = M3.attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt())
        val ripple = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        return ImageView(ctx).apply {
            setImageDrawable(AppCompatResources.getDrawable(ctx, R.drawable.ic_restart_system_ui))
            imageTintList = ColorStateList.valueOf(primary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "重启界面"
            setPadding(pad, pad, pad, pad)
            if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId)
            layoutParams = android.view.ViewGroup.LayoutParams(iconSize + pad * 2, iconSize + pad * 2)
            setOnClickListener { onConfirm() }
        }
    }
}