package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * 持久化「原锁屏壁纸」多份备份，供按钮恢复使用。
 *
 * 说明：模块运行于 SystemUI 进程，传入的 context 就是 SystemUI 的 context。备份必须写到
 * SystemUI 自己能写的私有目录（context.filesDir，owner 即 systemui 进程），
 * 之前用 createPackageContext 取模块包目录，SystemUI 无权限落盘，导致备份从未写入成功。
 *
 * 维护两份：
 *  - recent：最近一次在「干净状态」下捕获的原壁纸（用户改锁屏壁纸后会刷新）。
 *  - first：首次备份，仅作 recent 缺失时的兜底。
 * load() 优先 recent，保证恢复的是用户当前设定的锁屏壁纸。
 */
object LockWallpaperBackup {

    private const val SUB_DIR = "music_lockscreen_wallpaper"
    private const val FIRST_FILE = "original_lock_wallpaper_first.jpg"
    private const val RECENT_FILE = "original_lock_wallpaper_recent.jpg"

    /** 保存一份干净原图。首次写入会同时落到 first（受保护份）；之后只刷新 recent。 */
    fun save(context: Context, bitmap: Bitmap) {
        val dir = backupDir(context) ?: return
        try {
            val first = File(dir, FIRST_FILE)
            if (!first.exists()) {
                FileOutputStream(first).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            val recent = File(dir, RECENT_FILE)
            FileOutputStream(recent).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (_: Throwable) {
            // 备份失败不阻断主流程
        }
    }

    /** 返回可用的原图：优先 recent（跟随用户改壁纸），其次 first。 */
    fun load(context: Context): Bitmap? {
        val dir = backupDir(context) ?: return null
        return try {
            val recent = File(dir, RECENT_FILE)
            if (recent.exists()) {
                BitmapFactory.decodeFile(recent.absolutePath)?.also { return it }
            }
            val first = File(dir, FIRST_FILE)
            if (first.exists()) {
                BitmapFactory.decodeFile(first.absolutePath)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /** 清除 recent；first 保留作兜底。恢复成功后调用，下次干净绑定时会重新 capture 并写入 recent。 */
    fun clear(context: Context) {
        val dir = backupDir(context) ?: return
        try {
            File(dir, RECENT_FILE).delete()
        } catch (_: Throwable) {
        }
    }

    /** 是否存在可用备份（recent 或 first 任一存在）。 */
    fun isBackupActive(context: Context): Boolean {
        val dir = backupDir(context) ?: return false
        return try {
            File(dir, FIRST_FILE).exists() ||
                File(dir, RECENT_FILE).exists()
        } catch (_: Throwable) {
            false
        }
    }

    /** 返回所有可用备份文件的绝对路径（供 root/调试兜底读取），按备份顺序。 */
    fun allBackupPaths(context: Context): List<String> {
        val dir = backupDir(context) ?: return emptyList()
        return try {
            listOf(File(dir, RECENT_FILE), File(dir, FIRST_FILE))
                .filter { it.exists() }
                .map { it.absolutePath }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun backupDir(context: Context): File? {
        return try {
            val dir = File(context.filesDir, SUB_DIR)
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (_: Throwable) {
            null
        }
    }
}