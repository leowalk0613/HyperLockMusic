package com.leowalk.musiclockscreen.xposed

import android.content.Context

/**
 * aod_full_media 开启时：锁屏媒体控件保持展开高度，不走息屏压缩/展开动画。
 */
internal object MediaAodExpandPolicy {

    private const val WAKEUP_AOD_FOLME_TYPE = 11030

    /**
     * @param onKeyguard 当前 KeyguardManager 是否 locked
     * @param inLinkage [KeyguardSleepTransition.isInLinkageAnimWindow]
     * @param goingToSleep [KeyguardSleepTransition.isGoingToSleep]
     */
    fun shouldKeepExpandedDuringSleepLinkage(
        aodFullMedia: Boolean,
        onKeyguard: Boolean,
        inLinkage: Boolean,
        goingToSleep: Boolean,
    ): Boolean {
        if (!aodFullMedia) return false
        if (onKeyguard) return true
        // 解锁后马上息屏：linkage 已开始但 keyguard 可能尚未 re-lock
        return inLinkage && goingToSleep
    }

    fun shouldKeepExpanded(context: Context?): Boolean {
        if (context == null) return false
        return shouldKeepExpandedDuringSleepLinkage(
            aodFullMedia = ConfigReader.aodFullMedia(context),
            onKeyguard = HookUtils.isOnKeyguard(context),
            inLinkage = KeyguardSleepTransition.isInLinkageAnimWindow(),
            goingToSleep = KeyguardSleepTransition.isGoingToSleep(),
        )
    }

    fun isWakeSleepFolmeType(type: Int): Boolean = type == WAKEUP_AOD_FOLME_TYPE

    /** 拦截非零 animateHeight，避免 Folme 逐帧改高度。 */
    fun shouldSuppressAnimateHeight(requestedHeight: Int): Boolean {
        return requestedHeight != 0
    }

    /** 高度变化时不触发展开/收缩动画。 */
    fun shouldForceSnapHeight(keepExpanded: Boolean, requestedAnimate: Boolean): Boolean {
        return keepExpanded && requestedAnimate
    }

    fun neutralWakeFolmeValue(methodName: String): Float {
        return when (methodName) {
            "setFolmeTranslationYForType" -> 0f
            else -> 1f
        }
    }
}
