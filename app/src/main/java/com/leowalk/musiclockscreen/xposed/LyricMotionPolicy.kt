package com.leowalk.musiclockscreen.xposed

import android.view.animation.Interpolator

/**
 * 全模块滑动 / 切行动画的统一时长与插值。
 *
 * 位移类动画走 AMLL 物理弹簧（[AmllSpringMotion]），避免三次缓动的「匀速段落」生硬感；
 * 纯淡入淡出仍用三次 ease。
 */
internal object LyricMotionPolicy {

    /** 沉浸三行上滑：对齐 AMLL posY 弹簧 settle */
    val STACK_SCROLL_MS: Long = AmllSpringMotion.settleMs(AmllSpringMotion.POS_Y)

    /** 普通切行离场 / 入场 */
    const val LINE_EXIT_MS = 240L
    val LINE_ENTER_MS: Long = (STACK_SCROLL_MS * 0.72f).toLong().coerceIn(280L, 520L)

    /** 锁屏进场 / 退场（[TransitionAnimator]） */
    val LYRIC_SURFACE_ENTER_MS: Long = AmllSpringMotion.settleMs(AmllSpringMotion.SCALE)
    const val LYRIC_SURFACE_EXIT_MS = 280L

    /** 滑动位移（dp）——略收，配合弹簧过冲不会「甩」太远 */
    const val LINE_SLIDE_DP = 24f
    const val SURFACE_SLIDE_DP = 24f

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

    /** AMLL 纵滑弹簧（欠阻尼，落点略弹） */
    fun springSlide(): Interpolator {
        return AmllSpringMotion.interpolator(
            AmllSpringMotion.POS_Y,
            STACK_SCROLL_MS / 1000f,
        )
    }

    /** 进场表面用稍沉的弹簧 */
    fun springSurface(): Interpolator {
        return AmllSpringMotion.interpolator(
            AmllSpringMotion.SCALE,
            LYRIC_SURFACE_ENTER_MS / 1000f,
        )
    }

    /** @deprecated 保留给测试/淡入淡出对照；滑动请用 [springSlide] */
    fun easeInOut(): Interpolator = springSlide()

    fun lineSlidePx(density: Float): Float {
        return LINE_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    fun surfaceSlidePx(density: Float): Float {
        return SURFACE_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    /** 滑动类切行用弹簧；纯淡入淡出用 easeIn / easeOut。 */
    fun forLineExit(mode: String): Interpolator {
        return if (LyricLineTransitionPolicy.normalize(mode) == LyricLineTransitionPolicy.FADE) {
            easeIn()
        } else {
            springSlide()
        }
    }

    fun forLineEnter(mode: String): Interpolator {
        return if (LyricLineTransitionPolicy.normalize(mode) == LyricLineTransitionPolicy.FADE) {
            easeOut()
        } else {
            springSlide()
        }
    }
}
