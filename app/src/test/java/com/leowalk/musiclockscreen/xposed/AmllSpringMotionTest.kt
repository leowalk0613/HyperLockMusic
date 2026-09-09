package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmllSpringMotionTest {

    @Test
    fun posYParams_matchAmllDefaults() {
        val p = AmllSpringMotion.POS_Y
        assertEquals(0.9f, p.mass, 0.001f)
        assertEquals(15f, p.damping, 0.001f)
        assertEquals(90f, p.stiffness, 0.001f)
        assertEquals(false, p.soft)
    }

    @Test
    fun solve_startsAtFrom_endsNearTo() {
        val solver = AmllSpringMotion.solve(0f, 0f, 1f, AmllSpringMotion.POS_Y)
        assertEquals(0f, solver(0f), 0.001f)
        val settled = AmllSpringMotion.estimateSettleSeconds(AmllSpringMotion.POS_Y)
        assertTrue(settled in 0.25f..1.2f)
        assertEquals(1f, solver(settled), 0.02f)
    }

    @Test
    fun posY_isUnderdamped_midCanOvershoot() {
        // ζ = 15 / (2*sqrt(81)) ≈ 0.833 < 1 → 可过冲
        val solver = AmllSpringMotion.solve(0f, 0f, 1f, AmllSpringMotion.POS_Y)
        var maxPos = 0f
        var t = 0f
        while (t < 1.2f) {
            t += 1f / 120f
            maxPos = maxOf(maxPos, solver(t))
        }
        assertTrue("expected slight overshoot, max=$maxPos", maxPos > 1.001f)
    }

    @Test
    fun interpolator_bounds() {
        val settle = AmllSpringMotion.estimateSettleSeconds(AmllSpringMotion.POS_Y)
        val ease = AmllSpringMotion.interpolator(AmllSpringMotion.POS_Y, settle)
        assertEquals(0f, ease.getInterpolation(0f), 0.001f)
        assertEquals(1f, ease.getInterpolation(1f), 0.001f)
        val mid = ease.getInterpolation(0.5f)
        assertTrue(mid > 0.55f)
        assertTrue(mid < 1.2f)
    }

    @Test
    fun soft_noOvershoot() {
        val soft = AmllSpringMotion.Params(mass = 0.9f, damping = 15f, stiffness = 90f, soft = true)
        val solver = AmllSpringMotion.solve(0f, 0f, 1f, soft)
        var maxPos = 0f
        var t = 0f
        while (t < 1.5f) {
            t += 1f / 120f
            maxPos = maxOf(maxPos, solver(t))
        }
        assertTrue(maxPos <= 1.001f)
    }
}
