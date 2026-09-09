package com.leowalk.musiclockscreen.xposed

/**
 * 普通模式 ↔ 画报模式切换：提示文案与立即退出当前展示态（纯函数 / 常量）。
 */
internal object ModeSwitchPolicy {

    /** App → SystemUI / 画报 Activity：立刻退出当前音乐锁屏或画报页。 */
    const val ACTION_EXIT_ACTIVE_PRESENTATION =
        "com.leowalk.musiclockscreen.action.EXIT_ACTIVE_PRESENTATION"

    const val SYSTEMUI_PACKAGE = "com.android.systemui"

    /** 主界面模式卡固定提醒。 */
    const val RESTART_SYSTEMUI_HINT =
        "切换普通模式 / 画报模式后，需重启系统界面才会完全生效（可用右上角重启按钮）。"

    /** Toast 短提示。 */
    const val RESTART_SYSTEMUI_TOAST =
        "已退出当前展示；请重启系统界面使新模式生效"

    fun shouldExitActivePresentation(
        wasMagazineMode: Boolean,
        wantMagazineMode: Boolean,
    ): Boolean = wasMagazineMode != wantMagazineMode
}
