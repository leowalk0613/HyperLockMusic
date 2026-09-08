package com.leowalk.musiclockscreen.xposed

import android.view.animation.Interpolator

/**
 * 全模块滑动 / 切行动画的统一时长与插值。
 * 一律避免 Linear；位移略短、时长略长；曲线用三次缓动（不依赖 PathInterpolator）。
 */
internal object LyricMotionPolicy {

    /** 沉浸三行上滑 */
    const val STACK_SCROLL_MS = 420L

    /** 普通切行离场 / 入场 */
    const val LINE_EXIT_MS = 200L
    const val LINE_ENTER_MS = 280L

    /** 锁屏进场 / 退场（[TransitionAnimator]） */
    const val LYRIC_SURFACE_ENTER_MS = 440L
    const val LYRIC_SURFACE_EXIT_MS = 240L

    /** 滑动位移（dp） */
    const val LINE_SLIDE_DP = 28f
    const val SURFACE_SLIDE_DP = 28f

    private fun clamp01(t: Float): Float = t.coerceIn(0f, 1f)

    /** 快起慢收（淡入、落点） */
    fun easeOut(): Interpolator = Interpolator { t ->
        val x = 1f - clamp01(t)
        1f - x * x * x
    }

    /** 慢起快收（淡出） */
    fun easeIn(): Interpolator = Interpolator { t ->
        val x = clamp01(t)
        x * x * x
    }

    /** 滑动专用：两端都软 */
    fun easeInOut(): Interpolator = Interpolator { t ->
        val x = clamp01(t)
        if (x < 0.5f) {
            4f * x * x * x
        } else {
            val u = -2f * x + 2f
            1f - (u * u * u) / 2f
        }
    }

    fun lineSlidePx(density: Float): Float {
        return LINE_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    fun surfaceSlidePx(density: Float): Float {
        return SURFACE_SLIDE_DP * density.coerceAtLeast(0.5f)
    }

    /** 滑动类切行用 easeInOut；纯淡入淡出用 easeIn / easeOut。 */
    fun forLineExit(mode: String): Interpolator {
        return if (LyricLineTransitionPolicy.normalize(mode) == LyricLineTransitionPolicy.FADE) {
            easeIn()
        } else {
            easeInOut()
        }
    }

    fun forLineEnter(mode: String): Interpolator {
        return if (LyricLineTransitionPolicy.normalize(mode) == LyricLineTransitionPolicy.FADE) {
            easeOut()
        } else {
            easeInOut()
        }
    }
}
