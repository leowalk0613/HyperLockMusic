package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸专辑布局是否相对上次配置发生变化。
 * 非烘焙时不比较 centerY：Float.NaN != NaN 恒为 true，否则会误触发反复 setBitmap。
 */
internal fun immersiveAlbumLayoutChanged(
    bakeBefore: Boolean,
    bakeAfter: Boolean,
    centerBefore: Float,
    centerAfter: Float,
    edgeGradientBefore: Boolean = false,
    edgeGradientAfter: Boolean = false,
): Boolean {
    val centerChanged = bakeAfter &&
        !(centerBefore == centerAfter || (centerBefore.isNaN() && centerAfter.isNaN()))
    val edgeGradientChanged = bakeAfter && edgeGradientBefore != edgeGradientAfter
    return bakeBefore != bakeAfter || centerChanged || edgeGradientChanged
}
