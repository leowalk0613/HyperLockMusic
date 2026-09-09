package com.leowalk.musiclockscreen.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * 模式切换时立即退出：
 * - App 进程：结束 [MagazineMusicActivity]
 * - SystemUI：走音乐锁屏退出（复原壁纸 / 通知 / 勿扰等）
 */
object ModeSwitchExit {

    @Volatile
    private var systemUiReceiverRegistered = false

    private val systemUiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ModeSwitchPolicy.ACTION_EXIT_ACTIVE_PRESENTATION) return
            val ctx = context?.applicationContext ?: return
            try {
                if (WallpaperController.isShowing() ||
                    ConfigReader.isWallpaperActive(ctx)
                ) {
                    WallpaperController.restoreOriginalWallpaper(ctx)
                }
            } catch (_: Throwable) {
            }
        }
    }

    /** 设置页发起：通知本包画报页 + SystemUI 退出当前展示。 */
    fun requestFromApp(context: Context) {
        val action = ModeSwitchPolicy.ACTION_EXIT_ACTIVE_PRESENTATION
        try {
            context.sendBroadcast(
                Intent(action).setPackage(context.packageName),
            )
        } catch (_: Throwable) {
        }
        try {
            context.sendBroadcast(
                Intent(action).setPackage(ModeSwitchPolicy.SYSTEMUI_PACKAGE),
            )
        } catch (_: Throwable) {
        }
    }

    /** SystemUI 侧注册（锁屏 bind / 画报宿主均可调用，幂等）。 */
    fun ensureSystemUiReceiverRegistered(context: Context) {
        if (systemUiReceiverRegistered) return
        try {
            val filter = IntentFilter(ModeSwitchPolicy.ACTION_EXIT_ACTIVE_PRESENTATION)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(
                    systemUiReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(systemUiReceiver, filter)
            }
            systemUiReceiverRegistered = true
        } catch (_: Throwable) {
        }
    }
}
