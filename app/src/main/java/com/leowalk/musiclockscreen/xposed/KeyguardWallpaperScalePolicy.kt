package com.leowalk.musiclockscreen.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

/**
 * 音乐锁屏息屏策略：按开关禁用 HyperOS 壁纸 [wallpaperScale] 缩放，仅保留 [wallpaperBlack] 压暗。
 *
 * 依据反编译 `KeyguardPanelViewController`：
 * - `linkageViewAnim` / Folme `WallpaperParam.wallpaperScale` → setWallpaperScale / doDeductedImageScaleAnim
 * - `doWallpaperBlackAnim` / Folme `wallpaperBlack` → enableWallPaperAnim + setWallPaperAnimProcess（压暗，保留）
 *
 * 时序：`onStartedGoingToSleep` 里先 `interactive=false` 再跑 linkage 动画，此时 PowerManager 仍可能 interactive=true，
 * 必须靠 [goingToSleep] 提前打开抑制窗口。
 *
 * 适用范围：开关开启且音乐锁屏激活（大专辑 / 沉浸 / 仅歌词均生效）。
 */
internal object KeyguardWallpaperScalePolicy {

    const val SUPPRESS_MS = 1500L

    @Volatile
    private var goingToSleep = false

    private var suppressUntilUptimeMs = 0L
    private var screenReceiver: BroadcastReceiver? = null
    private var registeredContext: Context? = null

    fun isMusicLockscreenActive(): Boolean {
        return WallpaperController.isShowing() || MusicLockscreenManager.isShowing
    }

    /** 息屏过渡时是否应取消 Folme scale / 打开抑制窗口。 */
    fun shouldHandleSleepTransition(context: Context): Boolean {
        return shouldHandleSleepTransition(
            disableWallpaperScale = ConfigReader.disableWallpaperScale(context),
            musicLockscreenActive = isMusicLockscreenActive(),
        )
    }

    fun shouldSuppress(context: Context?): Boolean {
        if (context == null) return false
        return shouldSuppressScale(
            disableWallpaperScale = ConfigReader.disableWallpaperScale(context),
            musicLockscreenActive = isMusicLockscreenActive(),
            inLinkageAnimWindow = KeyguardSleepTransition.isInLinkageAnimWindow() || goingToSleep,
            screenInteractive = HookUtils.isScreenInteractive(context),
        )
    }

    /** 纯决策：开关 + 音乐锁屏时，息屏过渡或整段 AOD 抑制壁纸缩放。 */
    fun shouldSuppressScale(
        disableWallpaperScale: Boolean,
        musicLockscreenActive: Boolean,
        inLinkageAnimWindow: Boolean,
        screenInteractive: Boolean,
    ): Boolean {
        if (!disableWallpaperScale || !musicLockscreenActive) return false
        if (inLinkageAnimWindow) return true
        return !screenInteractive
    }

    fun shouldHandleSleepTransition(
        disableWallpaperScale: Boolean,
        musicLockscreenActive: Boolean,
    ): Boolean {
        return disableWallpaperScale && musicLockscreenActive
    }

    /** WakefulnessLifecycle.onStartedGoingToSleep — 早于 linkageViewAnim / Folme wallpaperScale。 */
    fun onGoingToSleep() {
        goingToSleep = true
        extendSuppressWindow()
        cancelFolmeWallpaperScale()
    }

    fun onWakingUp() {
        goingToSleep = false
    }

    fun onScreenPowerEvent() {
        extendSuppressWindow()
        cancelFolmeWallpaperScale()
    }

    private fun extendSuppressWindow() {
        suppressUntilUptimeMs = SystemClock.uptimeMillis() + SUPPRESS_MS
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

    /** 取消进行中的 Folme wallpaperScale，避免 linkageViewAnim 继续写矩阵。 */
    fun cancelFolmeWallpaperScale() {
        try {
            val folmeClass = Class.forName("miuix.animation.Folme")
            val useValue = folmeClass.getMethod("useValue", Any::class.java)
                .invoke(null, "WallpaperParam") ?: return
            val clazz = useValue.javaClass
            clazz.getMethod("cancel", String::class.java).invoke(useValue, "wallpaperScale")
            clazz.getMethod("setTo", String::class.java, Any::class.java)
                .invoke(useValue, "wallpaperScale", 1.0f)
        } catch (_: Throwable) {
        }
    }

    /** 测试用：是否处于息屏过渡窗口。 */
    internal fun isGoingToSleep(): Boolean = goingToSleep

    /** 测试用：模拟 onGoingToSleep 的 flag（JVM 单测无 SystemClock）。 */
    internal fun markGoingToSleepForTest() {
        goingToSleep = true
    }
}
