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
 * 依据反编译：
 * - `Settings.Secure wallpaper_effect_type_2 == 2` → depthEffectEnable
 * - [KeyguardDepthInteractor] `deductedImageView` / `isActualDisplayDepth`
 * - [KeyguardPanelViewController] `keyguardForegroundLayer` / `updateShowDepthState`
 * - 视频：`VideoDepthSurfaceHolder` + `isDepthVideoEnable`
 *
 * 不改写系统设置，仅在内存态压制显示；退出音乐锁屏后按 Secure 设置恢复。
 */
internal object KeyguardDepthEffectPolicy {

    private const val TAG = "HyperLockMusic_Depth"
    private const val SETTING_WALLPAPER_EFFECT_TYPE_2 = "wallpaper_effect_type_2"
    private const val DEPTH_EFFECT_TYPE = 2

    @Volatile
    private var suppressed = false

    /** suppress 前记下的景深开关，release 时优先用（避免 hook 干扰读设置）。 */
    @Volatile
    private var savedDepthEnable: Boolean? = null

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

    fun shouldSuppress(): Boolean = shouldSuppressDepth(isMusicLockscreenActive())

    fun isSuppressed(): Boolean = suppressed

    /** 与音乐锁屏状态对齐：开启则 suppress，关闭则 release。 */
    fun syncWithMusicLockscreen() {
        if (shouldSuppress()) suppress() else release()
    }

    fun suppress() {
        val panel = panelRef?.get()
        if (!suppressed && panel != null) {
            savedDepthEnable = readDepthEffectSetting(panel)
        }
        suppressed = true
        applySuppressToPanel()
        logI("depth suppressed")
    }

    fun release() {
        if (!suppressed && panelRef?.get() == null) return
        suppressed = false
        restorePanelDepthFromSettings()
        savedDepthEnable = null
        logI("depth released")
    }

    /** Hook 回弹时再刷一遍隐藏（不改 suppressed 标记）。 */
    fun reapplyIfSuppressed() {
        if (!shouldSuppress()) return
        suppressed = true
        applySuppressToPanel()
    }

    fun reset() {
        suppressed = false
        savedDepthEnable = null
        applying = false
        panelRef = null
    }

    /** 测试用：写入 savedDepthEnable。 */
    internal fun setSavedDepthEnableForTest(value: Boolean?) {
        savedDepthEnable = value
    }

    /** 测试用。 */
    internal fun savedDepthEnableForTest(): Boolean? = savedDepthEnable

    private fun applySuppressToPanel() {
        if (applying) return
        val panel = panelRef?.get() ?: return
        applying = true
        try {
            setBooleanField(panel, "depthEffectEnable", false)
            val interactor = getField(panel, "keyguardDepthInteractor") ?: return
            setBooleanField(interactor, "depthEffectEnableInner", false)
            setBooleanField(interactor, "isActualDisplayDepth", false)

            hideDeductedImage(interactor)
            hideForegroundLayer(panel, interactor)
            hideVideoDepth(interactor)
            forceDepthAlphaZero(interactor)

            invokeNoArg(panel, "updateShowDepthState")
            invokeNoArg(panel, "updateKeyguardElementsVisibility")
            hideDeductedImage(interactor)
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
            val enabled = savedDepthEnable ?: readDepthEffectSetting(panel)
            setBooleanField(panel, "depthEffectEnable", enabled)
            val interactor = getField(panel, "keyguardDepthInteractor")
            if (interactor != null) {
                setBooleanField(interactor, "depthEffectEnableInner", enabled)
                if (enabled) {
                    invokeNoArg(interactor, "updateDeductedImageView")
                    invokeNoArg(interactor, "updateVideoDepthSurface")
                } else {
                    hideDeductedImage(interactor)
                    hideVideoDepth(interactor)
                }
            }
            invokeNoArg(panel, "updateShowDepthState")
            invokeNoArg(panel, "updateKeyguardElementsVisibility")
        } catch (e: Throwable) {
            logE("restorePanelDepthFromSettings error", e)
        } finally {
            applying = false
        }
    }

    /** 直接读 Secure 设置，绕过 getDepthEffectEnable hook。 */
    fun readDepthEffectSetting(panel: Any): Boolean {
        try {
            val ctx = getField(panel, "context") as? Context
            if (ctx != null) {
                val type = Settings.Secure.getInt(
                    ctx.contentResolver,
                    SETTING_WALLPAPER_EFFECT_TYPE_2,
                    0
                )
                return type == DEPTH_EFFECT_TYPE
            }
        } catch (_: Throwable) {
        }
        return getBooleanField(panel, "depthEffectEnable") ?: false
    }

    /** 纯函数：effect type → 是否景深。 */
    fun isDepthEffectType(effectType: Int): Boolean = effectType == DEPTH_EFFECT_TYPE

    private fun forceDepthAlphaZero(interactor: Any) {
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
                arrayOf(0f, null, false)
            )
        } catch (_: Throwable) {
            // Folme setTo 已在 hideDeductedImage 里兜底
        }
    }

    private fun hideDeductedImage(interactor: Any) {
        val image = getField(interactor, "deductedImageView") as? ImageView ?: return
        image.visibility = View.INVISIBLE
        image.alpha = 0f
        try {
            image.setImageDrawable(null)
        } catch (_: Throwable) {
        }
        val folme = getField(interactor, "deductedTranslateAlphaFolmeAnimator")
        if (folme != null) {
            try {
                val viewProperty = Class.forName("miuix.animation.property.ViewProperty")
                val alpha = viewProperty.getField("TRANSITION_ALPHA").get(null)
                folme.javaClass.getMethod("setTo", Any::class.java, Any::class.java)
                    .invoke(folme, alpha, 0f)
            } catch (_: Throwable) {
                try {
                    folme.javaClass.getMethod("setTo", String::class.java, Any::class.java)
                        .invoke(folme, "transitionAlpha", 0f)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun hideForegroundLayer(panel: Any, interactor: Any) {
        val layer = (getField(panel, "keyguardForegroundLayer") as? ViewGroup)
            ?: (getField(interactor, "keyguardForegroundLayer") as? ViewGroup)
            ?: return
        layer.visibility = View.INVISIBLE
    }

    private fun hideVideoDepth(interactor: Any) {
        val holder = getField(interactor, "videoDepthSurfaceHolder") ?: return
        try {
            (getField(holder, "backgroundTextureView") as? TextureView)?.visibility = View.INVISIBLE
            (getField(holder, "foregroundTextureView") as? TextureView)?.visibility = View.INVISIBLE
        } catch (_: Throwable) {
        }
        try {
            invokeNoArg(interactor, "removeVideoDepthSurface")
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
