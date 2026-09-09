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
    fun uiSlide_isSoftAndSnappy() {
        val p = AmllSpringMotion.UI_SLIDE
        assertTrue(p.soft)
        val settle = AmllSpringMotion.settleMs(p)
        assertTrue("settle=$settle", settle in 240L..420L)
    }

    @Test
    fun solve_startsAtFrom_endsNearTo() {
        val solver = AmllSpringMotion.solve(0f, 0f, 1f, AmllSpringMotion.UI_SLIDE)
        assertEquals(0f, solver(0f), 0.001f)
        val settled = AmllSpringMotion.estimateSettleSeconds(AmllSpringMotion.UI_SLIDE)
        assertTrue(settled in 0.2f..0.5f)
        assertEquals(1f, solver(settled), 0.03f)
    }

    @Test
    fun posY_isUnderdamped_midCanOvershoot() {
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
    fun uiSlide_noOvershoot() {
        val solver = AmllSpringMotion.solve(0f, 0f, 1f, AmllSpringMotion.UI_SLIDE)
        var maxPos = 0f
        var t = 0f
        while (t < 1.0f) {
            t += 1f / 120f
            maxPos = maxOf(maxPos, solver(t))
        }
        assertTrue(maxPos <= 1.001f)
    }

    @Test
    fun interpolator_bounds() {
        val settle = AmllSpringMotion.estimateSettleSeconds(AmllSpringMotion.UI_SLIDE)
        val ease = AmllSpringMotion.interpolator(AmllSpringMotion.UI_SLIDE, settle)
        assertEquals(0f, ease.getInterpolation(0f), 0.001f)
        assertEquals(1f, ease.getInterpolation(1f), 0.001f)
        val mid = ease.getInterpolation(0.5f)
        assertTrue(mid > 0.55f)
        assertTrue(mid <= 1f)
    }
}
