package com.leowalk.musiclockscreen

import android.content.Context
import android.widget.TextView
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

    /** 工具栏上的"重启界面"入口按钮，供 MainActivity 使用。 */
    fun buildAction(ctx: Context, onConfirm: () -> Unit): TextView {
        return TextView(ctx).apply {
            text = "重启界面"
            setTextSize(14f)
            setTextColor(M3.attrColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFFFFFFFF.toInt()))
            setPadding(M3.dp(ctx, 16f), M3.dp(ctx, 6f), M3.dp(ctx, 16f), M3.dp(ctx, 6f))
            setOnClickListener { onConfirm() }
        }
    }
}