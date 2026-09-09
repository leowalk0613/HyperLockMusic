package com.leowalk.musiclockscreen

import android.widget.Toast
import kotlin.concurrent.thread

/**
 * 主界面：MaterialToolbar + 卡片化功能入口，
 * 参考 AodChange 的 UI 风格按功能分类跳转到子页面。
 */
class MainActivity : BaseScrollingActivity() {

    private var notificationPermissionRow: M3.PermissionStatusRow? = null
    private var lockscreenDisplayPermissionRow: M3.PermissionStatusRow? = null
    private var rootPermissionRow: M3.PermissionStatusRow? = null
    private var albumSwitchRow: android.widget.LinearLayout? = null

    override fun titleText() = "HyperLockMusic"

    override fun showHomeAsUp(): Boolean = false

    override fun buildToolbarAction(ctx: android.content.Context): android.view.View? =
        SystemUiRestart.buildAction(ctx) { confirmRestart() }

    override fun onResume() {
        super.onResume()
        refreshPermissionRows()
        refreshModeDependentRows()
    }

    override fun buildContent(list: android.widget.LinearLayout) {
        list.addView(M3.card(this, M3.tipContent(this,
            "基于 LSPosed 的 HyperLockMusic 模块，为锁屏重绘专辑壁纸与歌词。" +
                "调整后可点右上角重启按钮让改动立即生效。")))

        val modeCard = M3.cardContent(this)
        modeCard.addView(M3.title(this, "锁屏模式", primary = true))
        val modeIndex = if (ModuleConfig.isMagazineMode) 1 else 0
        modeCard.addView(
            M3.segmentGroup(
                this,
                listOf("普通模式", "画报模式"),
                modeIndex,
                2,
            ) { index ->
                val wantMagazine = index == 1
                val wasMagazine = ModuleConfig.isMagazineMode
                if (!com.leowalk.musiclockscreen.xposed.ModeSwitchPolicy
                        .shouldExitActivePresentation(wasMagazine, wantMagazine)
                ) {
                    return@segmentGroup
                }
                ModuleConfig.setMagazineMode(enabled = wantMagazine)
                ModuleConfig.push(this)
                // 立刻退出当前展示（音乐锁屏复原壁纸/通知，或关闭画报页）
                com.leowalk.musiclockscreen.xposed.ModeSwitchExit.requestFromApp(this)
                Toast.makeText(
                    this,
                    com.leowalk.musiclockscreen.xposed.ModeSwitchPolicy.RESTART_SYSTEMUI_TOAST,
                    Toast.LENGTH_LONG,
                ).show()
                recreate()
            },
        )
        modeCard.addView(
            M3.stylePreviewRow(
                this,
                listOf("普通模式", "画报模式"),
                intArrayOf(
                    R.drawable.preview_mode_normal,
                    R.drawable.preview_mode_magazine,
                ),
                maxHeightDp = 220f,
            ),
        )
        modeCard.addView(
            android.widget.TextView(this).apply {
                text = "普通：改媒体控件 / 藏通知 / 藏勿扰条，大专辑或沉浸封面。\n" +
                    "画报：不改锁屏媒体/通知/勿扰；右划自建页显示大专辑或沉浸样式。" +
                    "需开启下方「锁屏显示」权限。\n" +
                    com.leowalk.musiclockscreen.xposed.ModeSwitchPolicy.RESTART_SYSTEMUI_HINT
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
                setTextColor(
                    M3.attrColor(
                        this@MainActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0xFFCAC4D0.toInt(),
                    ),
                )
                setPadding(0, M3.dp(this@MainActivity, 8f), 0, 0)
            },
        )
        list.addView(M3.card(this, modeCard))

        val albumCard = M3.cardContent(this)
        if (ModuleConfig.isMagazineMode) {
            albumCard.addView(
                M3.cardEntryRow(
                    this,
                    "画报页封面",
                    "大专辑 / 沉浸仅作用于右划自建页，不改锁屏",
                    bottomMarginDp = 0f,
                ) {
                    startActivity(android.content.Intent(this, AlbumStyleActivity::class.java))
                },
            )
            albumSwitchRow = null
        } else {
            albumCard.addView(
                M3.switchRow(
                    this,
                    "专辑封面",
                    "关闭后锁屏不绘制大专辑 / 沉浸封面",
                    ModuleConfig.showBigAlbum,
                    titlePrimary = true,
                    onTitleClick = {
                        startActivity(android.content.Intent(this, AlbumStyleActivity::class.java))
                    },
                ) { checked ->
                    ModuleConfig.showBigAlbum = checked
                    ModuleConfig.push(this)
                }
            )
            albumSwitchRow = albumCard.getChildAt(0) as? android.widget.LinearLayout
        }
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
                    startActivity(android.content.Intent(this, LyricStyleActivity::class.java))
                },
            ) { checked ->
                ModuleConfig.lyricEnabled = checked
                ModuleConfig.push(this)
            }
        )
        list.addView(M3.card(this, lyricCard))

        list.addView(M3.clickRow(
            this,
            "其他设置",
            if (ModuleConfig.isMagazineMode) {
                "画报页模糊 / 锁屏常亮 / 歌名括号"
            } else {
                "壁纸模糊 / 媒体控件 / 简洁时钟 / 息屏缩放 / 锁屏常亮"
            },
        ) {
            startActivity(android.content.Intent(this, OtherSettingsActivity::class.java))
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
                    startActivity(android.content.Intent(this, AppWhitelistActivity::class.java))
                },
            ) { checked ->
                ModuleConfig.musicWhitelistEnabled = checked
                if (checked) ModuleConfig.ensureDefaultWhitelistIfEmpty()
                ModuleConfig.push(this)
            }
        )
        list.addView(M3.card(this, whitelistCard))

        val accessCard = M3.cardContent(this)
        accessCard.addView(M3.title(this, "权限", primary = true))
        notificationPermissionRow = M3.permissionRow(
            this,
            "通知使用权",
            "更准确检测播放/退出，关闭音乐软件会自动退出音乐锁屏",
            MediaSessionAccess.isNotificationAccessEnabled(this),
        ) {
            MediaSessionAccess.openNotificationAccessSettings(this)
        }
        accessCard.addView(notificationPermissionRow!!.view)
        lockscreenDisplayPermissionRow = M3.permissionRow(
            this,
            "锁屏显示",
            "画报模式右划进自建页必需；未开则 Activity 无法盖在锁屏上",
            LockscreenDisplayAccess.isGranted(this),
        ) {
            LockscreenDisplayAccess.openSettings(this)
        }
        accessCard.addView(lockscreenDisplayPermissionRow!!.view)
        rootPermissionRow = M3.permissionRow(
            this,
            "Root 权限",
            "用于重启 SystemUI，使改动立即生效（右上角重启按钮）",
            granted = false,
            bottomMarginDp = 0f,
        ) {
            if (!RootAccess.isGranted()) {
                Toast.makeText(this, "请在 Root 管理器中向 Shell 授予 root 权限", Toast.LENGTH_SHORT).show()
            }
        }
        accessCard.addView(rootPermissionRow!!.view)
        list.addView(M3.card(this, accessCard))

        list.addView(M3.clickRow(this, "关于", "版本号与使用说明") {
            startActivity(android.content.Intent(this, AboutActivity::class.java))
        })

        refreshPermissionRows()
        refreshModeDependentRows()
    }

    private fun refreshModeDependentRows() {
        // 画报模式主界面用「画报页封面」入口，不在此处灰显开关
        albumSwitchRow?.let { M3.setControlsEnabled(it, true) }
    }

    private fun refreshPermissionRows() {
        notificationPermissionRow?.setGranted(
            MediaSessionAccess.isNotificationAccessEnabled(this),
        )
        lockscreenDisplayPermissionRow?.setGranted(
            LockscreenDisplayAccess.isGranted(this),
        )
        RootAccess.invalidate()
        thread {
            val rootGranted = RootAccess.probeAndCache()
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    rootPermissionRow?.setGranted(rootGranted)
                }
            }
        }
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
