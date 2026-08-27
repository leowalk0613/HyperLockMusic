package com.leowalk.musiclockscreen.xposed

import android.view.View

/**
 * 音乐锁屏激活时保持锁屏界面常亮（忽略系统自动息屏时长；手动关屏仍生效）。
 */
object KeepScreenController {

    private var targetView: View? = null

    fun bindLayer(layer: View?) {
        targetView = layer
        sync()
    }

    fun sync() {
        val view = targetView ?: return
        val ctx = view.context
        val keepOn = MusicLockscreenManager.isShowing &&
            ConfigReader.keepLockScreenOn(ctx) &&
            HookUtils.isOnKeyguard(ctx) &&
            HookUtils.isScreenInteractive(ctx)
        if (view.keepScreenOn != keepOn) {
            view.keepScreenOn = keepOn
        }
    }
}
