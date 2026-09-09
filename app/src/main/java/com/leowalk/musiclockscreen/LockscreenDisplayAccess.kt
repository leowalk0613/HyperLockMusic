package com.leowalk.musiclockscreen

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * HyperOS / MIUI「锁屏显示」权限：第三方 Activity 盖在锁屏上必需。
 * AppOps 码 10020 为小米私有；检测失败时视为未知（不强制报未授权）。
 */
object LockscreenDisplayAccess {

    /** MIUI AppOps：锁屏显示 */
    const val OP_SHOW_WHEN_LOCKED = 10020

    fun isGranted(context: Context): Boolean {
        return when (checkOpMode(context)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_IGNORED,
            AppOpsManager.MODE_ERRORED,
            AppOpsManager.MODE_DEFAULT,
            -> false
            else -> false
        }
    }

    /**
     * @return AppOps mode，反射失败返回 null（无法检测）。
     */
    fun checkOpMode(context: Context): Int? {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return null
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            method.invoke(appOps, OP_SHOW_WHEN_LOCKED, Process.myUid(), context.packageName) as Int
        } catch (_: Throwable) {
            null
        }
    }

    fun openSettings(context: Context) {
        val pkg = context.packageName
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                putExtra("extra_pkgname", pkg)
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", pkg)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
            },
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Throwable) {
                // try next
            }
        }
    }

    fun modeAllowed(mode: Int?): Boolean =
        mode == AppOpsManager.MODE_ALLOWED
}
