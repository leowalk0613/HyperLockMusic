package com.leowalk.musiclockscreen.xposed

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable

/**
 * HyperOS 壁纸辅助：在「写音乐壁纸之前」捕获干净的原锁屏壁纸，供按钮恢复使用。
 *
 * 时序关键：WallpaperController.setMusicWallpaper 会先调用本类捕获原图，再写音乐壁纸。
 * 此刻锁屏壁纸(FLAG_LOCK)尚未被音乐壁纸污染，读到的是真正的原锁屏壁纸——
 * 因此在锁屏壁纸 ≠ 桌面壁纸时也能准确恢复（按桌面则会把锁屏覆盖成桌面壁纸）。
 */
object HyperOsWallpaperBridge {

    private const val TAG = "MusicLockScreen_HyperWp"

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun bindKeyguardPanel(panelVc: Any) {
        logI("bindKeyguardPanel ok")
    }

    /**
     * 捕获原锁屏壁纸。仅由 saveOriginalWallpaper 在写音乐壁纸之前调用。
     * 优先级：以 root 读系统持久锁屏壁纸文件（/data/system/theme_magic/.../lock_wallpaper.jpg，
     * 用户设定、不受音乐壁纸写 FLAG_LOCK 影响，锁屏≠桌面时也准确）→ WallpaperManager 锁屏兜底
     * → 桌面壁纸最后兜底。
     */
    fun captureOriginalLockWallpaper(context: Context): Bitmap? {
        readSystemLockWallpaperWithRoot()?.let { return it }
        readLockWallpaper(context)?.let { return it }
        return readFromWallpaperManager(context)
    }

    /** 以 root 直接读取系统持久化锁屏壁纸文件（干净源），失败返回 null。 */
    private fun readSystemLockWallpaperWithRoot(): Bitmap? {
        return try {
            val pb = ProcessBuilder(
                "su",
                "-c",
                "cat /data/system/theme_magic/users/0/wallpaper/image/lock_wallpaper.jpg; " +
                    "cat /data/system/theme_magic/users/0/singleton_wallpaper/lock_wallpaper.jpg 2>/dev/null"
            )
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val data = proc.inputStream.readBytes()
            proc.waitFor()
            if (data.isEmpty()) null
            else BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (_: Throwable) {
            null
        }
    }

    /** 锁屏壁纸读取（写音乐壁纸前调用，此时是干净的原锁屏壁纸）。 */
    private fun readLockWallpaper(context: Context): Bitmap? {
        return try {
            val d = WallpaperManager.getInstance(context).getDrawable(WallpaperManager.FLAG_LOCK)
            if (d is BitmapDrawable) d.bitmap else null
        } catch (_: Throwable) {
            null
        }
    }

    /** 桌面壁纸兜底（未单独设置锁屏壁纸、锁屏即桌面时命中）。 */
    private fun readFromWallpaperManager(context: Context): Bitmap? {
        return try {
            val d = WallpaperManager.getInstance(context).drawable
            if (d is BitmapDrawable) d.bitmap else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun logI(msg: String) {
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}