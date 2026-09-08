package com.leowalk.musiclockscreen.xposed

/**
 * 解锁 / 通知中心与「隐藏普通通知」交还 SystemUI 的时机。
 *
 * HyperOS 解锁时 [STATUS_SHADE] 常早于 [KeyguardManager.isKeyguardLocked]=false；
 * 若仍用 KM 门禁会漏掉 release，通知行保持 GONE 数秒直到 SystemUI 重建。
 */
internal object NotificationReleasePolicy {

    /**
     * 进入 STATUS_SHADE 时是否应立刻交还通知栈。
     * Shade 表示通知中心或已离开干净锁屏：应放出被藏起的行；
     * 回到 STATUS_KEYGUARD 且音乐锁屏仍开时会再 hide。
     */
    fun shouldReleaseOnStatusShade(moduleHidden: Boolean): Boolean = moduleHidden

    /**
     * 过滤条件已失效（非锁屏或未开音乐壁纸）但仍标记 hidden 时必须 release。
     */
    fun shouldReleaseWhenFilterInactive(
        shouldFilter: Boolean,
        moduleHidden: Boolean,
    ): Boolean = !shouldFilter && moduleHidden
}
