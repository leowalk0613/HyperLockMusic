package com.leowalk.musiclockscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class RootAccessTest {

    @Test
    fun probeAndCache_isStableUntilInvalidate() {
        RootAccess.invalidate()
        val first = RootAccess.probeAndCache()
        val cached = RootAccess.isGranted()
        assertEquals(first, cached)
        RootAccess.invalidate()
    }
}
