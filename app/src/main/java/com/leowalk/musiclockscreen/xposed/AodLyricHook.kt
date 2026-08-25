package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * AOD 双行歌词注入
 *
 * Hook AOD 进程，往 AODView 里注入双行歌词 View
 * 通过 ContentObserver 监听歌词数据变化
 */
class AodLyricHook {

    private var sRoot: ViewGroup? = null
    private var sLyricContainer: LinearLayout? = null
    private var sMainLyric: TextView? = null
    private var sSubLyric: TextView? = null
    private var sObserverRegistered = false
    private var sConfigObserverRegistered = false
    private var cfgSwapLyric: Boolean = true

    companion object {
        private const val TAG = "MusicLockScreen_AodLyric"
        private const val LYRIC_URI = "content://com.leowalk.musiclockscreen.config/lyric"
        private const val CONFIG_URI = "content://com.leowalk.musiclockscreen.config/config"
    }

    fun install(classLoader: ClassLoader, module: XposedModule) {
        try {
            logI("install: start")

            val aodViewClass = Class.forName("com.miui.aod.AODView", false, classLoader)
            logI("install: AODView class found = ${aodViewClass.name}")

            val updateMethod = aodViewClass.getDeclaredMethod(
                "handleUpdateView",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            updateMethod.isAccessible = true
            logI("install: handleUpdateView method found")

            module.hook(updateMethod).intercept { chain ->
                chain.proceed()
                try {
                    val thisObj = chain.thisObject
                    if (thisObj is ViewGroup) {
                        sRoot = thisObj
                        logI("handleUpdateView: root = ${thisObj.javaClass.simpleName}, childCount = ${thisObj.childCount}")
                        registerObserverOnce()
                        setupLyricView()
                        registerConfigObserverOnce()
                        refreshLyric()
                    } else {
                        logI("handleUpdateView: thisObj is not ViewGroup, is ${thisObj?.javaClass?.name}")
                    }
                } catch (e: Throwable) {
                    logE("handleUpdateView hook error", e)
                }
                null
            }

            logI("install: hook installed successfully")
        } catch (e: Throwable) {
            logE("install failed", e)
        }
    }

    private fun registerObserverOnce() {
        if (sObserverRegistered || sRoot == null) return
        sObserverRegistered = true
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    logI("ContentObserver: onChange triggered")
                    refreshLyric()
                }
            }
            sRoot!!.context.contentResolver.registerContentObserver(
                Uri.parse(LYRIC_URI), true, observer
            )
            logI("ContentObserver registered for $LYRIC_URI")
        } catch (e: Throwable) {
            logE("registerObserver error", e)
        }
    }

    private fun registerConfigObserverOnce() {
        if (sConfigObserverRegistered || sRoot == null) return
        sConfigObserverRegistered = true
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    loadSwapConfig()
                    refreshLyric()
                }
            }
            sRoot!!.context.contentResolver.registerContentObserver(
                Uri.parse(CONFIG_URI), true, observer
            )
            loadSwapConfig()
            logI("ConfigObserver registered for $CONFIG_URI")
        } catch (e: Throwable) {
            logE("registerConfigObserver error", e)
        }
    }

    private fun loadSwapConfig() {
        try {
            val ctx = sRoot?.context ?: return
            val uri = Uri.parse(CONFIG_URI)
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex("swap_lyric")
                if (idx >= 0) {
                    cfgSwapLyric = cursor.getInt(idx) != 0
                    logI("swap_lyric config: $cfgSwapLyric")
                }
                cursor.close()
            }
        } catch (e: Throwable) {
            logE("loadSwapConfig error", e)
        }
    }

    private fun setupLyricView() {
        if (sLyricContainer != null && sLyricContainer?.parent == sRoot) {
            logI("setupLyricView: already added, skip")
            return
        }

        val root = sRoot ?: return
        val ctx = root.context
        val density = ctx.resources.displayMetrics.density
        logI("setupLyricView: density=$density, root width=${root.width}, height=${root.height}")

        // 容器
        sLyricContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.argb(80, 255, 0, 0)) // 红色半透明背景，方便定位
        }

        // 主歌词
        sMainLyric = TextView(ctx).apply {
            text = "音乐锁屏测试"
            textSize = 18f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(dp(density, 2f), 0f, 0f, Color.argb(128, 0, 0, 0))
        }
        val mainParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        sLyricContainer?.addView(sMainLyric, mainParams)

        // 副歌词
        sSubLyric = TextView(ctx).apply {
            text = "双行歌词测试中..."
            textSize = 14f
            setTextColor(Color.argb(180, 255, 255, 255))
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(dp(density, 2f), 0f, 0f, Color.argb(100, 0, 0, 0))
        }
        val subParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(density, 6f).toInt()
        }
        sLyricContainer?.addView(sSubLyric, subParams)

        // 布局参数：放在屏幕中间偏下
        val flp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(density, 320f).toInt()
            leftMargin = dp(density, 40f).toInt()
            rightMargin = dp(density, 40f).toInt()
        }
        root.addView(sLyricContainer, flp)

        logI("setupLyricView: lyric view added to AOD, topMargin=${flp.topMargin}")
    }

    private fun refreshLyric() {
        try {
            val ctx = sRoot?.context ?: return
            val uri = Uri.parse(LYRIC_URI)
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val mainIdx = cursor.getColumnIndex("lyric_main")
                val subIdx = cursor.getColumnIndex("lyric_sub")

                var mainText = ""
                var subText = ""
                var hasSub = false

                if (mainIdx >= 0) {
                    mainText = cursor.getString(mainIdx) ?: ""
                }
                if (subIdx >= 0) {
                    subText = cursor.getString(subIdx) ?: ""
                    hasSub = subText.isNotBlank()
                }

                // 歌词/翻译互换：数据源无法区分 lyric_sub 是"翻译"还是"下一句"，
                // 无条件互换会把没翻译歌词的后一句误当成翻译对调。
                // 因此 AOD 不再做互换，保持 main=当前句、sub=副行原样显示。
                // if (cfgSwapLyric && hasSub) {
                //     val tmp = mainText
                //     mainText = subText
                //     subText = tmp
                // }

                sMainLyric?.text = mainText
                sSubLyric?.text = subText
                sSubLyric?.visibility = if (hasSub) View.VISIBLE else View.GONE

                logI("refreshLyric: main=$mainText sub=$subText swap=$cfgSwapLyric")
                cursor.close()
            } else {
                logI("refreshLyric: cursor is null or empty")
            }
        } catch (e: Throwable) {
            logE("refreshLyric error", e)
        }
    }

    private fun dp(density: Float, dp: Float): Float = dp * density

    private fun logI(msg: String) {
        android.util.Log.i(TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e(TAG, msg, e)
    }
}
