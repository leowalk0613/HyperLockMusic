package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertFalse
import org.junit.Test

class HyperMiBlurHelperTest {

    @Test
    fun canUseSystemMiuiBlurUtils_falseOnJvmUnitTest() {
        // App process / JVM tests 没有 SystemUI 的 MiuiBlurUtils；SystemUI 进程内应为 true。
        assertFalse(HyperMiBlurHelper.canUseSystemMiuiBlurUtils())
    }
}
