package com.leowalk.musiclockscreen.xposed

/**
 * 锁屏歌词切行动画策略（AOD / 息屏不播）。
 * 滑动模式下位移用 smoothstep，透明度略快于位移收干净，减轻「整块硬推」感。
 */
internal object LyricLineTransitionPolicy {

    const val FADE = "fade"
    const val SLIDE_LEFT = "slide_left"
    const val SLIDE_RIGHT = "slide_right"
    const val SLIDE_UP = "slide_up"
    const val SLIDE_DOWN = "slide_down"

    fun normalize(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            SLIDE_LEFT, SLIDE_RIGHT, SLIDE_UP, SLIDE_DOWN -> raw.trim().lowercase()
            else -> FADE
        }
    }

    /** 亮屏才播；AOD / 息屏直接换词。 */
    fun shouldAnimate(screenInteractive: Boolean): Boolean = screenInteractive

    data class Transform(val alpha: Float, val tx: Float, val ty: Float)

    /** Hermite smoothstep：位移两端加速度为 0。 */
    fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /**
     * @param progress 0=可见原位，1=完全离场
     */
    fun exitTransform(mode: String, progress: Float, slidePx: Float): Transform {
        val p = progress.coerceIn(0f, 1f)
        val move = smoothstep(p)
        // 透明度略快结束，避免末端还拖着半透明硬块
        val alpha = (1f - smoothstep((p * 1.12f).coerceAtMost(1f))).coerceIn(0f, 1f)
        val d = slidePx.coerceAtLeast(0f)
        return when (normalize(mode)) {
            SLIDE_LEFT -> Transform(alpha, -d * move, 0f)
            SLIDE_RIGHT -> Transform(alpha, d * move, 0f)
            SLIDE_UP -> Transform(alpha, 0f, -d * move)
            SLIDE_DOWN -> Transform(alpha, 0f, d * move)
            else -> Transform(1f - p, 0f, 0f)
        }
    }

    /**
     * @param progress 0=入场起点，1=可见原位
     */
    fun enterTransform(mode: String, progress: Float, slidePx: Float): Transform {
        val p = progress.coerceIn(0f, 1f)
        val move = smoothstep(p)
        val alpha = smoothstep((p * 1.08f).coerceAtMost(1f))
        val d = slidePx.coerceAtLeast(0f)
        val inv = 1f - move
        return when (normalize(mode)) {
            SLIDE_LEFT -> Transform(alpha, d * inv, 0f)
            SLIDE_RIGHT -> Transform(alpha, -d * inv, 0f)
            SLIDE_UP -> Transform(alpha, 0f, d * inv)
            SLIDE_DOWN -> Transform(alpha, 0f, -d * inv)
            else -> Transform(p, 0f, 0f)
        }
    }
}
