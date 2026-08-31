package com.leowalk.musiclockscreen.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class MainHook : XposedModule() {

    companion object {
        private const val TAG = "HyperLockMusic"
        private const val PACKAGE_SYSTEMUI = "com.android.systemui"
        private const val PACKAGE_AOD = "com.miui.aod"
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            log(android.util.Log.INFO, TAG, "onPackageReady: " + param.packageName)
            when (param.packageName) {
                PACKAGE_SYSTEMUI -> {
                    installSafe(param.classLoader, this, "KeyguardOverlayHook") {
                        KeyguardOverlayHook().install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "MediaAlbumClickHook") {
                        MediaAlbumClickHook().install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "MediaKeyguardButtonHook") {
                        MediaKeyguardButtonHook.install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "MediaTitleSubtitleHook") {
                        MediaTitleSubtitleHook.install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "MediaExitHook") {
                        MediaExitHook.install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "NotificationStackHook") {
                        NotificationStackHook().install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "MediaScaleFixHook") {
                        MediaScaleFixHook().install(param.classLoader, this)
                    }
                    installSafe(param.classLoader, this, "MediaProgressHook") {
                        MediaProgressHook.install(param.classLoader, this) { p, t, m, tr ->
                            log(p, t, m, tr)
                        }
                    }
                    installSafe(param.classLoader, this, "StatusBarStateHook") {
                        StatusBarStateHook.install(param.classLoader, this) { p, t, m, tr ->
                            log(p, t, m, tr)
                        }
                    }
                    installSafe(param.classLoader, this, "LockscreenClockController") {
                        LockscreenClockController.install(param.classLoader, this)
                    }
                }
                PACKAGE_AOD -> {
                    installSafe(param.classLoader, this, "AodLyricHook") {
                        AodLyricHook().install(param.classLoader, this)
                    }
                }
            }
        } catch (e: Throwable) {
            log(android.util.Log.ERROR, TAG, "hook failed", e)
        }
    }

    private fun installSafe(
        classLoader: ClassLoader,
        module: XposedModule,
        name: String,
        block: () -> Unit
    ) {
        try {
            block()
            log(android.util.Log.INFO, TAG, "$name installed")
        } catch (e: Throwable) {
            log(android.util.Log.ERROR, TAG, "$name install error", e)
        }
    }
}
