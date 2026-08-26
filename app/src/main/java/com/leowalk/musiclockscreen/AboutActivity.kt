package com.leowalk.musiclockscreen

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView

/** 关于：版本号与使用说明。 */
class AboutActivity : BaseScrollingActivity() {

    private var versionTapCount = 0
    private var lastVersionTapMs = 0L

    override fun titleText() = "关于"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "锁屏音乐"))

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        list.addView(M3.card(this, card.also { content ->
            val versionView = TextView(this).apply {
                text = "版本 $version"
                setTextSize(15f)
                setTextColor(M3.attrColor(this@AboutActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
                setOnClickListener { onVersionTapped() }
            }
            content.addView(versionView)
        }))

        list.addView(M3.card(this, M3.tipContent(this,
            "基于 LSPosed 的音乐锁屏模块，为重绘 HyperOS 锁屏界面的专辑背景与歌词而设计。\n\n" +
                "开启方式：在 LSPosed 中启用本模块并勾选 SystemUI（与需要生效的应用），重启系统界面后生效。\n\n" +
                "音乐锁屏开关位于锁屏媒体控件左侧自定义按钮，歌词开关在右侧。")))
    }

    private fun onVersionTapped() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTapMs > 2000L) versionTapCount = 0
        lastVersionTapMs = now
        versionTapCount++
        if (versionTapCount >= 5) {
            versionTapCount = 0
            startActivity(Intent(this, WallpaperMakerActivity::class.java))
        }
    }
}