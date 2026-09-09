package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 画报基底宿主：在 SystemUI 杂志层注入歌名/文案，并强制 isMagazineWallpaper。
 */
object MagazineHost {

    private const val TAG = "HyperLockMusic_Magazine"

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var controller: Any? = null

    @Volatile
    private var wallpaperInfoField: Field? = null

    @Volatile
    private var updateInfoMethod: Method? = null

    @Volatile
    private var supportLeftField: Field? = null

    @Volatile
    private var overrideActive: Boolean = false

    @Volatile
    private var title: String = ""

    @Volatile
    private var content: String = ""

    @Volatile
    private var artist: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    fun bindModule(module: XposedModule) {
        this.module = module
    }

    fun attachController(ctrl: Any) {
        controller = ctrl
        try {
            if (wallpaperInfoField == null) {
                wallpaperInfoField = ctrl.javaClass.getDeclaredField("mLockScreenMagazineWallpaperInfo")
                    .apply { isAccessible = true }
            }
            if (updateInfoMethod == null) {
                updateInfoMethod = ctrl.javaClass.getDeclaredMethod("updateLockScreenMagazineWallpaperInfo")
                    .apply { isAccessible = true }
            }
            if (supportLeftField == null) {
                supportLeftField = ctrl.javaClass.getDeclaredField("mIsSupportLockScreenMagazineLeft")
                    .apply { isAccessible = true }
            }
        } catch (e: Throwable) {
            logE("attachController reflect failed", e)
        }
        if (overrideActive) {
            applyOverrideAsync()
        }
    }

    fun shouldForceMagazine(context: Context?): Boolean {
        if (context == null) return false
        return MagazineModePolicy.shouldForceMagazineWallpaper(
            chromeMagazine = ConfigReader.isMagazineChrome(context),
            musicWallpaperShowing = WallpaperController.isShowing(),
        )
    }

    fun shouldSuppressLeft(context: Context?): Boolean {
        if (context == null) return overrideActive
        return MagazineModePolicy.shouldSuppressMagazineLeftSwipe(
            chromeMagazine = ConfigReader.isMagazineChrome(context),
            musicWallpaperShowing = WallpaperController.isShowing() || overrideActive,
        )
    }

    fun onMusicWallpaperShown(
        context: Context,
        songTitle: String,
        songArtist: String,
        lyricLine: String = "",
    ) {
        if (!ConfigReader.isMagazineChrome(context)) {
            clearOverride()
            return
        }
        title = songTitle.ifBlank { "正在播放" }
        artist = songArtist
        content = lyricLine.ifBlank { songArtist }
        overrideActive = true
        applyOverrideAsync()
        logI("override shown title=$title")
    }

    fun updateLyricLine(lyricLine: String) {
        if (!overrideActive) return
        content = lyricLine
        applyOverrideAsync()
    }

    fun onMusicWallpaperCleared() {
        if (!overrideActive && controller == null) return
        clearOverride()
        logI("override cleared")
    }

    fun clearOverride() {
        overrideActive = false
        title = ""
        content = ""
        artist = ""
        val ctrl = controller ?: return
        mainHandler.post {
            try {
                // 触发一次系统查询刷新（若仍是杂志壁纸则恢复系统数据；否则 PreView 自行隐藏）
                updateInfoMethod?.invoke(ctrl)
            } catch (_: Throwable) {
            }
        }
    }

    /** 在 updateLockScreenMagazineWallpaperInfo 前后写入覆盖字段。 */
    fun applyOverrideToController(ctrl: Any?) {
        val c = ctrl ?: controller ?: return
        if (!overrideActive) return
        try {
            attachController(c)
            val infoField = wallpaperInfoField ?: return
            var info = infoField.get(c)
            if (info == null) {
                val infoClass = Class.forName(
                    "com.android.keyguard.magazine.entity.LockScreenMagazineWallpaperInfo",
                    false,
                    c.javaClass.classLoader,
                )
                info = infoClass.getDeclaredConstructor().newInstance()
                infoField.set(c, info)
            }
            setField(info, "title", title)
            setField(info, "content", content)
            setField(info, "packageName", "com.leowalk.musiclockscreen")
            setField(info, "authority", "com.leowalk.musiclockscreen.magazine")
            setField(
                info,
                "ex",
                MagazineModePolicy.buildMagazineExJson(
                    entryText = "音乐",
                    source = artist,
                ),
            )
            try {
                val initExtra = info.javaClass.getDeclaredMethod("initExtra")
                initExtra.isAccessible = true
                initExtra.invoke(info)
            } catch (_: Throwable) {
            }
            if (MagazineModePolicy.shouldSuppressMagazineLeftSwipe(true, true)) {
                supportLeftField?.setBoolean(c, false)
            }
        } catch (e: Throwable) {
            logE("applyOverrideToController failed", e)
        }
    }

    private fun applyOverrideAsync() {
        val ctrl = controller ?: return
        mainHandler.post {
            try {
                applyOverrideToController(ctrl)
                updateInfoMethod?.invoke(ctrl)
            } catch (e: Throwable) {
                logE("applyOverrideAsync failed", e)
            }
        }
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            module?.log(android.util.Log.ERROR, TAG, msg, e)
        } else {
            module?.log(android.util.Log.ERROR, TAG, msg)
        }
    }
}

/**
 * Hook SystemUI 杂志层：强制 gallery 判定 + 注入 WallpaperInfo + 抑制左滑。
 */
object MagazineHostHook {

    private const val TAG = "HyperLockMusic_MagazineHook"

    fun install(classLoader: ClassLoader, module: XposedModule) {
        MagazineHost.bindModule(module)
        hookIsMagazineWallpaper(classLoader, module)
        hookMagazineController(classLoader, module)
        module.log(android.util.Log.INFO, TAG, "MagazineHostHook installed")
    }

    private fun hookIsMagazineWallpaper(classLoader: ClassLoader, module: XposedModule) {
        try {
            val wpClass = Class.forName(
                "com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager",
                false,
                classLoader,
            )
            val method = wpClass.declaredMethods.firstOrNull {
                it.name == "isMagazineWallpaper" && it.parameterTypes.isEmpty()
            } ?: run {
                module.log(android.util.Log.ERROR, TAG, "isMagazineWallpaper not found")
                return
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val original = chain.proceed() as? Boolean ?: false
                try {
                    val ctx = resolveContext(chain.thisObject)
                    if (MagazineHost.shouldForceMagazine(ctx)) {
                        true
                    } else {
                        original
                    }
                } catch (_: Throwable) {
                    original
                }
            }
            module.log(android.util.Log.INFO, TAG, "hooked isMagazineWallpaper")
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookIsMagazineWallpaper failed", e)
        }
    }

    private fun hookMagazineController(classLoader: ClassLoader, module: XposedModule) {
        try {
            val ctrlClass = Class.forName(
                "com.android.keyguard.magazine.LockScreenMagazineController",
                false,
                classLoader,
            )
            val updateMethod = ctrlClass.declaredMethods.firstOrNull {
                it.name == "updateLockScreenMagazineWallpaperInfo" && it.parameterTypes.isEmpty()
            }
            if (updateMethod != null) {
                updateMethod.isAccessible = true
                module.hook(updateMethod).intercept { chain ->
                    MagazineHost.attachController(chain.thisObject)
                    MagazineHost.applyOverrideToController(chain.thisObject)
                    val result = chain.proceed()
                    MagazineHost.applyOverrideToController(chain.thisObject)
                    result
                }
                module.log(android.util.Log.INFO, TAG, "hooked updateLockScreenMagazineWallpaperInfo")
            } else {
                module.log(android.util.Log.ERROR, TAG, "updateLockScreenMagazineWallpaperInfo not found")
            }

            // 构造后缓存 controller
            ctrlClass.declaredConstructors.forEach { ctor ->
                try {
                    ctor.isAccessible = true
                    module.hook(ctor).intercept { chain ->
                        val created = chain.proceed()
                        try {
                            MagazineHost.attachController(chain.thisObject)
                        } catch (_: Throwable) {
                        }
                        created
                    }
                } catch (_: Throwable) {
                }
            }
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookMagazineController failed", e)
        }
    }

    private fun resolveContext(wpManager: Any): Context? {
        return try {
            val f = wpManager.javaClass.getDeclaredField("mContext")
            f.isAccessible = true
            f.get(wpManager) as? Context
        } catch (_: Throwable) {
            null
        }
    }
}
