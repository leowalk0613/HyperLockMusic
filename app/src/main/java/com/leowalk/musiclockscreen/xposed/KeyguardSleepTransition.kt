package com.leowalk.musiclockscreen.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

/**
 * 音乐锁屏息屏/亮屏 **linkage 动画窗口**（约 1.5s），不含稳定 AOD 时段。
 *
 * 勿用 [HookUtils.isScreenInteractive] 判定过渡——息屏后整段 AOD 都为 false，
 * 会导致 hook 在 AOD 上每帧和系统动画打架（媒体控件卡顿）。
 */
internal object KeyguardSleepTransition {

    const val LINKAGE_MS = 1500L

    @Volatile
    private var goingToSleep = false

    private var suppressUntilUptimeMs = 0L
    private var screenReceiver: BroadcastReceiver? = null
    private var registeredContext: Context? = null

    fun isMusicLockscreenActive(): Boolean {
        return WallpaperController.isShowing() || MusicLockscreenManager.isShowing
    }

    /** linkage 动画窗口（按电源 → 动画结束），不是「整个 AOD」。 */
    fun isInLinkageAnimWindow(): Boolean {
        expireIfNeeded()
        if (goingToSleep) return true
        val until = suppressUntilUptimeMs
        return until > 0L && SystemClock.uptimeMillis() < until
    }

    /** @deprecated 用 [isInLinkageAnimWindow]；保留给仍依赖「非交互」语义的调用方。 */
    fun isInSleepTransition(context: Context?): Boolean = isInLinkageAnimWindow()

    fun onStartedGoingToSleep() {
        goingToSleep = true
        extendLinkageWindow()
    }

    fun onStartedWakingUp() {
        goingToSleep = false
        extendLinkageWindow()
    }

    fun onScreenPowerEvent() {
        extendLinkageWindow()
    }

    private fun extendLinkageWindow() {
        suppressUntilUptimeMs = SystemClock.uptimeMillis() + LINKAGE_MS
    }

    private fun expireIfNeeded() {
        val until = suppressUntilUptimeMs
        if (goingToSleep && until > 0L && SystemClock.uptimeMillis() >= until) {
            goingToSleep = false
        }
    }

    fun bindScreenEvents(context: Context) {
        if (screenReceiver != null) return
        val app = context.applicationContext
        registeredContext = app
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON -> onScreenPowerEvent()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        app.registerReceiver(screenReceiver, filter)
    }

    fun reset() {
        goingToSleep = false
        suppressUntilUptimeMs = 0L
    }

    fun unbind() {
        reset()
        val app = registeredContext ?: return
        try {
            screenReceiver?.let { app.unregisterReceiver(it) }
        } catch (_: Throwable) {
        }
        screenReceiver = null
        registeredContext = null
    }

    internal fun isGoingToSleep(): Boolean = goingToSleep

    internal fun markGoingToSleepForTest() {
        goingToSleep = true
    }
}
