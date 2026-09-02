package com.leowalk.musiclockscreen.xposed

import android.view.View
import android.view.ViewParent
import android.widget.ImageButton
import android.widget.ImageView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * 锁屏 [MiuiMediaHeaderView] 媒体控件：
 * - 锁屏：action0 / action4 显示模块注入按钮，屏蔽应用 custom0 / custom1
 * - 解锁：action0 / action4 显示空占位（保留布局，无图标、不可点）
 */
object MediaKeyguardButtonHook {

    private const val TAG = "HyperLockMusic_MediaBtn"

    private val tagAction0Bound = 0x7f000002
    private val tagAction4Bound = 0x7f000004

    private var module: XposedModule? = null
    private var action0ResId = 0
    private var action4ResId = 0

    private var lastAction0: WeakReference<ImageButton>? = null
    private var lastAction4: WeakReference<ImageButton>? = null
    private var lastAlbumImageView: WeakReference<ImageView>? = null
    private var lastMediaPackage: String? = null

    /** 与 StatusBarState KEYGUARD 对齐，避免解锁瞬间 KeyguardManager 仍返回 locked */
    private var keyguardUiActive = true

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module

        try {
            val vcClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                false,
                classLoader
            )
            val mediaDataClass = Class.forName(
                "com.android.systemui.media.controls.shared.model.MediaData",
                false,
                classLoader
            )
            val mediaActionClass = Class.forName(
                "com.android.systemui.media.controls.shared.model.MediaAction",
                false,
                classLoader
            )

            val viewHolderClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
                false,
                classLoader
            )
            val playerField = viewHolderClass.getDeclaredField("player").apply { isAccessible = true }
            val action0Field = viewHolderClass.getDeclaredField("action0").apply { isAccessible = true }
            val action4Field = viewHolderClass.getDeclaredField("action4").apply { isAccessible = true }
            val albumImageViewField = viewHolderClass.getDeclaredField("albumImageView").apply {
                isAccessible = true
            }
            val holderField = vcClass.getDeclaredField("holder").apply { isAccessible = true }

            val bindMediaData = vcClass.getDeclaredMethod("bindMediaData", mediaDataClass)
            module.hook(bindMediaData).intercept { chain ->
                val result = chain.proceed()
                try {
                    val mediaData = chain.args.getOrNull(0)
                    lastMediaPackage = HookUtils.packageFromMediaData(mediaData)

                    val holder = holderField.get(chain.thisObject)
                    if (holder == null) return@intercept result

                    val player = playerField.get(holder) as? View
                    val ctx = player?.context
                    if (ctx == null || !isMiuiMediaHeaderPlayer(player)) return@intercept result

                    // 白名单开启且当前媒体不在白名单：若音乐锁屏已开则退出
                    if (WallpaperController.isShowing() &&
                        !HookUtils.isAllowedMusicApp(ctx, lastMediaPackage)
                    ) {
                        logI("media package not in whitelist: $lastMediaPackage, exiting")
                        WallpaperController.restoreOriginalWallpaper(ctx.applicationContext)
                    }

                    resolveResourceIds(ctx)
                    val action0 = action0Field.get(holder) as? ImageButton
                    val action4 = action4Field.get(holder) as? ImageButton
                    val albumImageView = albumImageViewField.get(holder) as? ImageView

                    lastAction0 = action0?.let { WeakReference(it) }
                    lastAction4 = action4?.let { WeakReference(it) }
                    lastAlbumImageView = albumImageView?.let { WeakReference(it) }

                    applyHeaderSlotState(action0, action4, albumImageView, ctx)
                } catch (e: Throwable) {
                    logE("after bindMediaData error", e)
                }
                result
            }

            val bindButtonCommon = vcClass.getDeclaredMethod("bindButtonCommon", ImageButton::class.java, mediaActionClass)
            module.hook(bindButtonCommon).intercept { chain ->
                val button = chain.args[0] as ImageButton
                if (shouldBlockAppCustomSlot(button)) {
                    return@intercept null
                }
                chain.proceed()
            }

            val utilsClass = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaActionButtonUtils",
                false,
                classLoader
            )
            val setSemanticButton = utilsClass.getDeclaredMethod(
                "setSemanticButton",
                ImageButton::class.java,
                mediaActionClass
            )
            module.hook(setSemanticButton).intercept { chain ->
                val button = chain.args[0] as ImageButton
                if (shouldBlockAppCustomSlot(button)) {
                    return@intercept null
                }
                chain.proceed()
            }

            logI("MediaKeyguardButtonHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    /** 解锁 / 回到锁屏时刷新槽位（bindMediaData 不一定会再次触发） */
    fun refreshSlots(onKeyguard: Boolean? = null) {
        if (onKeyguard != null) {
            keyguardUiActive = onKeyguard
        }
        val action0 = lastAction0?.get()
        val action4 = lastAction4?.get()
        val album = lastAlbumImageView?.get()
        val ctx = action0?.context ?: action4?.context
        if (ctx == null) return
        applyHeaderSlotState(action0, action4, album, ctx)
    }

    private fun shouldShowOurSlots(context: android.content.Context): Boolean {
        return keyguardUiActive && HookUtils.isOnKeyguard(context)
    }

    private fun applyHeaderSlotState(
        action0: ImageButton?,
        action4: ImageButton?,
        albumImageView: ImageView?,
        context: android.content.Context
    ) {
        val pkg = lastMediaPackage ?: HookUtils.currentMediaPackage(context)
        val allowed = HookUtils.isAllowedMusicApp(context, pkg)
        if (shouldShowOurSlots(context) && allowed) {
            if (action0 != null) {
                bindMusicLockscreenButton(action0, albumImageView, context)
            }
            if (action4 != null) {
                bindLyricToggleButton(action4, context)
            }
        } else {
            if (action0 != null) {
                applyEmptyPlaceholder(action0, tagAction0Bound)
            }
            if (action4 != null) {
                applyEmptyPlaceholder(action4, tagAction4Bound)
            }
        }
    }

    private fun shouldBlockAppCustomSlot(button: ImageButton): Boolean {
        resolveResourceIds(button.context)
        if (!isCustomSlot(button)) return false
        return isUnderMiuiMediaHeaderView(button)
    }

    /**
     * 与系统 setSemanticButton(null) 一致：占位但不显示内容，避免通知中心媒体控件布局错乱。
     */
    private fun applyEmptyPlaceholder(button: ImageButton, ourTag: Int) {
        button.setTag(ourTag, null)
        button.setOnClickListener(null)
        button.visibility = View.VISIBLE
        button.isEnabled = false
        button.isSelected = false
        button.setImageDrawable(null)
        button.contentDescription = null
        button.alpha = 1f
        button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun bindMusicLockscreenButton(
        button: ImageButton,
        albumImageView: ImageView?,
        context: android.content.Context
    ) {
        button.visibility = View.VISIBLE
        if (button.getTag(tagAction0Bound) == null) {
            button.setTag(tagAction0Bound, true)
            button.setOnClickListener {
                val ctx = it.context
                if (!HookUtils.isOnKeyguard(ctx) || !shouldShowOurSlots(ctx)) return@setOnClickListener
                if (!HookUtils.isAllowedMusicApp(ctx, lastMediaPackage ?: HookUtils.currentMediaPackage(ctx))) {
                    logI("action0 blocked: media not in whitelist")
                    return@setOnClickListener
                }
                val drawable = albumImageView?.drawable
                WallpaperController.toggle(ctx, drawable)
                updateMusicLockscreenButtonVisual(button, ctx)
                logI("action0 toggled music lockscreen, showing=${WallpaperController.isShowing()}")
            }
        }
        updateMusicLockscreenButtonVisual(button, context)
    }

    private fun updateMusicLockscreenButtonVisual(button: ImageButton, context: android.content.Context) {
        val icon = ModuleResources.drawable(context, "ic_btn_music_lockscreen")
        if (icon != null) {
            button.setImageDrawable(icon)
        }
        val active = WallpaperController.isShowing()
        button.isEnabled = true
        button.isSelected = active
        button.alpha = if (active) 1f else 0.45f
        button.contentDescription = if (active) {
            "关闭音乐锁屏"
        } else {
            "开启音乐锁屏"
        }
        button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun bindLyricToggleButton(button: ImageButton, context: android.content.Context) {
        button.visibility = View.VISIBLE
        if (button.getTag(tagAction4Bound) == null) {
            button.setTag(tagAction4Bound, true)
            button.setOnClickListener {
                val ctx = it.context
                if (!HookUtils.isOnKeyguard(ctx) || !shouldShowOurSlots(ctx)) return@setOnClickListener
                if (!ConfigReader.lyricEnabled(ctx)) {
                    logI("action4 blocked: lyric feature disabled")
                    return@setOnClickListener
                }
                val newValue = !ConfigReader.showLyric(ctx)
                if (ConfigReader.setShowLyric(ctx, newValue)) {
                    MusicLockscreenManager.lyricView?.refreshVisibility()
                    MusicLockscreenManager.showAlbumOverlay()
                    MediaFollowController.requestReflow()
                    updateLyricToggleButtonVisual(button, ctx)
                    logI("action4 toggled show lyric=$newValue")
                } else {
                    logE("action4 failed to update show_lyric")
                }
            }
        }
        updateLyricToggleButtonVisual(button, context)
    }

    private fun updateLyricToggleButtonVisual(button: ImageButton, context: android.content.Context) {
        val icon = ModuleResources.drawable(context, "ic_btn_show_lyric")
        if (icon != null) {
            button.setImageDrawable(icon)
        }
        val featureOn = ConfigReader.lyricEnabled(context)
        val active = featureOn && ConfigReader.showLyric(context)
        button.isEnabled = featureOn
        button.isSelected = active
        button.alpha = when {
            !featureOn -> 0.28f
            active -> 1f
            else -> 0.45f
        }
        button.contentDescription = when {
            !featureOn -> "歌词功能已关闭"
            active -> "隐藏歌词"
            else -> "显示歌词"
        }
        button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun resolveResourceIds(context: android.content.Context) {
        if (action0ResId != 0) return
        val res = context.resources
        action0ResId = res.getIdentifier("action0", "id", "com.android.systemui")
        action4ResId = res.getIdentifier("action4", "id", "com.android.systemui")
    }

    private fun isCustomSlot(button: ImageButton): Boolean {
        val id = button.id
        return id != 0 && (id == action0ResId || id == action4ResId)
    }

    private fun isUnderMiuiMediaHeaderView(view: View): Boolean {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            if (parent.javaClass.name.endsWith("MiuiMediaHeaderView")) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun isMiuiMediaHeaderPlayer(player: View): Boolean {
        var parent: ViewParent? = player.parent
        while (parent != null) {
            if (parent.javaClass.name.endsWith("MiuiMediaHeaderView")) {
                return true
            }
            parent = parent.parent
        }
        return false
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
