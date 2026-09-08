package com.leowalk.musiclockscreen.xposed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View

/**
 * 过渡动画管理器
 *
 * 音乐锁屏进入/退出过渡动画；插值与位移统一走 [LyricMotionPolicy]。
 */
object TransitionAnimator {

    private const val DURATION_NOTIFICATION_HIDE = 320L
    private const val DURATION_NOTIFICATION_SHOW = 320L
    private const val NOTIFICATION_TRANSLATION_DP = 6f

    private var currentAnimator: AnimatorSet? = null

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun playLyricEnterAnimation(
        lyricView: View,
        onEnd: (() -> Unit)? = null
    ) {
        cancelCurrent()

        val translationPx = LyricMotionPolicy.surfaceSlidePx(lyricView.resources.displayMetrics.density)
        val duration = LyricMotionPolicy.LYRIC_SURFACE_ENTER_MS
        val ease = LyricMotionPolicy.easeOut()

        lyricView.alpha = 0f
        lyricView.translationY = translationPx
        lyricView.scaleX = 0.96f
        lyricView.scaleY = 0.96f
        lyricView.visibility = View.VISIBLE

        val alpha = ObjectAnimator.ofFloat(lyricView, "alpha", 0f, 1f).apply {
            this.duration = duration
            interpolator = ease
        }
        val translate = ObjectAnimator.ofFloat(lyricView, "translationY", translationPx, 0f).apply {
            this.duration = duration
            interpolator = LyricMotionPolicy.easeInOut()
        }
        val scaleX = ObjectAnimator.ofFloat(lyricView, "scaleX", 0.96f, 1f).apply {
            this.duration = duration
            interpolator = ease
        }
        val scaleY = ObjectAnimator.ofFloat(lyricView, "scaleY", 0.96f, 1f).apply {
            this.duration = duration
            interpolator = ease
        }

        val set = AnimatorSet()
        set.playTogether(alpha, translate, scaleX, scaleY)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                currentAnimator = null
                lyricView.alpha = 1f
                lyricView.translationY = 0f
                lyricView.scaleX = 1f
                lyricView.scaleY = 1f
                lyricView.visibility = View.VISIBLE
                logI("lyric enter animation ended")
                onEnd?.invoke()
            }

            override fun onAnimationCancel(animation: Animator) {
                currentAnimator = null
            }
        })
        currentAnimator = set
        set.start()
        logI("lyric enter animation started")
    }

    fun playLyricExitAnimation(
        lyricView: View,
        onEnd: (() -> Unit)? = null
    ) {
        cancelCurrent()

        val translationPx = LyricMotionPolicy.surfaceSlidePx(lyricView.resources.displayMetrics.density)
        val duration = LyricMotionPolicy.LYRIC_SURFACE_EXIT_MS
        val easeIn = LyricMotionPolicy.easeIn()
        val easeSlide = LyricMotionPolicy.easeInOut()

        val alpha = ObjectAnimator.ofFloat(lyricView, "alpha", 1f, 0f).apply {
            this.duration = duration
            interpolator = easeIn
        }
        val translate = ObjectAnimator.ofFloat(lyricView, "translationY", 0f, translationPx).apply {
            this.duration = duration
            interpolator = easeSlide
        }
        val scaleX = ObjectAnimator.ofFloat(lyricView, "scaleX", 1f, 0.96f).apply {
            this.duration = duration
            interpolator = easeIn
        }
        val scaleY = ObjectAnimator.ofFloat(lyricView, "scaleY", 1f, 0.96f).apply {
            this.duration = duration
            interpolator = easeIn
        }

        val set = AnimatorSet()
        set.playTogether(alpha, translate, scaleX, scaleY)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                currentAnimator = null
                lyricView.visibility = View.GONE
                lyricView.alpha = 1f
                lyricView.translationY = 0f
                lyricView.scaleX = 1f
                lyricView.scaleY = 1f
                (lyricView as? LockscreenLyricView)?.refreshVisibility()
                logI("lyric exit animation ended")
                onEnd?.invoke()
            }

            override fun onAnimationCancel(animation: Animator) {
                currentAnimator = null
            }
        })
        currentAnimator = set
        set.start()
        logI("lyric exit animation started")
    }

    fun animateNotificationsHide(
        views: List<View>,
        staggerDelayMs: Long = 20L,
        maxStaggerMs: Long = 120L,
        onEnd: (() -> Unit)? = null
    ) {
        if (views.isEmpty()) {
            onEnd?.invoke()
            return
        }
        val animators = mutableListOf<Animator>()
        val translationPx = NOTIFICATION_TRANSLATION_DP * views[0].resources.displayMetrics.density
        val ease = LyricMotionPolicy.easeInOut()

        for ((index, view) in views.withIndex()) {
            val delay = (index * staggerDelayMs).coerceAtMost(maxStaggerMs)
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                duration = DURATION_NOTIFICATION_HIDE
                startDelay = delay
                interpolator = LyricMotionPolicy.easeOut()
            }
            val translate = ObjectAnimator.ofFloat(view, "translationY", 0f, -translationPx).apply {
                duration = DURATION_NOTIFICATION_HIDE
                startDelay = delay
                interpolator = ease
            }
            animators.addAll(listOf(alpha, translate))
        }
        val set = AnimatorSet()
        set.playTogether(animators)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                views.forEach {
                    it.visibility = View.GONE
                    it.alpha = 1f
                    it.translationY = 0f
                }
                onEnd?.invoke()
            }
        })
        set.start()
    }

    fun animateNotificationsShow(
        views: List<View>,
        staggerDelayMs: Long = 20L,
        maxStaggerMs: Long = 120L,
        onEnd: (() -> Unit)? = null
    ) {
        if (views.isEmpty()) {
            onEnd?.invoke()
            return
        }
        val animators = mutableListOf<Animator>()
        val translationPx = NOTIFICATION_TRANSLATION_DP * views[0].resources.displayMetrics.density
        val ease = LyricMotionPolicy.easeInOut()

        for ((index, view) in views.withIndex()) {
            val delay = (index * staggerDelayMs).coerceAtMost(maxStaggerMs)
            view.alpha = 0f
            view.translationY = -translationPx
            view.visibility = View.VISIBLE
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                duration = DURATION_NOTIFICATION_SHOW
                startDelay = delay
                interpolator = LyricMotionPolicy.easeOut()
            }
            val translate = ObjectAnimator.ofFloat(view, "translationY", -translationPx, 0f).apply {
                duration = DURATION_NOTIFICATION_SHOW
                startDelay = delay
                interpolator = ease
            }
            animators.addAll(listOf(alpha, translate))
        }
        val set = AnimatorSet()
        set.playTogether(animators)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        set.start()
    }

    fun cancelCurrent() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    fun createColorAnimator(
        fromColor: Int,
        toColor: Int,
        duration: Long,
        onUpdate: (Int) -> Unit
    ): ValueAnimator {
        val animator = ValueAnimator.ofArgb(fromColor, toColor).apply {
            this.duration = duration
            interpolator = LyricMotionPolicy.easeOut()
        }
        animator.addUpdateListener {
            onUpdate(it.animatedValue as Int)
        }
        return animator
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, "HyperLockMusic_Anim", msg, null)
    }
}
