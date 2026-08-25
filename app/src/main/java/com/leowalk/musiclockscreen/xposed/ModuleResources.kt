package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * 从模块 APK 加载资源（SystemUI 进程内使用）
 */
object ModuleResources {

    private const val PKG = "com.leowalk.musiclockscreen"

    fun drawable(context: Context, resName: String): Drawable? {
        return try {
            val pkgCtx = context.createPackageContext(PKG, Context.CONTEXT_IGNORE_SECURITY)
            val id = pkgCtx.resources.getIdentifier(resName, "drawable", PKG)
            if (id == 0) return null
            pkgCtx.resources.getDrawable(id, null)?.mutate()
        } catch (_: Throwable) {
            null
        }
    }
}
