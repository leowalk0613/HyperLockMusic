package com.leowalk.musiclockscreen

import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 关于：版本号、项目信息、开源致谢与使用说明。 */
class AboutActivity : BaseScrollingActivity() {

    private var versionTapCount = 0
    private var lastVersionTapMs = 0L

    override fun titleText() = "关于"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "HyperLockMusic"))

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

        val infoCard = M3.cardContent(this)
        infoCard.addView(M3.title(this, "项目信息"))
        infoCard.addView(
            M3.cardEntryRow(this, "项目地址", "查看本仓库地址") {
                showProjectUrlDialog()
            }
        )
        infoCard.addView(
            M3.cardEntryRow(this, "致谢", "查看引用的开源项目") {
                showCreditsDialog()
            }
        )
        infoCard.addView(
            M3.cardEntryRow(this, "许可证", "MIT License", bottomMarginDp = 0f) {
                showLicenseDialog()
            }
        )
        list.addView(M3.card(this, infoCard))

        list.addView(M3.card(this, M3.tipContent(this,
            "基于 LSPosed 的 HyperLockMusic 模块，为重绘 HyperOS 锁屏界面的专辑背景与歌词而设计。\n\n" +
                "开启方式：在 LSPosed 中启用本模块并勾选 SystemUI（与需要生效的应用），重启系统界面后生效。\n\n" +
                "音乐锁屏开关位于锁屏媒体控件左侧自定义按钮，歌词开关在右侧。\n\n" +
                "无需为本应用开启自启动或后台保活：钩子在 SystemUI 内运行，歌词已落盘，进程被杀后会自动恢复。")))
    }

    private fun showProjectUrlDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("项目地址")
            .setMessage(OpenSourceCredits.PROJECT_REPO_URL)
            .setPositiveButton("在浏览器打开") { _, _ ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(OpenSourceCredits.PROJECT_REPO_URL))
                    )
                } catch (_: Throwable) {
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showCreditsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("致谢")
            .setMessage(OpenSourceCredits.aboutBody())
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showLicenseDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("许可证")
            .setMessage("本项目以 MIT License 发布。详见仓库根目录 LICENSE 文件。")
            .setPositiveButton("关闭", null)
            .show()
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
