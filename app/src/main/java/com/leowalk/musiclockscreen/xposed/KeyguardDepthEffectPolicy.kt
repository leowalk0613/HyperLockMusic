package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.provider.Settings
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import java.lang.ref.WeakReference

/**
 * 音乐锁屏开启时临时停用 HyperOS 官方壁纸景深（抠图层 / 视频景深），避免盖住音乐壁纸。
 *
 * 原则：只隐藏，不销毁 drawable / VideoDepthSurface；退出后按 suppress 前现场状态恢复。
 * 原壁纸 setBitmap 完成后再 release，避免过早 parse 失败。
 */
internal object KeyguardDepthEffectPolicy {

    private const val TAG = "HyperLockMusic_Depth"
    private const val SETTING_WALLPAPER_EFFECT_TYPE_2 = "wallpaper_effect_type_2"
    private const val DEPTH_EFFECT_TYPE = 2

    @Volatile
    private var suppressed = false

    /** suppress 前是否应显示景深（字段 / 可见性 / 设置）。 */
    @Volatile
    private var savedDepthEnable: Boolean? = null

    /**
     * 音乐锁屏已关、原壁纸尚未 setBitmap 完成：继续压制，避免 restore 过早。
     */
    @Volatile
    private var holdUntilWallpaperRestored = false

    @Volatile
    private var panelRef: WeakReference<Any>? = null

    /** 防止 updateShowDepthState hook → reapply → 再调 updateShowDepthState 递归。 */
    @Volatile
    private var applying = false

    var logCallback: ((Int, String, String, Throwable?) -> Unit)? = null

    fun bindPanel(panelVc: Any) {
        panelRef = WeakReference(panelVc)
        if (shouldSuppress()) {
            suppress()
        }
    }

    fun isMusicLockscreenActive(): Boolean {
        return WallpaperController.isShowing() || MusicLockscreenManager.isShowing
    }

    /** 纯决策：音乐锁屏激活时压制景深。 */
    fun shouldSuppressDepth(musicLockscreenActive: Boolean): Boolean = musicLockscreenActive

    fun shouldSuppress(): Boolean {
        if (holdUntilWallpaperRestored) return true
        return shouldSuppressDepth(isMusicLockscreenActive())
    }

    fun isSuppressed(): Boolean = suppressed

    fun isHoldingUntilWallpaperRestored(): Boolean = holdUntilWallpaperRestored

    /** 与音乐锁屏状态对齐：开启则 suppress；关闭时若未 hold 则 release。 */
    fun syncWithMusicLockscreen() {
        if (shouldSuppressDepth(isMusicLockscreenActive())) {
            holdUntilWallpaperRestored = false
            suppress()
        } else if (!holdUntilWallpaperRestored) {
            release()
        }
    }

    /**
     * 关闭音乐锁屏、即将异步恢复原壁纸：先继续压制，等 [onOriginalWallpaperRestored]。
     */
    fun beginRestoreHold() {
        holdUntilWallpaperRestored = true
        suppressed = true
        applySuppressToPanel()
        logI("depth restore hold")
    }

    /** 原锁屏壁纸 setBitmap 已提交：解除 hold 并恢复景深。 */
    fun onOriginalWallpaperRestored() {
        holdUntilWallpaperRestored = false
        release()
    }

    fun suppress() {
        val panel = panelRef?.get()
        if (!suppressed && panel != null) {
            savedDepthEnable = captureDepthWasEnabled(panel)
            logI("savedDepthEnable=$savedDepthEnable")
        }
        suppressed = true
        applySuppressToPanel()
        logI("depth suppressed")
    }

    fun release() {
        if (!suppressed && panelRef?.get() == null) {
            holdUntilWallpaperRestored = false
            return
        }
        suppressed = false
        holdUntilWallpaperRestored = false
        restorePanelDepthFromSettings()
        logI("depth released savedWas=${savedDepthEnable}")
        savedDepthEnable = null
    }

    /** Hook 回弹时再刷一遍隐藏。 */
    fun reapplyIfSuppressed() {
        if (!shouldSuppress()) return
        suppressed = true
        applySuppressToPanel()
    }

    fun reset() {
        suppressed = false
        savedDepthEnable = null
        holdUntilWallpaperRestored = false
        applying = false
        panelRef = null
    }

    internal fun setSavedDepthEnableForTest(value: Boolean?) {
        savedDepthEnable = value
    }

    internal fun savedDepthEnableForTest(): Boolean? = savedDepthEnable

    /** 纯函数：是否应视为「曾经开着景深」以便恢复。 */
    fun shouldRestoreDepth(
        panelDepthEnable: Boolean,
        interactorDepthEnable: Boolean,
        actualDisplayDepth: Boolean,
        depthVideoEnable: Boolean,
        deductedVisibleWithDrawable: Boolean,
        settingsDepthType: Int,
    ): Boolean {
        return panelDepthEnable ||
            interactorDepthEnable ||
            actualDisplayDepth ||
            depthVideoEnable ||
            deductedVisibleWithDrawable ||
            isDepthEffectType(settingsDepthType)
    }

    private fun captureDepthWasEnabled(panel: Any): Boolean {
        val interactor = getField(panel, "keyguardDepthInteractor")
        val image = interactor?.let { getField(it, "deductedImageView") as? ImageView }
        val deductedVisibleWithDrawable =
            image != null &&
                image.visibility == View.VISIBLE &&
                image.drawable != null
        return shouldRestoreDepth(
            panelDepthEnable = getBooleanField(panel, "depthEffectEnable") == true,
            interactorDepthEnable = interactor?.let {
                getBooleanField(it, "depthEffectEnableInner")
            } == true,
            actualDisplayDepth = interactor?.let {
                getBooleanField(it, "isActualDisplayDepth")
            } == true,
            depthVideoEnable = interactor?.let {
                getBooleanField(it, "depthVideoEnable")
            } == true,
            deductedVisibleWithDrawable = deductedVisibleWithDrawable,
            settingsDepthType = readDepthEffectType(panel),
        )
    }

    private fun applySuppressToPanel() {
        if (applying) return
        val panel = panelRef?.get() ?: return
        applying = true
        try {
            setBooleanField(panel, "depthEffectEnable", false)
            val interactor = getField(panel, "keyguardDepthInteractor") ?: return
            setBooleanField(interactor, "depthEffectEnableInner", false)
            setBooleanField(interactor, "isActualDisplayDepth", false)

            // 只隐藏，不 clear drawable / 不 removeVideoDepthSurface，否则无法恢复
            hideDeductedImage(interactor, clearDrawable = false)
            hideForegroundLayer(panel, interactor)
            hideVideoDepth(interactor)
            forceDepthAlpha(interactor, 0f)

            invokeNoArg(panel, "updateShowDepthState")
            invokeNoArg(panel, "updateKeyguardElementsVisibility")
            hideDeductedImage(interactor, clearDrawable = false)
            hideForegroundLayer(panel, interactor)
            hideVideoDepth(interactor)
        } catch (e: Throwable) {
            logE("applySuppressToPanel error", e)
        } finally {
            applying = false
        }
    }

    private fun restorePanelDepthFromSettings() {
        if (applying) return
        val panel = panelRef?.get() ?: return
        applying = true
        try {
            val restore = savedDepthEnable
                ?: (captureDepthWasEnabled(panel) || readDepthEffectSetting(panel))
            logI("restore depth enable=$restore (saved=$savedDepthEnable)")

            setBooleanField(panel, "depthEffectEnable", restore)
            val interactor = getField(panel, "keyguardDepthInteractor")
            if (interactor != null) {
                setBooleanField(interactor, "depthEffectEnableInner", restore)
                if (restore) {
                    setBooleanField(interactor, "isActualDisplayDepth", true)
                    showDeductedImage(interactor)
                    showForegroundLayer(panel, interactor)
                    showVideoDepth(interactor)
                    forceDepthAlpha(interactor, 1f)
                    invokeNoArg(interactor, "updateDeductedImageView")
                    invokeNoArg(interactor, "updateVideoDepthSurface")
                } else {
                    hideDeductedImage(interactor, clearDrawable = false)
                    hideVideoDepth(interactor)
                }
            }
            invokeNoArg(panel, "updateShowDepthState")
            invokeNoArg(panel, "updateKeyguardElementsVisibility")
            if (restore && interactor != null) {
                // updateShowDepthState 可能按层级信息再次关掉，强制再亮一次
                setBooleanField(interactor, "isActualDisplayDepth", true)
                showDeductedImage(interactor)
                showForegroundLayer(panel, interactor)
                showVideoDepth(interactor)
                forceDepthAlpha(interactor, 1f)
                invokeNoArg(panel, "updateKeyguardElementsVisibility")
            }
        } catch (e: Throwable) {
            logE("restorePanelDepthFromSettings error", e)
        } finally {
            applying = false
        }
    }

    fun readDepthEffectSetting(panel: Any): Boolean {
        return isDepthEffectType(readDepthEffectType(panel))
    }

    private fun readDepthEffectType(panel: Any): Int {
        try {
            val ctx = getField(panel, "context") as? Context
            if (ctx != null) {
                return Settings.Secure.getInt(
                    ctx.contentResolver,
                    SETTING_WALLPAPER_EFFECT_TYPE_2,
                    0
                )
            }
        } catch (_: Throwable) {
        }
        return 0
    }

    fun isDepthEffectType(effectType: Int): Boolean = effectType == DEPTH_EFFECT_TYPE

    private fun forceDepthAlpha(interactor: Any, alpha: Float) {
        try {
            val animConfigClass = Class.forName("miuix.animation.base.AnimConfig")
            tryInvoke(
                interactor,
                "setDepthTransitionAlpha",
                arrayOf(
                    Float::class.javaPrimitiveType!!,
                    animConfigClass,
                    Boolean::class.javaPrimitiveType!!
                ),
                arrayOf(alpha, null, false)
            )
        } catch (_: Throwable) {
        }
        val folme = getField(interactor, "deductedTranslateAlphaFolmeAnimator")
        if (folme != null) {
            try {
                val viewProperty = Class.forName("miuix.animation.property.ViewProperty")
                val prop = viewProperty.getField("TRANSITION_ALPHA").get(null)
                folme.javaClass.getMethod("setTo", Any::class.java, Any::class.java)
                    .invoke(folme, prop, alpha)
            } catch (_: Throwable) {
            }
        }
    }

    private fun hideDeductedImage(interactor: Any, clearDrawable: Boolean) {
        val image = getField(interactor, "deductedImageView") as? ImageView ?: return
        image.visibility = View.INVISIBLE
        image.alpha = 0f
        if (clearDrawable) {
            try {
                image.setImageDrawable(null)
            } catch (_: Throwable) {
            }
        }
    }

    private fun showDeductedImage(interactor: Any) {
        val image = getField(interactor, "deductedImageView") as? ImageView ?: return
        image.visibility = View.VISIBLE
        image.alpha = 1f
    }

    private fun hideForegroundLayer(panel: Any, interactor: Any) {
        val layer = (getField(panel, "keyguardForegroundLayer") as? ViewGroup)
            ?: (getField(interactor, "keyguardForegroundLayer") as? ViewGroup)
            ?: return
        layer.visibility = View.INVISIBLE
    }

    private fun showForegroundLayer(panel: Any, interactor: Any) {
        val layer = (getField(panel, "keyguardForegroundLayer") as? ViewGroup)
            ?: (getField(interactor, "keyguardForegroundLayer") as? ViewGroup)
            ?: return
        layer.visibility = View.VISIBLE
    }

    private fun hideVideoDepth(interactor: Any) {
        val holder = getField(interactor, "videoDepthSurfaceHolder") ?: return
        try {
            (getField(holder, "backgroundTextureView") as? TextureView)?.visibility = View.INVISIBLE
            (getField(holder, "foregroundTextureView") as? TextureView)?.visibility = View.INVISIBLE
        } catch (_: Throwable) {
        }
    }

    private fun showVideoDepth(interactor: Any) {
        val holder = getField(interactor, "videoDepthSurfaceHolder") ?: return
        try {
            (getField(holder, "backgroundTextureView") as? TextureView)?.visibility = View.VISIBLE
            (getField(holder, "foregroundTextureView") as? TextureView)?.visibility = View.VISIBLE
        } catch (_: Throwable) {
        }
    }

    private fun getField(target: Any, name: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    private fun setBooleanField(target: Any, name: String, value: Boolean) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                f.setBoolean(target, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }

    private fun getBooleanField(target: Any, name: String): Boolean? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                return f.getBoolean(target)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun invokeNoArg(target: Any, name: String) {
        try {
            val m = target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterCount == 0
            } ?: target.javaClass.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == 0
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(target)
            }
        } catch (e: Throwable) {
            logE("invoke $name error", e)
        }
    }

    private fun tryInvoke(target: Any, name: String, types: Array<Class<*>>, args: Array<Any?>) {
        val m = target.javaClass.getDeclaredMethod(name, *types)
        m.isAccessible = true
        m.invoke(target, *args)
    }

    private fun logI(msg: String) {
        try {
            android.util.Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        logCallback?.invoke(android.util.Log.INFO, TAG, msg, null)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        try {
            android.util.Log.e(TAG, msg, e)
        } catch (_: Throwable) {
        }
        logCallback?.invoke(android.util.Log.ERROR, TAG, msg, e)
    }
}
