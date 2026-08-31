package com.leowalk.musiclockscreen.xposed

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModule

/**
 * 锁屏视图 Hook
 *
 * 1. 在 keyguardBackgroundLayer 中添加歌词 overlay（小区域，shade 展开时隐藏）
 * 2. 壁纸渲染走 HyperOS which=2 管线，不在此层挂全屏 overlay
 * 3. 获取通知列表 / 时钟 / 勿扰状态 View
 */
class KeyguardOverlayHook {

    private val tag = "HyperLockMusic_Overlay"
    private var module: XposedModule? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        this.module = module

        // 统一设置所有控制器的日志回调
        setupLogCallbacks(module)

        try {
            logI("install start")

            val panelVcClass = Class.forName(
                "com.android.keyguard.panel.KeyguardPanelViewController",
                false,
                classLoader
            )

            val constraintLayoutClass = Class.forName(
                "androidx.constraintlayout.widget.ConstraintLayout",
                false,
                classLoader
            )

            val bindMethod = panelVcClass.getDeclaredMethod(
                "onKeyguardViewBind",
                constraintLayoutClass
            )

            module.hook(bindMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val thisObj = chain.thisObject
                    logI("onKeyguardViewBind called")

                    HyperOsWallpaperBridge.bindKeyguardPanel(thisObj)

                    // 重启且音乐壁纸残留时自动恢复原壁纸（先恢复，避免随后把残留当成干净源缓存）
                    val bindRoot = chain.args[0] as? ViewGroup
                    WallpaperController.autoRestoreIfResidual(bindRoot?.context)
                    // 干净状态下持续缓存当前锁屏原壁纸（跟随用户改壁纸）；
                    // 音乐壁纸激活/残留时会内部自动跳过。
                    WallpaperController.cacheOriginalWallpaperIfClean(bindRoot?.context)

                    // 获取 keyguardBackgroundLayer（仅挂歌词 overlay）
                    val bgLayerField = panelVcClass.getDeclaredField("keyguardBackgroundLayer")
                    bgLayerField.isAccessible = true
                    val bgLayer = bgLayerField.get(thisObj) as? ViewGroup

                    if (bgLayer != null) {
                        bgLayer.post {
                            // 子 View 顺序：遮罩(底) → 专辑 → 歌词(顶)；过渡时再把遮罩临时抬起
                            addTransitionMask(bgLayer)
                            addBigAlbumOverlay(bgLayer)
                            addMinimalClockOverlay(bgLayer)
                            addLyricOverlay(bgLayer)
                            MusicLockscreenManager.lyricView?.let { lyric ->
                                val d = lyric.resources.displayMetrics.density
                                lyric.elevation = 48f * d
                                lyric.translationZ = 48f * d
                                lyric.bringToFront()
                            }
                            LockscreenClockController.getMinimalClockView()?.bringToFront()
                            MediaFollowController.bindBackgroundLayer(bgLayer)
                            KeepScreenController.bindLayer(bgLayer)
                            LockscreenClockController.sync()
                        }
                    } else {
                        logE("keyguardBackgroundLayer is null")
                    }

                    // 获取通知列表 View
                    val view = chain.args[0] as? ViewGroup
                    if (view != null) {
                        view.post {
                            // 从根视图查找所有需要的 View
                            val rootView = view.rootView
                            if (rootView is ViewGroup) {
                                findNotificationView(rootView)
                                findClockView(rootView)
                                NumStateViewController.bindKeyguardRoot(rootView)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logE("after hook error", e)
                }
                result
            }

            logI("KeyguardOverlayHook installed")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    /**
     * 统一设置所有单例控制器的日志回调
     */
    private fun setupLogCallbacks(module: XposedModule) {
        val callback: (Int, String, String, Throwable?) -> Unit = { priority, tag, msg, e ->
            if (e != null) {
                module.log(priority, tag, msg, e)
            } else {
                module.log(priority, tag, msg)
            }
        }
        MusicLockscreenManager.logCallback = callback
        LockscreenNotificationController.logCallback = callback
        LockscreenClockController.logCallback = callback
        NumStateViewController.logCallback = callback
        WallpaperController.logCallback = callback
        HyperOsWallpaperBridge.logCallback = callback
        AlbumArtResolver.logCallback = callback
        NetEaseAlbumArtSource.logCallback = callback
        NetEaseSongIdResolver.logCallback = callback
        TransitionAnimator.logCallback = callback
        SystemNotificationAnimator.logCallback = callback
        MediaFollowController.logCallback = callback
    }

    private fun addBigAlbumOverlay(bgLayer: ViewGroup) {
        try {
            val existing = bgLayer.findViewWithTag<View>("music_big_album_overlay")
            if (existing is BigAlbumOverlayView) {
                MusicLockscreenManager.bigAlbumView = existing
                logI("big album overlay already exists")
                return
            }

            val overlay = BigAlbumOverlayView(bgLayer.context).apply {
                tag = "music_big_album_overlay"
                visibility = View.GONE
            }
            // 与歌词同一套：自身尺寸 + topMargin（底边距媒体上沿），由 MediaFollow 维护
            overlay.layoutParams = FrameLayout.LayoutParams(
                overlay.layoutWidthPx,
                overlay.layoutHeightPx
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                leftMargin = 0
                topMargin = 0
                rightMargin = 0
                bottomMargin = 0
            }
            bgLayer.addView(overlay)
            MusicLockscreenManager.bigAlbumView = overlay
            logI("big album overlay added")
        } catch (e: Throwable) {
            logE("addBigAlbumOverlay error", e)
        }
    }

    private fun addTransitionMask(bgLayer: ViewGroup) {
        try {
            val existing = bgLayer.findViewWithTag<View>("music_wallpaper_transition_mask")
            if (existing != null) {
                MusicLockscreenManager.transitionMaskView = existing
                return
            }
            val mask = View(bgLayer.context).apply {
                tag = "music_wallpaper_transition_mask"
                setBackgroundColor(Color.BLACK)
                alpha = 0f
                visibility = View.INVISIBLE
            }
            mask.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            bgLayer.addView(mask)
            MusicLockscreenManager.transitionMaskView = mask
            logI("transition mask added")
        } catch (e: Throwable) {
            logE("addTransitionMask error", e)
        }
    }

    private fun addMinimalClockOverlay(bgLayer: ViewGroup) {
        try {
            val existing = bgLayer.findViewWithTag<View>(MusicMinimalClockView.TAG_VIEW)
            if (existing is MusicMinimalClockView) {
                LockscreenClockController.setMinimalClockView(existing)
                logI("minimal clock overlay already exists")
                return
            }
            val overlay = MusicMinimalClockView(bgLayer.context).apply {
                tag = MusicMinimalClockView.TAG_VIEW
                visibility = View.GONE
            }
            overlay.layoutParams = overlay.attachLayoutParams()
            bgLayer.addView(overlay)
            LockscreenClockController.setMinimalClockView(overlay)
            logI("minimal clock overlay added at 20% top")
        } catch (e: Throwable) {
            logE("addMinimalClockOverlay error", e)
        }
    }

    private fun addLyricOverlay(bgLayer: ViewGroup) {
        try {
            val existing = bgLayer.findViewWithTag<View>("music_lyric_overlay")
            if (existing != null) {
                logI("lyric overlay already exists")
                MusicLockscreenManager.lyricView = existing as? LockscreenLyricView
                return
            }

            logI("Adding lyric overlay to keyguardBackgroundLayer")

            val overlay = LockscreenLyricView(bgLayer.context).apply {
                tag = "music_lyric_overlay"
                visibility = View.GONE
            }
            // 与专辑同一套：WRAP_CONTENT + topMargin，初始 0，由 MediaFollow 按底边对齐媒体上沿
            overlay.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                leftMargin = 0
                topMargin = 0
                rightMargin = 0
                bottomMargin = 0
            }

            bgLayer.addView(overlay)
            MusicLockscreenManager.lyricView = overlay
            logI("lyric overlay added successfully")
        } catch (e: Throwable) {
            logE("addLyricOverlay error", e)
        }
    }

    /**
     * 查找通知列表 View
     */
    private fun findNotificationView(root: ViewGroup) {
        try {
            val scrollerView = HookUtils.findViewByIdByName(root, "notification_stack_scroller")
            if (scrollerView is ViewGroup) {
                logI("Found notification_stack_scroller, childCount=${scrollerView.childCount}")
                LockscreenNotificationController.setNotificationStackView(scrollerView)
                return
            }
            logE("notification_stack_scroller not found")
        } catch (e: Throwable) {
            logE("findNotificationView error", e)
        }
    }

    /**
     * 查找时钟容器 View
     */
    private fun findClockView(root: ViewGroup) {
        try {
            val context = root.context
            val resources = context.resources

            // 按优先级查找
            val clockIds = listOf(
                "keyguard_clock_container",
                "clock_view",
                "keyguard_clock_view",
                "miui_keyguard_clock",
                "keyguard_clock",
                "clock_container",
                "lock_clock",
                "lockscreen_clock"
            )

            for (idName in clockIds) {
                val clockId = resources.getIdentifier(idName, "id", context.packageName)
                if (clockId != 0) {
                    val clockV = root.findViewById<View>(clockId)
                    if (clockV != null) {
                        logI("Found clock view: $idName (${clockV.javaClass.simpleName})")
                        logI("  parent: ${clockV.parent?.javaClass?.simpleName}")
                        logI("  grandparent: ${clockV.parent?.parent?.javaClass?.simpleName}")
                        LockscreenClockController.setClockView(clockV)
                        return
                    }
                }
            }

            // 没找到的话，打印所有包含 clock 的 id 辅助调试
            logI("clock container not found, listing all clock-related ids...")
            dumpClockIds(root)
        } catch (e: Throwable) {
            logE("findClockView error", e)
        }
    }

    private fun dumpClockIds(root: ViewGroup) {
        try {
            val context = root.context
            val rClass = Class.forName("${context.packageName}.R\$id")
            val fields = rClass.declaredFields
            var count = 0
            for (field in fields) {
                val name = field.name.lowercase()
                if (name.contains("clock") || name.contains("time") || name.contains("date")) {
                    try {
                        val id = field.getInt(null)
                        val view = root.findViewById<View>(id)
                        if (view != null) {
                            logI("  clock-related id: ${field.name} -> ${view.javaClass.simpleName}")
                            count++
                        }
                    } catch (_: Throwable) { }
                }
            }
            logI("Found $count clock-related views")
        } catch (e: Throwable) {
            logE("dumpClockIds error", e)
        }
    }

    private fun logI(msg: String) {
        module?.log(android.util.Log.INFO, tag, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        if (e != null) {
            module?.log(android.util.Log.ERROR, tag, msg, e)
        } else {
            module?.log(android.util.Log.ERROR, tag, msg)
        }
    }
}
