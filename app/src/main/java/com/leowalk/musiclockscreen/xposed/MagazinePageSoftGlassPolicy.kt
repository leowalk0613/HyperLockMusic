package com.leowalk.musiclockscreen.xposed

/**
 * 画报底栏柔光玻璃参数（对齐 HyperChanger 锁屏快捷方式 Soft Glass）。
 * 实际套用见 [HyperOsSoftGlass]：Legacy backdrop + MiGlassCompat，禁止平替。
 */
internal object MagazinePageSoftGlassPolicy {

    /** 是否给歌曲信息+按钮整体套柔光玻璃底。 */
    fun enabled(): Boolean = true

    /**
     * 玻璃层由 [com.leowalk.musiclockscreen.MagazineMusicActivity] 挂在壁纸与底栏之间
     *（与 HyperChanger 快捷方式同级采样壁纸）。Chrome 内不再嵌一层。
     */
    fun chromeEmbedsGlassLayer(): Boolean = false

    /** 圆角（dp）。 */
    const val CORNER_RADIUS_DP = 28f

    /** 玻璃层相对内容的内缩（dp）：四边一致。 */
    const val INSET_HORIZONTAL_DP = 8
    const val INSET_TOP_DP = 8
    const val INSET_BOTTOM_DP = 8

    /** 混色不透明度 0–100（HyperChanger 默认 10）。 */
    const val OPACITY_PERCENT = 10

    /** 合成器 backdrop 半径（HyperChanger softGlassBackdropBlurRadius 默认 80）。 */
    const val BACKDROP_BLUR_RADIUS = 80

    /** MiGlass 小半径（HyperChanger softGlassBlurRadius 默认 36）。 */
    const val GLASS_BLUR_RADIUS = 36

    /** 玻璃亮度（HyperChanger softGlassLuminance 默认 0.14）。 */
    const val LUMINANCE = 0.14f

    /** 混色底色（白，配合低 opacity）。 */
    const val TINT_COLOR = 0xFFFFFFFF.toInt()

    /** HyperChanger SHORTCUT_GLASS_BLUR_MODE / BLEND_MODE。 */
    const val BLUR_MODE = 1
    const val BLEND_MODE = 101

    /** 近透明源，供合成器注册（argb(1,255,255,255)）。 */
    fun seedDrawableColor(): Int = 0x01FFFFFF

    fun cornerRadiusPx(density: Float): Float =
        CORNER_RADIUS_DP * density.coerceAtLeast(0.01f)
}
