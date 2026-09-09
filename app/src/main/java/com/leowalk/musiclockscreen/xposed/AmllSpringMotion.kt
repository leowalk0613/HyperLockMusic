package com.leowalk.musiclockscreen.xposed

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AMLL 弹簧位移求解（`utils/spring.ts` / pushkine，MIT）。
 *
 * 锁屏上不要直接用网页端 POS_Y 的「欠阻尼 + 长 settle」：
 * 切行是离场+入场串行，会拖到近 1s，且过冲 + MiBlur 重绘容易「卡」。
 * 位移统一走 [UI_SLIDE]（过阻尼 soft，约 300ms 落稳）。
 */
internal object AmllSpringMotion {

    data class Params(
        val mass: Float = 1f,
        val damping: Float = 10f,
        val stiffness: Float = 100f,
        val soft: Boolean = false,
    )

    /** AMLL 原文 posY（保留测试对照，勿直接用于锁屏时长） */
    val POS_Y = Params(mass = 0.9f, damping = 15f, stiffness = 90f, soft = false)

    /** AMLL scale（同样偏沉） */
    val SCALE = Params(mass = 2f, damping = 25f, stiffness = 100f, soft = false)

    /**
     * 锁屏滑动：soft 过阻尼，无过冲；刚度抬高，约 300ms 内视觉落稳。
     */
    val UI_SLIDE = Params(mass = 1f, damping = 36f, stiffness = 480f, soft = true)

    private const val ARRIVE_POS = 0.02f
    private const val ARRIVE_VEL = 0.12f

    fun solve(
        from: Float,
        velocity: Float,
        to: Float,
        params: Params = UI_SLIDE,
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

    fun estimateSettleSeconds(
        params: Params = UI_SLIDE,
        maxSeconds: Float = 1.2f,
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

    fun interpolator(
        params: Params = UI_SLIDE,
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

    fun settleMs(params: Params = UI_SLIDE): Long {
        // 锁屏动画上限压住，避免串行切行拖成「卡住」
        return (estimateSettleSeconds(params) * 1000f).toLong().coerceIn(240L, 420L)
    }
}
