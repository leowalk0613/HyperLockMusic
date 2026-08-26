package com.leowalk.musiclockscreen.xposed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator

/**
 * 过渡动画管理器
 *
 * 音乐锁屏进入/退出过渡动画，使用系统原生 Animator 方案。
 */
object TransitionAnimator {

    private const val DURATION_LYRIC_ENTER = 400L
    private const val DURATION_LYRIC_EXIT = 150L
    private const val DURATION_NOTIFICATION_HIDE = 280L
    private const val DURATION_NOTIFICATION_SHOW = 280L
    private const val LYRIC_ENTER_DELAY_MS = 120L

    private const val LYRIC_ENTER_TRANSLATION_DP = 36f
    private const val NOTIFICATION_TRANSLATION_DP = 8f

    private val easeOutInterpolator by lazy {
        PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }

    private val easeInInterpolator by lazy {
        PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }

    private var currentAnimator: AnimatorSet? = null

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun playLyricEnterAnimation(
        lyricView: View,
        onEnd: (() -> Unit)? = null
    ) {
        cancelCurrent()

        val translationPx = LYRIC_ENTER_TRANSLATION_DP * lyricView.resources.displayMetrics.density

        lyricView.alpha = 0f
        lyricView.translationY = translationPx
        lyricView.scaleX = 0.94f
        lyricView.scaleY = 0.94f
        lyricView.visibility = View.VISIBLE

        val alpha = ObjectAnimator.ofFloat(lyricView, "alpha", 0f, 1f).apply {
            duration = DURATION_LYRIC_ENTER
            interpolator = easeOutInterpolator
        }
        val translate = ObjectAnimator.ofFloat(lyricView, "translationY", translationPx, 0f).apply {
            duration = DURATION_LYRIC_ENTER
            interpolator = easeOutInterpolator
        }
        val scaleX = ObjectAnimator.ofFloat(lyricView, "scaleX", 0.94f, 1f).apply {
            duration = DURATION_LYRIC_ENTER
            interpolator = easeOutInterpolator
        }
        val scaleY = ObjectAnimator.ofFloat(lyricView, "scaleY", 0.94f, 1f).apply {
            duration = DURATION_LYRIC_ENTER
            interpolator = easeOutInterpolator
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

        val translationPx = LYRIC_ENTER_TRANSLATION_DP * lyricView.resources.displayMetrics.density

        val alpha = ObjectAnimator.ofFloat(lyricView, "alpha", 1f, 0f).apply {
            duration = DURATION_LYRIC_EXIT
            interpolator = easeInInterpolator
        }
        val translate = ObjectAnimator.ofFloat(lyricView, "translationY", 0f, translationPx).apply {
            duration = DURATION_LYRIC_EXIT
            interpolator = easeInInterpolator
        }
        val scaleX = ObjectAnimator.ofFloat(lyricView, "scaleX", 1f, 0.94f).apply {
            duration = DURATION_LYRIC_EXIT
            interpolator = easeInInterpolator
        }
        val scaleY = ObjectAnimator.ofFloat(lyricView, "scaleY", 1f, 0.94f).apply {
            duration = DURATION_LYRIC_EXIT
            interpolator = easeInInterpolator
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

        for ((index, view) in views.withIndex()) {
            val delay = (index * staggerDelayMs).coerceAtMost(maxStaggerMs)
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                duration = DURATION_NOTIFICATION_HIDE
                startDelay = delay
                interpolator = easeOutInterpolator
            }
            val translate = ObjectAnimator.ofFloat(view, "translationY", 0f, -translationPx).apply {
                duration = DURATION_NOTIFICATION_HIDE
                startDelay = delay
                interpolator = easeOutInterpolator
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

        for ((index, view) in views.withIndex()) {
            val delay = (index * staggerDelayMs).coerceAtMost(maxStaggerMs)
            view.alpha = 0f
            view.translationY = -translationPx
            view.visibility = View.VISIBLE
            val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                duration = DURATION_NOTIFICATION_SHOW
                startDelay = delay
                interpolator = easeOutInterpolator
            }
            val translate = ObjectAnimator.ofFloat(view, "translationY", -translationPx, 0f).apply {
                duration = DURATION_NOTIFICATION_SHOW
                startDelay = delay
                interpolator = easeOutInterpolator
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
            interpolator = easeOutInterpolator
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
