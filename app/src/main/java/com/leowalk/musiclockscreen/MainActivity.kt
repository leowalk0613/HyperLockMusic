package com.leowalk.musiclockscreen

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout

/**
 * 主界面：MaterialToolbar + 卡片化功能入口，
 * 参考 AodChange 的 UI 风格按功能分类跳转到子页面。
 */
class MainActivity : BaseScrollingActivity() {

    private var notificationAccessRow: View? = null

    override fun titleText() = "HyperLockMusic"

    override fun showHomeAsUp(): Boolean = false

    override fun buildToolbarAction(ctx: Context): View? =
        SystemUiRestart.buildAction(ctx) { confirmRestart() }

    override fun onResume() {
        super.onResume()
        refreshNotificationAccessRow()
    }

    override fun buildContent(list: LinearLayout) {
        list.addView(M3.card(this, M3.tipContent(this,
            "基于 LSPosed 的 HyperLockMusic 模块，为锁屏重绘专辑壁纸与歌词。" +
                "调整后可点右上角重启按钮让改动立即生效。")))

        val albumCard = M3.cardContent(this)
        albumCard.addView(
            M3.switchRow(
                this,
                "专辑封面",
                "关闭后锁屏不绘制大专辑 / 沉浸封面",
                ModuleConfig.showBigAlbum,
                titlePrimary = true,
                onTitleClick = {
                    startActivity(Intent(this, AlbumStyleActivity::class.java))
                },
            ) { checked ->
                ModuleConfig.showBigAlbum = checked
                ModuleConfig.push(this)
            }
        )
        list.addView(M3.card(this, albumCard))

        val lyricCard = M3.cardContent(this)
        lyricCard.addView(
            M3.switchRow(
                this,
                "歌词",
                "关闭后禁用整个歌词功能；锁屏按钮无法重新开启",
                ModuleConfig.lyricEnabled,
                titlePrimary = true,
                onTitleClick = {
                    startActivity(Intent(this, LyricStyleActivity::class.java))
                },
            ) { checked ->
                ModuleConfig.lyricEnabled = checked
                ModuleConfig.push(this)
            }
        )
        list.addView(M3.card(this, lyricCard))

        list.addView(M3.clickRow(this, "其他设置", "壁纸模糊/媒体控件/简洁时钟/息屏缩放/锁屏常亮") {
            startActivity(Intent(this, OtherSettingsActivity::class.java))
        })

        val whitelistCard = M3.cardContent(this)
        whitelistCard.addView(
            M3.switchRow(
                this,
                "音乐应用白名单",
                "开启后仅白名单内应用可开启/保持音乐锁屏",
                ModuleConfig.musicWhitelistEnabled,
                titlePrimary = true,
                onTitleClick = {
                    startActivity(Intent(this, AppWhitelistActivity::class.java))
                },
            ) { checked ->
                ModuleConfig.musicWhitelistEnabled = checked
                if (checked) ModuleConfig.ensureDefaultWhitelistIfEmpty()
                ModuleConfig.push(this)
            }
        )
        list.addView(M3.card(this, whitelistCard))

        val accessCard = M3.cardContent(this)
        accessCard.addView(M3.title(this, "权限"))
        notificationAccessRow = M3.clickRow(
            this,
            "通知使用权",
            notificationAccessDesc()
        ) {
            MediaSessionAccess.openNotificationAccessSettings(this)
        }
        accessCard.addView(notificationAccessRow)
        list.addView(M3.card(this, accessCard))

        list.addView(M3.clickRow(this, "关于", "版本号与使用说明") {
            startActivity(Intent(this, AboutActivity::class.java))
        })
    }

    private fun notificationAccessDesc(): String {
        return if (MediaSessionAccess.isNotificationAccessEnabled(this)) {
            "已开启：可更准确检测播放/退出，关闭音乐软件会自动退出音乐锁屏"
        } else {
            "未开启：点此到系统设置打开 HyperLockMusic 通知使用权"
        }
    }

    private fun refreshNotificationAccessRow() {
        val row = notificationAccessRow as? LinearLayout ?: return
        val textCol = row.getChildAt(0) as? LinearLayout ?: return
        val desc = textCol.getChildAt(1) as? android.widget.TextView ?: return
        desc.text = notificationAccessDesc()
    }

    private fun confirmRestart() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("重启系统界面")
            .setMessage("重启 SystemUI 会让模块的所有改动立即生效，锁屏与状态栏会短暂刷新。是否继续？")
            .setPositiveButton("重启") { _, _ -> SystemUiRestart.restart(this) }
            .setNegativeButton("取消", null)
            .show()
    }
}
