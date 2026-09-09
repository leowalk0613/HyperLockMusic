package com.leowalk.musiclockscreen.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 画报基底宿主：在 SystemUI 杂志层注入歌名/文案，并强制 isMagazineWallpaper；
 * 左滑/右划入口改写到模块 [MagazineModePolicy.MAGAZINE_MUSIC_ACTIVITY]。
 */
object MagazineHost {

    private const val TAG = "HyperLockMusic_Magazine"

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var controller: Any? = null

    @Volatile
    private var appContext: Context? = null

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
            resolveContext(ctrl)?.let { appContext = it.applicationContext }
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

    fun shouldSuppressLeft(context: Context?): Boolean = false

    /** 是否把左滑/右划画报入口改到模块 Activity（与官方同手势，不依赖模块壁纸）。 */
    fun shouldRedirectLeft(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext ?: return false
        return MagazineModePolicy.shouldRedirectMagazineLeftSwipe(
            chromeMagazine = ConfigReader.isMagazineChrome(ctx),
        )
    }

    fun buildMusicLeftIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                MagazineModePolicy.MODULE_PACKAGE,
                MagazineModePolicy.MAGAZINE_MUSIC_ACTIVITY,
            )
            // 对齐官方：NEW_TASK | MULTIPLE_TASK (0x10800000)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra("from", "keyguard")
            putExtra("entry_source", "swipe")
            putExtra(MagazineModePolicy.EXTRA_SONG_TITLE, title)
            putExtra(MagazineModePolicy.EXTRA_SONG_ARTIST, artist)
            putExtra(MagazineModePolicy.EXTRA_LYRIC_LINE, content)
        }
    }

    /** 画报模式：强制左滑能力字段，避免卸载 emag 后 supportMoveToRight=false。 */
    fun ensureLeftSwipeCapable(ctrl: Any?) {
        if (!shouldRedirectLeft()) return
        val c = ctrl ?: controller ?: return
        try {
            supportLeftField?.setBoolean(c, true)
            setField(c, "mIsLockScreenMagazinePkgExist", true)
            setField(c, "mPreLeftScreenActivityName", MagazineModePolicy.MAGAZINE_MUSIC_ACTIVITY)
        } catch (e: Throwable) {
            logE("ensureLeftSwipeCapable failed", e)
        }
    }

    fun onMusicWallpaperShown(
        context: Context,
        songTitle: String,
        songArtist: String,
        lyricLine: String = "",
    ) {
        appContext = context.applicationContext
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
            // 锁屏皮仍用系统画报包名；真正划入由 Hook Intent 改到模块 Activity
            setField(info, "packageName", "com.mfashiongallery.emag")
            setField(
                info,
                "authority",
                "com.xiaomi.tv.gallerylockscreen.lockscreen_magazine_provider",
            )
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

    fun resolveContext(host: Any): Context? {
        return try {
            val f = host.javaClass.getDeclaredField("mContext")
            f.isAccessible = true
            f.get(host) as? Context
        } catch (_: Throwable) {
            null
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
 * Hook SystemUI 杂志层：强制 gallery 判定 + 注入 WallpaperInfo + 劫持左滑入口。
 */
object MagazineHostHook {

    private const val TAG = "HyperLockMusic_MagazineHook"

    fun install(classLoader: ClassLoader, module: XposedModule) {
        MagazineHost.bindModule(module)
        hookIsMagazineWallpaper(classLoader, module)
        hookMagazineController(classLoader, module)
        hookLeftSwipeLaunch(classLoader, module)
        hookMagazineRemoteAnimation(classLoader, module)
        hookSpoofEmagInstalled(classLoader, module)
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
                    val ctx = MagazineHost.resolveContext(chain.thisObject)
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
                    MagazineHost.ensureLeftSwipeCapable(chain.thisObject)
                    MagazineHost.applyOverrideToController(chain.thisObject)
                    val result = chain.proceed()
                    MagazineHost.ensureLeftSwipeCapable(chain.thisObject)
                    MagazineHost.applyOverrideToController(chain.thisObject)
                    result
                }
                module.log(android.util.Log.INFO, TAG, "hooked updateLockScreenMagazineWallpaperInfo")
            } else {
                module.log(android.util.Log.ERROR, TAG, "updateLockScreenMagazineWallpaperInfo not found")
            }

            // 左滑 Intent：绕过 emag 包名硬编码，指向模块 Activity
            val intentMethod = ctrlClass.declaredMethods.firstOrNull {
                it.name == "getPreLeftScreenIntent" && it.parameterTypes.isEmpty()
            }
            if (intentMethod != null) {
                intentMethod.isAccessible = true
                module.hook(intentMethod).intercept { chain ->
                    MagazineHost.attachController(chain.thisObject)
                    MagazineHost.ensureLeftSwipeCapable(chain.thisObject)
                    val ctx = MagazineHost.resolveContext(chain.thisObject)
                    if (MagazineHost.shouldRedirectLeft(ctx)) {
                        MagazineHost.buildMusicLeftIntent()
                    } else {
                        chain.proceed()
                    }
                }
                module.log(android.util.Log.INFO, TAG, "hooked getPreLeftScreenIntent")
            } else {
                module.log(android.util.Log.ERROR, TAG, "getPreLeftScreenIntent not found")
            }

            // 启动入口兜底：即便系统侧 supportLeft=false，仍可直接拉起我们的页
            val startMethod = ctrlClass.declaredMethods.firstOrNull {
                it.name == "startMagazineLeftActivity" && it.parameterTypes.isEmpty()
            }
            if (startMethod != null) {
                startMethod.isAccessible = true
                module.hook(startMethod).intercept { chain ->
                    MagazineHost.attachController(chain.thisObject)
                    MagazineHost.ensureLeftSwipeCapable(chain.thisObject)
                    val ctx = MagazineHost.resolveContext(chain.thisObject)
                    if (MagazineHost.shouldRedirectLeft(ctx) && ctx != null) {
                        try {
                            startActivityAsCurrentUser(ctx, MagazineHost.buildMusicLeftIntent())
                            module.log(android.util.Log.INFO, TAG, "startMagazineLeftActivity -> module activity")
                        } catch (e: Throwable) {
                            module.log(android.util.Log.ERROR, TAG, "start module magazine activity failed", e)
                        }
                        null
                    } else {
                        chain.proceed()
                    }
                }
                module.log(android.util.Log.INFO, TAG, "hooked startMagazineLeftActivity")
            }

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

    /** SystemUI 用 @hide startActivityAsUser；编译期不可见，运行期反射。 */
    private fun startActivityAsCurrentUser(context: Context, intent: Intent) {
        val userHandleClass = Class.forName("android.os.UserHandle")
        val current = userHandleClass.getField("CURRENT").get(null)
        try {
            val m = Context::class.java.getMethod(
                "startActivityAsUser",
                Intent::class.java,
                userHandleClass,
            )
            m.invoke(context, intent, current)
        } catch (_: NoSuchMethodException) {
            context.startActivity(intent)
        }
    }

    /**
     * 国内机常走 Overlay；画报模式强制 Activity 启动路径。
     * 卸载 emag 后 supportMoveToRight / isSupportSwipeToLaunchMagazine 本会失败，一并放宽。
     */
    private fun hookLeftSwipeLaunch(classLoader: ClassLoader, module: XposedModule) {
        try {
            val moveClass = Class.forName(
                "com.android.keyguard.negative.KeyguardMoveLeftController",
                false,
                classLoader,
            )
            listOf(
                "isLeftViewLaunchActivity",
                "isSupportSwipeToLaunchMagazine",
                "supportMoveToRight",
            ).forEach { name ->
                moveClass.declaredMethods.firstOrNull {
                    it.name == name && it.parameterTypes.isEmpty()
                }?.let { method ->
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        if (MagazineHost.shouldRedirectLeft()) {
                            true
                        } else {
                            chain.proceed()
                        }
                    }
                    module.log(android.util.Log.INFO, TAG, "hooked $name")
                }
            }
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookLeftSwipeLaunch failed", e)
        }
    }

    /** 遮罩动画：模块包也算 magazine，否则进我们的页没有官方 occlude 动画。 */
    private fun hookMagazineRemoteAnimation(classLoader: ClassLoader, module: XposedModule) {
        try {
            val helperClass = Class.forName(
                "com.android.keyguard.magazine.KeyguardMagazineHelper",
                false,
                classLoader,
            )
            val method = helperClass.declaredMethods.firstOrNull {
                it.name == "checkIsMagazineRemoteAnimation" && it.parameterTypes.size == 1
            } ?: run {
                module.log(android.util.Log.ERROR, TAG, "checkIsMagazineRemoteAnimation not found")
                return
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val original = chain.proceed() as? Boolean ?: false
                if (original) return@intercept true
                try {
                    if (!MagazineHost.shouldRedirectLeft()) return@intercept false
                    val target = chain.args.getOrNull(0) ?: return@intercept false
                    val taskInfo = target.javaClass.getField("taskInfo").get(target) ?: return@intercept false
                    val base = try {
                        taskInfo.javaClass.getField("baseActivity").get(taskInfo) as? ComponentName
                    } catch (_: Throwable) {
                        null
                    }
                    val real = try {
                        taskInfo.javaClass.getField("realActivity").get(taskInfo) as? ComponentName
                    } catch (_: Throwable) {
                        null
                    }
                    val pkg = base?.packageName ?: real?.packageName
                    MagazineModePolicy.isMagazineRemoteAnimationPackage(
                        packageName = pkg,
                        chromeMagazine = true,
                    )
                } catch (_: Throwable) {
                    false
                }
            }
            module.log(android.util.Log.INFO, TAG, "hooked checkIsMagazineRemoteAnimation")
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookMagazineRemoteAnimation failed", e)
        }
    }

    /** 卸载 emag 后伪装「已安装」，避免其它 SystemUI 门闩关掉右划。 */
    private fun hookSpoofEmagInstalled(classLoader: ClassLoader, module: XposedModule) {
        try {
            val pkgClass = Class.forName(
                "com.miui.utils.PackageUtils",
                false,
                classLoader,
            )
            val method = pkgClass.declaredMethods.firstOrNull {
                it.name == "isAppInstalledForUser" &&
                    it.parameterTypes.size >= 2 &&
                    it.parameterTypes[1] == String::class.java
            } ?: run {
                module.log(android.util.Log.ERROR, TAG, "isAppInstalledForUser not found")
                return
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val queried = chain.args.getOrNull(1) as? String
                if (MagazineModePolicy.shouldSpoofMagazinePackageInstalled(
                        chromeMagazine = MagazineHost.shouldRedirectLeft(),
                        queriedPackage = queried,
                    )
                ) {
                    true
                } else {
                    chain.proceed()
                }
            }
            module.log(android.util.Log.INFO, TAG, "hooked isAppInstalledForUser (emag spoof)")
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookSpoofEmagInstalled failed", e)
        }
    }
}
