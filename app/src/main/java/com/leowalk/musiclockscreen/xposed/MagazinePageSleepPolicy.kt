package com.leowalk.musiclockscreen.xposed

/**
 * 画报页灭屏：对齐官方 LeftLKS —— 结束 Activity，交回未 occlude 的锁屏，
 * 再走系统「锁屏 → AOD」同一条链路。
 *
 * 仅当系统已开启「锁屏样式一致」(full_screen_aod_on=1) 时，才护住 / 恢复 FullAOD；
 * 未开启时只清 occlude，绝不推 full_aod=1。
 */
internal object MagazinePageSleepPolicy {

    /** Activity → SystemUI：灭屏瞬间先清 occlude，抢在 doze 判定之前。 */
    const val ACTION_MAGAZINE_PAGE_GOING_TO_SLEEP =
        "com.leowalk.musiclockscreen.action.MAGAZINE_PAGE_GOING_TO_SLEEP"

    fun shouldFinishOnBroadcast(action: String?): Boolean {
        return action == android.content.Intent.ACTION_SCREEN_OFF ||
            action == android.content.Intent.ACTION_USER_PRESENT
    }

    /** 灭屏前清 occlude，让后续与「锁屏直接进 AOD」相同。 */
    fun shouldReleaseOccludeForLockscreenMatchedAod(chromeMagazine: Boolean): Boolean =
        chromeMagazine

    /**
     * 本轮拉起过模块画报页、且用户已开 FullAOD 时，禁止 changeAodStyleShown 关掉 FullAOD。
     */
    fun shouldBlockChangeAodStyleShown(
        magazinePageActive: Boolean,
        fullScreenAodOn: Boolean,
    ): Boolean = magazinePageActive && fullScreenAodOn

    /** 清 occlude 后仅在已开 FullAOD 时补发 state=1。 */
    fun shouldRestoreFullAodAfterMagazineSleep(
        didReleaseOcclude: Boolean,
        fullScreenAodOn: Boolean,
    ): Boolean = didReleaseOcclude && fullScreenAodOn
}
