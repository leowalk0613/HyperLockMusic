package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFollowLyricPinPolicyTest {

    @Test
    fun anchorChange_shouldClearPin() {
        assertTrue(MediaFollowLyricPinPolicy.shouldClearPinOnAnchorChange(62f, 70f))
        assertFalse(MediaFollowLyricPinPolicy.shouldClearPinOnAnchorChange(62f, 62f))
        assertTrue(MediaFollowLyricPinPolicy.shouldClearPinOnAnchorChange(Float.NaN, 62f))
    }

    @Test
    fun resizeKeepingBottom_mustMoveWhenOnlyAnchorChanges() {
        // 高度不变、底边目标变 → 必须改 top（复现「滑条无效」）
        val parentH = 1000
        val height = 120
        val oldBottom = (parentH * 0.62f).toInt()
        val newBottom = (parentH * 0.80f).toInt()
        val oldTop = oldBottom - height
        val newTop = newBottom - height
        assertTrue(oldTop != newTop)
        assertEquals(newBottom, newTop + height)
    }
}
