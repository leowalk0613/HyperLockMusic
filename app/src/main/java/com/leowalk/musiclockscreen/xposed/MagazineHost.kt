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
 * 画报宿主：劫持左滑/右划到模块 Activity；不强制 isMagazineWallpaper（避免 AOD 变自定义）。
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

    @Volatile
    private var magazineHelper: Any? = null

    @Volatile
    private var keyguardViewMediator: Any? = null

    /** 本轮已拉起模块画报页；仅此时灭屏需要清 occlude / 护住 FullAOD。 */
    @Volatile
    private var magazinePageLaunched: Boolean = false

    /** 灭屏保护窗：拦住 changeAodStyleShown，并允许补发 full_aod=1。 */
    @Volatile
    private var protectFullAodForMagazineSleep: Boolean = false

    @Volatile
    private var sleepBroadcastRegistered: Boolean = false

    private val magazineSleepReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action != MagazinePageSleepPolicy.ACTION_MAGAZINE_PAGE_GOING_TO_SLEEP) return
            logI("magazine page going to sleep broadcast")
            releaseOccludeForSystemAod(restoreFullAod = true)
        }
    }

    fun bindModule(module: XposedModule) {
        this.module = module
    }

    fun attachMagazineHelper(helper: Any) {
        magazineHelper = helper
        try {
            val f = helper.javaClass.getDeclaredField("mKeyguardViewMediator")
            f.isAccessible = true
            keyguardViewMediator = f.get(helper)
        } catch (_: Throwable) {
        }
    }

    fun markMagazinePageLaunched() {
        magazinePageLaunched = true
    }

    fun shouldBlockFullAodStyleChange(): Boolean {
        val fullAodOn = isFullScreenAodOn()
        return MagazinePageSleepPolicy.shouldBlockChangeAodStyleShown(
            magazinePageLaunched || protectFullAodForMagazineSleep,
            fullAodOn,
        )
    }

    private fun isFullScreenAodOn(): Boolean {
        val ctx = appContext ?: return false
        return try {
            android.provider.Settings.Secure.getInt(
                ctx.contentResolver,
                "full_screen_aod_on",
                0,
            ) == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun ensureSleepBroadcastRegistered(context: Context) {
        if (sleepBroadcastRegistered) return
        try {
            val filter = android.content.IntentFilter(
                MagazinePageSleepPolicy.ACTION_MAGAZINE_PAGE_GOING_TO_SLEEP,
            )
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(
                    magazineSleepReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(magazineSleepReceiver, filter)
            }
            sleepBroadcastRegistered = true
            logI("registered magazine sleep broadcast")
            ModeSwitchExit.ensureSystemUiReceiverRegistered(context.applicationContext)
        } catch (e: Throwable) {
            logE("register magazine sleep broadcast failed", e)
        }
    }

    /**
     * 画报页灭屏后清 occlude，交回与「锁屏直接灭屏」相同的系统 AOD。
     * 仅当确实拉起过模块页时执行，避免普通锁屏息屏被打扰。
     * 未开 FullAOD 时只清 occlude，不 block / 不 push full_aod=1。
     */
    fun releaseOccludeForSystemAod(restoreFullAod: Boolean = false) {
        if (!magazinePageLaunched && !protectFullAodForMagazineSleep) return
        val ctx = appContext
        if (ctx != null &&
            !MagazinePageSleepPolicy.shouldReleaseOccludeForLockscreenMatchedAod(
                ConfigReader.isMagazineChrome(ctx),
            )
        ) {
            magazinePageLaunched = false
            protectFullAodForMagazineSleep = false
            return
        }
        val didRelease = magazinePageLaunched || protectFullAodForMagazineSleep
        val fullAodOn = isFullScreenAodOn()
        if (fullAodOn) {
            protectFullAodForMagazineSleep = true
        } else {
            protectFullAodForMagazineSleep = false
        }
        magazinePageLaunched = false
        try {
            val mediator = keyguardViewMediator
            if (mediator != null) {
                val m = mediator.javaClass.methods.firstOrNull {
                    it.name == "setOccluded" && it.parameterTypes.size >= 1 &&
                        it.parameterTypes[0] == Boolean::class.javaPrimitiveType
                }
                when (m?.parameterTypes?.size) {
                    1 -> m.invoke(mediator, false)
                    2 -> m.invoke(mediator, false, false)
                    else -> {
                        val m2 = mediator.javaClass.getMethod("setOccluded", Boolean::class.javaPrimitiveType)
                        m2.invoke(mediator, false)
                    }
                }
                logI("releaseOccludeForSystemAod: setOccluded(false) fullAodOn=$fullAodOn")
            }
            val helper = magazineHelper
            if (helper != null) {
                try {
                    val finish = helper.javaClass.getDeclaredMethod("finishMagazineActivity")
                    finish.isAccessible = true
                    finish.invoke(helper)
                } catch (_: Throwable) {
                }
                try {
                    val playing = helper.javaClass.getDeclaredMethod("isOccludedAnimationPlaying")
                    playing.isAccessible = true
                    playing.invoke(helper)
                } catch (_: Throwable) {
                }
            }
            if (restoreFullAod &&
                MagazinePageSleepPolicy.shouldRestoreFullAodAfterMagazineSleep(didRelease, fullAodOn)
            ) {
                pushFullAodStyleEnabled(true)
            }
        } catch (e: Throwable) {
            logE("releaseOccludeForSystemAod failed", e)
        } finally {
            if (fullAodOn) {
                mainHandler.postDelayed({
                    protectFullAodForMagazineSleep = false
                }, 2500L)
            }
        }
    }

    /** 向 AOD 进程推送 full_aod state（1=锁屏样式一致，0=关掉 FullAOD）。 */
    fun pushFullAodStyleEnabled(enabled: Boolean) {
        try {
            val cl = appContext?.classLoader ?: return
            val mgr = Class.forName(
                "com.miui.systemui.interfacesmanager.InterfacesImplManager",
                false,
                cl,
            )
            val iface = Class.forName("com.miui.sysuiinterfaces.IDozeServiceHost", false, cl)
            val host = mgr.getMethod("getImpl", Class::class.java).invoke(null, iface) ?: return
            val field = host.javaClass.getDeclaredField("dozeServices").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val services = field.get(host) as? java.util.ArrayList<Any?> ?: return
            val bundle = android.os.Bundle()
            bundle.putString("action", "full_aod")
            bundle.putInt("state", if (enabled) 1 else 0)
            for (svc in services) {
                if (svc == null) continue
                try {
                    svc.javaClass.getMethod(
                        "onSystemUIAction",
                        Int::class.javaPrimitiveType,
                        android.os.Bundle::class.java,
                    ).invoke(svc, 64, bundle)
                } catch (_: Throwable) {
                }
            }
            logI("pushFullAodStyleEnabled($enabled)")
        } catch (e: Throwable) {
            logE("pushFullAodStyleEnabled failed", e)
        }
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
            resolveContext(ctrl)?.let {
                appContext = it.applicationContext
                ensureSleepBroadcastRegistered(it.applicationContext)
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

    fun shouldSuppressLeft(context: Context?): Boolean = false

    /** 是否把左滑/右划画报入口改到模块 Activity（需画报模式且有音乐）。 */
    fun shouldRedirectLeft(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext ?: return false
        return MagazineModePolicy.shouldRedirectMagazineLeftSwipe(
            chromeMagazine = ConfigReader.isMagazineChrome(ctx),
            musicActive = isMusicActiveForMagazineEntry(ctx),
        )
    }

    fun isMagazineChromeActive(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext ?: return false
        return ConfigReader.isMagazineChrome(ctx)
    }

    /** 有白名单可用媒体会话时，才允许进音乐画报页（不用 override 粘滞态）。 */
    private fun isMusicActiveForMagazineEntry(context: Context): Boolean {
        return try {
            com.leowalk.musiclockscreen.MediaSessionAccess.getActiveControllers(context).any { controller ->
                if (!ConfigReader.isAllowedMusicApp(context, controller.packageName)) {
                    return@any false
                }
                MagazineModePolicy.isUsableMusicPlaybackState(controller.playbackState?.state)
            }
        } catch (_: Throwable) {
            false
        }
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

    /** 画报模式：有音乐时强制左滑指向模块页；无音乐时清掉模块页目标并清覆盖，禁止误进黑页。 */
    fun ensureLeftSwipeCapable(ctrl: Any?) {
        val c = ctrl ?: controller ?: return
        if (!shouldRedirectLeft()) {
            try {
                if (overrideActive) clearOverride()
                val current = try {
                    c.javaClass.getDeclaredField("mPreLeftScreenActivityName").apply {
                        isAccessible = true
                    }.get(c) as? String
                } catch (_: Throwable) {
                    null
                }
                if (current == MagazineModePolicy.MAGAZINE_MUSIC_ACTIVITY ||
                    (current != null && current.contains("MagazineMusicActivity"))
                ) {
                    setField(c, "mPreLeftScreenActivityName", "")
                }
            } catch (e: Throwable) {
                logE("clear magazine left target failed", e)
            }
            return
        }
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
        // 锁屏侧不再写 gallery 覆盖；仅缓存文案供划入页 Intent extras
        if (!ConfigReader.isMagazineChrome(context)) {
            clearOverride()
            return
        }
        title = songTitle.ifBlank { "正在播放" }
        artist = songArtist
        content = lyricLine.ifBlank { songArtist }
        overrideActive = false
        logI("magazine page extras cached title=$title")
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

    /**
     * 锁屏侧不再注入 gallery WallpaperInfo（易把壁纸类型拖成画报，AOD 变自定义）。
     * 歌名等只在模块画报页展示。
     */
    fun applyOverrideToController(ctrl: Any?) {
        // no-op：保留调用点兼容
    }

    private fun applyOverrideAsync() {
        // no-op
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
        // 不 hook 强制 isMagazineWallpaper：gallery 判定会把息屏拖成自定义/万象 AOD
        hookMagazineController(classLoader, module)
        hookLeftSwipeLaunch(classLoader, module)
        hookMagazineRemoteAnimation(classLoader, module)
        hookReleaseOccludeOnSleep(classLoader, module)
        hookBlockFullAodStyleChange(classLoader, module)
        hookSpoofEmagInstalled(classLoader, module)
        module.log(android.util.Log.INFO, TAG, "MagazineHostHook installed")
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
                        MagazineHost.markMagazinePageLaunched()
                        MagazineHost.buildMusicLeftIntent()
                    } else if (MagazineHost.isMagazineChromeActive(ctx)) {
                        null
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
                    when {
                        MagazineHost.shouldRedirectLeft(ctx) && ctx != null -> {
                            try {
                                MagazineHost.markMagazinePageLaunched()
                                startActivityAsCurrentUser(ctx, MagazineHost.buildMusicLeftIntent())
                                module.log(android.util.Log.INFO, TAG, "startMagazineLeftActivity -> module activity")
                            } catch (e: Throwable) {
                                module.log(android.util.Log.ERROR, TAG, "start module magazine activity failed", e)
                            }
                            null
                        }
                        // 无音乐：吞掉启动，禁止 proceed 拉起残留模块页
                        MagazineHost.isMagazineChromeActive(ctx) -> null
                        else -> chain.proceed()
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
                        when {
                            MagazineHost.shouldRedirectLeft() -> true
                            // 画报模式无音乐：禁止左滑启动，避免黑页
                            MagazineHost.isMagazineChromeActive() -> false
                            else -> chain.proceed()
                        }
                    }
                    module.log(android.util.Log.INFO, TAG, "hooked $name")
                }
            }
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookLeftSwipeLaunch failed", e)
        }
    }

    /** 遮罩动画：仅放行官方 emag；模块页不参与，保证灭屏回普通锁屏 AOD。 */
    private fun hookMagazineRemoteAnimation(classLoader: ClassLoader, module: XposedModule) {
        try {
            val helperClass = Class.forName(
                "com.android.keyguard.magazine.KeyguardMagazineHelper",
                false,
                classLoader,
            )
            helperClass.declaredConstructors.forEach { ctor ->
                try {
                    ctor.isAccessible = true
                    module.hook(ctor).intercept { chain ->
                        val created = chain.proceed()
                        try {
                            MagazineHost.attachMagazineHelper(chain.thisObject)
                        } catch (_: Throwable) {
                        }
                        created
                    }
                } catch (_: Throwable) {
                }
            }
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

    /**
     * 灭屏清 occlude：Activity finish 广播抢先清；FinishedGoingToSleep 再兜底并恢复 FullAOD。
     */
    private fun hookReleaseOccludeOnSleep(classLoader: ClassLoader, module: XposedModule) {
        try {
            val injector = Class.forName(
                "com.android.keyguard.injector.KeyguardViewMediatorInjector",
                false,
                classLoader,
            )
            val finished = injector.declaredMethods.firstOrNull {
                it.name == "handleNotifyFinishedGoingToSleep" && it.parameterTypes.isEmpty()
            } ?: run {
                module.log(android.util.Log.INFO, TAG, "handleNotifyFinishedGoingToSleep not found")
                return
            }
            finished.isAccessible = true
            module.hook(finished).intercept { chain ->
                try {
                    MagazineHost.releaseOccludeForSystemAod(restoreFullAod = true)
                } catch (_: Throwable) {
                }
                chain.proceed()
            }
            module.log(android.util.Log.INFO, TAG, "hooked handleNotifyFinishedGoingToSleep for aod release")
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookReleaseOccludeOnSleep failed", e)
        }
    }

    /**
     * 画报页灭屏窗口内禁止 changeAodStyleShown：否则 occluded+doze 会把 FullAOD 关掉变自定义。
     */
    private fun hookBlockFullAodStyleChange(classLoader: ClassLoader, module: XposedModule) {
        try {
            val injector = Class.forName(
                "com.android.keyguard.injector.DozeServiceHostInjector",
                false,
                classLoader,
            )
            val method = injector.declaredMethods.firstOrNull {
                it.name == "changeAodStyleShown" && it.parameterTypes.isEmpty()
            } ?: run {
                module.log(android.util.Log.INFO, TAG, "changeAodStyleShown not found")
                return
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                if (MagazineHost.shouldBlockFullAodStyleChange()) {
                    module.log(android.util.Log.INFO, TAG, "blocked changeAodStyleShown (magazine sleep)")
                    null
                } else {
                    chain.proceed()
                }
            }
            module.log(android.util.Log.INFO, TAG, "hooked changeAodStyleShown for magazine FullAOD")
        } catch (e: Throwable) {
            module.log(android.util.Log.ERROR, TAG, "hookBlockFullAodStyleChange failed", e)
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
