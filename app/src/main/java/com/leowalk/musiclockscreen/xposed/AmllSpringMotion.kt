package com.leowalk.musiclockscreen.xposed

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AMLL（applemusic-like-lyrics）弹簧位移求解，用于歌词滑动。
 *
 * 算法对齐 `@applemusic-like-lyrics/core` 的 `utils/spring.ts`
 *（原 spring 求解来自 github.com/pushkine，MIT）。
 *
 * 默认 [POS_Y] 与 AMLL `LyricPlayerBase.posYSpringParams` 一致：
 * mass=0.9 / damping=15 / stiffness=90（略欠阻尼，落点带一点弹性）。
 */
internal object AmllSpringMotion {

    data class Params(
        val mass: Float = 1f,
        val damping: Float = 10f,
        val stiffness: Float = 100f,
        val soft: Boolean = false,
    )

    /** AMLL 歌词行纵坐标弹簧 */
    val POS_Y = Params(mass = 0.9f, damping = 15f, stiffness = 90f)

    /** 进场 / 缩放类：更沉一点，少抖 */
    val SCALE = Params(mass = 2f, damping = 25f, stiffness = 100f)

    private const val ARRIVE_POS = 0.01f
    private const val ARRIVE_VEL = 0.01f

    fun solve(
        from: Float,
        velocity: Float,
        to: Float,
        params: Params = POS_Y,
    ): (Float) -> Float {
        val mass = params.mass.coerceAtLeast(1e-4f)
        val stiffness = params.stiffness.coerceAtLeast(1e-4f)
        val damping = params.damping
        val delta = to - from
        val critically =
            params.soft || damping / (2f * sqrt(stiffness * mass)) >= 1f

        if (critically) {
            val angular = -sqrt(stiffness / mass)
            val leftover = -angular * delta - velocity
            return { tRaw ->
                val t = tRaw
                if (t < 0f) from
                else to - (delta + t * leftover) * exp(t * angular)
            }
        }

        val dampingFrequency = sqrt(4f * mass * stiffness - damping * damping)
        val leftover = (damping * delta - 2f * mass * velocity) / dampingFrequency
        val dfm = (0.5f * dampingFrequency) / mass
        val dm = -(0.5f * damping) / mass
        return { tRaw ->
            val t = tRaw
            if (t < 0f) from
            else to - (cos(t * dfm) * delta + sin(t * dfm) * leftover) * exp(t * dm)
        }
    }

    /**
     * 估计弹簧落到目标附近所需秒数（零初速，0→1）。
     */
    fun estimateSettleSeconds(
        params: Params = POS_Y,
        maxSeconds: Float = 2f,
        sampleHz: Float = 120f,
    ): Float {
        val solver = solve(0f, 0f, 1f, params)
        val dt = 1f / sampleHz.coerceAtLeast(30f)
        var t = 0f
        var prev = 0f
        while (t < maxSeconds) {
            t += dt
            val pos = solver(t)
            val vel = (pos - prev) / dt
            prev = pos
            if (kotlin.math.abs(1f - pos) < ARRIVE_POS && kotlin.math.abs(vel) < ARRIVE_VEL) {
                return t
            }
        }
        return maxSeconds
    }

    /**
     * 把 0→1 弹簧轨迹映射成 [Interpolator]。
     * [durationSeconds] 应对齐动画时长；t=1 时强制落在 1，避免欠阻尼末端截断。
     */
    fun interpolator(
        params: Params = POS_Y,
        durationSeconds: Float,
    ): Interpolator {
        val duration = durationSeconds.coerceAtLeast(0.05f)
        val solver = solve(0f, 0f, 1f, params)
        return Interpolator { fraction ->
            val f = fraction.coerceIn(0f, 1f)
            if (f >= 1f) 1f
            else solver(f * duration)
        }
    }

    fun settleMs(params: Params = POS_Y): Long {
        return (estimateSettleSeconds(params) * 1000f).toLong().coerceIn(280L, 900L)
    }
}
