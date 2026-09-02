package com.leowalk.musiclockscreen.xposed

/**
 * 沉浸壁纸上下沿暗色渐变：范围随专辑在屏上的留白动态伸缩。
 */
data class ImmersiveEdgeGradientSpans(
    val topSpanPx: Float,
    val bottomSpanPx: Float,
)

/** 专辑上方/下方可用空间越大，对应端渐变延伸越远。 */
fun computeImmersiveEdgeGradientSpans(
    wallpaperHeight: Float,
    albumTop: Float,
    albumBottom: Float,
): ImmersiveEdgeGradientSpans {
    val h = wallpaperHeight.coerceAtLeast(1f)
    val spaceAbove = albumTop.coerceIn(0f, h)
    val spaceBelow = (h - albumBottom).coerceIn(0f, h)
    val minSpan = h * 0.14f
    val maxSpan = h * 0.55f
    val topSpan = (spaceAbove * 0.85f + h * 0.08f).coerceIn(minSpan, maxSpan)
    val bottomSpan = (spaceBelow * 0.85f + h * 0.08f).coerceIn(minSpan, maxSpan)
    return ImmersiveEdgeGradientSpans(topSpan, bottomSpan)
}

/** 沿用全局暗色叠加强度，沉浸上下沿略加强以便锁屏可见。 */
fun computeImmersiveEdgeGradientPeakAlpha(darkOverlayAlpha: Int): Int {
    return (darkOverlayAlpha * 0.85f + 55f).toInt().coerceIn(100, 230)
}
