package com.leowalk.musiclockscreen.xposed

/**
 * 锁屏歌词切行动画策略（AOD / 息屏不播）。
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

    /**
     * @param progress 0=可见原位，1=完全离场
     */
    fun exitTransform(mode: String, progress: Float, slidePx: Float): Transform {
        val p = progress.coerceIn(0f, 1f)
        val alpha = 1f - p
        val d = slidePx.coerceAtLeast(0f)
        return when (normalize(mode)) {
            SLIDE_LEFT -> Transform(alpha, -d * p, 0f)
            SLIDE_RIGHT -> Transform(alpha, d * p, 0f)
            SLIDE_UP -> Transform(alpha, 0f, -d * p)
            SLIDE_DOWN -> Transform(alpha, 0f, d * p)
            else -> Transform(alpha, 0f, 0f)
        }
    }

    /**
     * @param progress 0=入场起点，1=可见原位
     */
    fun enterTransform(mode: String, progress: Float, slidePx: Float): Transform {
        val p = progress.coerceIn(0f, 1f)
        val alpha = p
        val d = slidePx.coerceAtLeast(0f)
        val inv = 1f - p
        return when (normalize(mode)) {
            // 左滑：旧词左出，新词从右侧进入
            SLIDE_LEFT -> Transform(alpha, d * inv, 0f)
            SLIDE_RIGHT -> Transform(alpha, -d * inv, 0f)
            SLIDE_UP -> Transform(alpha, 0f, d * inv)
            SLIDE_DOWN -> Transform(alpha, 0f, -d * inv)
            else -> Transform(alpha, 0f, 0f)
        }
    }
}
