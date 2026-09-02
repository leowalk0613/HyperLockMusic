package com.leowalk.musiclockscreen

/** 关于页连点版本号彩蛋：进入制作壁纸页，与锁屏实机渲染脱钩。 */
object VersionEasterEgg {
    const val TAP_THRESHOLD = 5
    const val TAP_WINDOW_MS = 2_000L
    const val TOAST_MESSAGE = "恭喜你发现彩蛋！"

    /**
     * @return Pair(新的连点计数, 是否应触发彩蛋)
     */
    fun onTap(tapCount: Int, lastTapMs: Long, nowMs: Long): Pair<Int, Boolean> {
        val nextCount = if (nowMs - lastTapMs > TAP_WINDOW_MS) 1 else tapCount + 1
        val triggered = nextCount >= TAP_THRESHOLD
        return (if (triggered) 0 else nextCount) to triggered
    }
}
