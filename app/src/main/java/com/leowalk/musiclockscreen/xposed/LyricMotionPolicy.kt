package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.Interpolator

/**
 * 全模块滑动 / 切行 / 槽位过渡的统一时长与插值。
 *
 * 位移走 AMLL 系 soft 弹簧 [AmllSpringMotion.UI_SLIDE]；时长刻意短于网页端 POS_Y settle，
 * 避免切行「离场+入场」串行显得卡，以及 MiBlur 视图长动画掉帧。
 */
internal object LyricMotionPolicy {

    private val settleMs: Long = AmllSpringMotion.settleMs(AmllSpringMotion.UI_SLIDE)

    /** 沉浸三行上滑 */
    val STACK_SCROLL_MS: Long = settleMs

    /** 普通切行：离场短、入场略长（串行总时长约 0.5s） */
    val LINE_EXIT_MS: Long = (settleMs * 0.55f).toLong().coerceIn(160L, 240L)
    val LINE_ENTER_MS: Long = (settleMs * 0.75f).toLong().coerceIn(220L, 320L)

    /** 锁屏进场 / 退场 */
    val LYRIC_SURFACE_ENTER_MS: Long = settleMs
    val LYRIC_SURFACE_EXIT_MS: Long = (settleMs * 0.7f).toLong().coerceIn(200L, 300L)

    val NOTIFICATION_SLIDE_MS: Long = settleMs
    val SLOT_CROSSFADE_MS: Long = (settleMs * 0.7f).toLong().coerceIn(200L, 300L)

    const val LINE_SLIDE_DP = 24f
    const val SURFACE_SLIDE_DP = 24f
    const val SLOT_SLIDE_DP = 12f
    const val NOTIFICATION_SLIDE_DP = 8f

    private val cachedSpringByDurationMs = HashMap<Long, Interpolator>()

    private fun clamp01(t: Float): Float = t.coerceIn(0f, 1f)

    fun easeOut(): Interpolator = Interpolator { t ->
        val x = 1f - clamp01(t)
        1f - x * x * x
    }

    fun easeIn(): Interpolator = Interpolator { t ->
        val x = clamp01(t)
        x * x * x
    }

    fun springSlide(durationMs: Long = STACK_SCROLL_MS): Interpolator {
        val key = durationMs.coerceAtLeast(50L)
        return cachedSpringByDurationMs.getOrPut(key) {
            AmllSpringMotion.interpolator(
                AmllSpringMotion.UI_SLIDE,
                key / 1000f,
            )
        }
    }

    fun springSurface(durationMs: Long = LYRIC_SURFACE_ENTER_MS): Interpolator {
        return springSlide(durationMs)
    }

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

    /**
     * 槽位交叉：默认淡入淡出 + 轻位移。
     * [allowTranslation]=false 时只做 alpha（MiBlur 歌词位移会整层合成掉帧）。
     */
    fun applySpring(
        animator: ViewPropertyAnimator,
        durationMs: Long = SLOT_CROSSFADE_MS,
    ): ViewPropertyAnimator {
        return animator
            .setDuration(durationMs)
            .setInterpolator(springSlide(durationMs))
    }

    fun shouldTranslateSlot(view: View): Boolean {
        // 带系统 MiBlur 的歌词 view 做 translation 极易掉帧
        return (view as? LockscreenLyricView)?.hasActiveMiBlur() != true
    }

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
