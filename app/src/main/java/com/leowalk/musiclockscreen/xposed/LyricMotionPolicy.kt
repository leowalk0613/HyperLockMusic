package com.leowalk.musiclockscreen.xposed

import android.view.animation.Interpolator
import android.view.ViewPropertyAnimator

/**
 * 全模块滑动 / 切行 / 槽位过渡的统一时长与插值。
 *
 * 位移类动画走 AMLL 物理弹簧（[AmllSpringMotion]）；
 * 纯淡入淡出仍可用三次 ease，但槽位交叉淡入默认也挂弹簧，避免系统 AccelerateDecelerate 生硬。
 */
internal object LyricMotionPolicy {

    /** 沉浸三行上滑 / 通用纵滑：对齐 AMLL posY 弹簧 settle */
    val STACK_SCROLL_MS: Long = AmllSpringMotion.settleMs(AmllSpringMotion.POS_Y)

    /** 普通切行离场 / 入场：与弹簧 settle 同窗，避免压扁曲线 */
    val LINE_EXIT_MS: Long = STACK_SCROLL_MS
    val LINE_ENTER_MS: Long = STACK_SCROLL_MS

    /** 锁屏进场 / 退场（[TransitionAnimator]） */
    val LYRIC_SURFACE_ENTER_MS: Long = AmllSpringMotion.settleMs(AmllSpringMotion.SCALE)
    val LYRIC_SURFACE_EXIT_MS: Long = STACK_SCROLL_MS

    /** 通知行轻位移 */
    val NOTIFICATION_SLIDE_MS: Long = STACK_SCROLL_MS

    /** 歌词 ↔ 专辑槽位交叉过渡 */
    val SLOT_CROSSFADE_MS: Long = STACK_SCROLL_MS

    /** 滑动位移（dp）——略收，配合弹簧过冲不会「甩」太远 */
    const val LINE_SLIDE_DP = 24f
    const val SURFACE_SLIDE_DP = 24f
    const val SLOT_SLIDE_DP = 12f
    const val NOTIFICATION_SLIDE_DP = 8f

    private fun clamp01(t: Float): Float = t.coerceIn(0f, 1f)

    /** 快起慢收（淡入） */
    fun easeOut(): Interpolator = Interpolator { t ->
        val x = 1f - clamp01(t)
        1f - x * x * x
    }

    /** 慢起快收（淡出） */
    fun easeIn(): Interpolator = Interpolator { t ->
        val x = clamp01(t)
        x * x * x
    }

    /** AMLL 纵滑弹簧；[durationMs] 必须与 ValueAnimator / ViewPropertyAnimator 时长一致 */
    fun springSlide(durationMs: Long = STACK_SCROLL_MS): Interpolator {
        return AmllSpringMotion.interpolator(
            AmllSpringMotion.POS_Y,
            durationMs.coerceAtLeast(50L) / 1000f,
        )
    }

    /** 进场表面用稍沉的弹簧 */
    fun springSurface(durationMs: Long = LYRIC_SURFACE_ENTER_MS): Interpolator {
        return AmllSpringMotion.interpolator(
            AmllSpringMotion.SCALE,
            durationMs.coerceAtLeast(50L) / 1000f,
        )
    }

    /** @deprecated 保留别名；滑动请用 [springSlide] */
    fun easeInOut(): Interpolator = springSlide()

    fun lineSlidePx(density: Float): Float {
        return LINE_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    fun surfaceSlidePx(density: Float): Float {
        return SURFACE_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    fun slotSlidePx(density: Float): Float {
        return SLOT_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    fun notificationSlidePx(density: Float): Float {
        return NOTIFICATION_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    /** 给 ViewPropertyAnimator 挂上弹簧时长与插值。 */
    fun applySpring(animator: ViewPropertyAnimator, durationMs: Long = SLOT_CROSSFADE_MS): ViewPropertyAnimator {
        return animator
            .setDuration(durationMs)
            .setInterpolator(springSlide(durationMs))
    }

    /** 滑动类切行用弹簧；纯淡入淡出用 easeIn / easeOut。 */
    fun forLineExit(mode: String): Interpolator {
        return if (LyricLineTransitionPolicy.normalize(mode) == LyricLineTransitionPolicy.FADE) {
            easeIn()
        } else {
            springSlide(LINE_EXIT_MS)
        }
    }

    fun forLineEnter(mode: String): Interpolator {
        return if (LyricLineTransitionPolicy.normalize(mode) == LyricLineTransitionPolicy.FADE) {
            easeOut()
        } else {
            springSlide(LINE_ENTER_MS)
        }
    }
}
