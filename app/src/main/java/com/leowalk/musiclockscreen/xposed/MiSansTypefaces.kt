package com.leowalk.musiclockscreen.xposed

import android.graphics.Typeface
import java.io.File

/** 应用进程 / SystemUI 共用的 MiSans 解析（文件优先，族名回退）。 */
internal object MiSansTypefaces {

    @Volatile
    private var cachedBold: Typeface? = null

    @Volatile
    private var cachedMedium: Typeface? = null

    fun bold(): Typeface = resolve(bold = true)

    fun medium(): Typeface = resolve(bold = false)

    private fun resolve(bold: Boolean): Typeface {
        if (bold) cachedBold?.let { return it } else cachedMedium?.let { return it }
        val paths = if (bold) {
            arrayOf(
                "/system/fonts/MiSans-Heavy.ttf",
                "/system/fonts/MiSans-Bold.ttf",
                "/system/fonts/MiSans-Semibold.ttf",
                "/system/fonts/MiSans-Demibold.ttf",
                "/product/fonts/MiSans-Bold.ttf",
                "/product/fonts/MiSans-Heavy.ttf",
                "/system/fonts/MiSansVF.ttf",
                "/system/fonts/MiSansVF.otf",
            )
        } else {
            arrayOf(
                "/system/fonts/MiSans-Medium.ttf",
                "/system/fonts/MiSans-Regular.ttf",
                "/system/fonts/MiSans-Demibold.ttf",
                "/product/fonts/MiSans-Regular.ttf",
                "/product/fonts/MiSans-Medium.ttf",
                "/system/fonts/MiSansVF.ttf",
            )
        }
        for (path in paths) {
            try {
                val file = File(path)
                if (!file.exists()) continue
                val tf = Typeface.createFromFile(file)
                if (bold) cachedBold = tf else cachedMedium = tf
                return tf
            } catch (_: Throwable) {
            }
        }
        val families = if (bold) {
            arrayOf("misans-bold", "misans-heavy", "MiSans", "mipro-bold", "mipro-heavy", "sans-serif-black")
        } else {
            arrayOf("misans-medium", "misans-regular", "MiSans", "mipro-medium", "sans-serif-medium")
        }
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        val defaultFace = Typeface.create(Typeface.DEFAULT, style)
        for (name in families) {
            try {
                val tf = Typeface.create(name, style)
                if (tf != null && tf !== Typeface.DEFAULT && tf !== defaultFace) {
                    if (bold) cachedBold = tf else cachedMedium = tf
                    return tf
                }
            } catch (_: Throwable) {
            }
        }
        if (bold) cachedBold = defaultFace else cachedMedium = defaultFace
        return defaultFace
    }
}
