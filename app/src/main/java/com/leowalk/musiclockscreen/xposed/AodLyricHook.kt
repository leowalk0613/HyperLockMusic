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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

/**
 * AOD 双行歌词注入
 *
 * Hook AOD 进程，往 AODView 里注入双行歌词 View。
 * 通过 ContentObserver + 轮询读取 [LyricDataProvider]（AOD 下 observer 可能不可靠）。
 */
class AodLyricHook {

    private var sRoot: ViewGroup? = null
    private var sLyricContainer: LinearLayout? = null
    private var sMainLyric: TextView? = null
    private var sSubLyric: TextView? = null
    private var sObserverRegistered = false
    private var sConfigObserverRegistered = false
    private var cfgSwapLyric: Boolean = true

    private var lastVLyric = -1
    private var lastVLyricFd = -1
    private var lastSongTitle = ""
    private var lastLyricJson = "{}"
    private var polling = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MusicLockScreen_AodLyric"
        private const val LYRIC_URI = "content://com.leowalk.musiclockscreen.lyric"
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
                        startPolling()
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

        sLyricContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        sMainLyric = TextView(ctx).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(dp(density, 2f), 0f, 0f, Color.argb(128, 0, 0, 0))
        }
        sLyricContainer?.addView(
            sMainLyric,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        sSubLyric = TextView(ctx).apply {
            textSize = 14f
            setTextColor(Color.argb(180, 255, 255, 255))
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(dp(density, 2f), 0f, 0f, Color.argb(100, 0, 0, 0))
        }
        sLyricContainer?.addView(
            sSubLyric,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(density, 6f).toInt()
            }
        )

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

            var vLyric = lastVLyric
            var vFd = lastVLyricFd
            try {
                val vb = ctx.contentResolver.call(uri, "versions", null, null)
                if (vb != null) {
                    vLyric = vb.getInt("lyric", -1)
                    vFd = vb.getInt("lyricfd", -1)
                }
            } catch (e: Throwable) {
                logE("refreshLyric versions error", e)
            }

            val mediaTitle = readCurrentMediaTitle(ctx)
            val titleChanged = mediaTitle.isNotBlank() &&
                lastSongTitle.isNotBlank() &&
                mediaTitle != lastSongTitle
            val versionsChanged = vLyric != lastVLyric || vFd != lastVLyricFd
            if (!versionsChanged && !titleChanged) return

            val oldVLyric = lastVLyric
            val oldVFd = lastVLyricFd
            if (titleChanged) {
                lastLyricJson = "{}"
                lastVLyric = -1
                lastVLyricFd = -1
            }

            lastVLyric = vLyric
            lastVLyricFd = vFd
            if (mediaTitle.isNotBlank()) lastSongTitle = mediaTitle

            var fdRead = false
            if (oldVFd != vFd || titleChanged) {
                try {
                    val fb = ctx.contentResolver.call(uri, "lyric_fd", null, null)
                    val pfd = fb?.getParcelable<android.os.ParcelFileDescriptor>("fd")
                    if (pfd != null) {
                        val fis = FileInputStream(pfd.fileDescriptor)
                        val bos = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var n: Int
                        while (true) {
                            n = fis.read(buf)
                            if (n <= 0) break
                            bos.write(buf, 0, n)
                        }
                        fis.close()
                        pfd.close()
                        lastLyricJson = String(bos.toByteArray(), Charsets.UTF_8)
                        fdRead = true
                    }
                } catch (e: Throwable) {
                    logE("refreshLyric lyric_fd error", e)
                }
            }

            if ((oldVLyric != vLyric || titleChanged) && (oldVFd == vFd || !fdRead)) {
                try {
                    val lb = ctx.contentResolver.call(uri, "lyric", null, null)
                    val j = lb?.getString("n")
                    if (j != null) {
                        if (titleChanged || lastLyricJson == "{}") {
                            lastLyricJson = j
                        } else {
                            try {
                                val old = JSONObject(lastLyricJson)
                                val neu = JSONObject(j)
                                if (!neu.has("ctx") && old.has("ctx")) {
                                    neu.put("ctx", old.get("ctx"))
                                }
                                lastLyricJson = neu.toString()
                            } catch (_: Throwable) {
                                lastLyricJson = j
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logE("refreshLyric lyric bundle error", e)
                }
            }

            applyLyricToViews(JSONObject(lastLyricJson))
        } catch (e: Throwable) {
            logE("refreshLyric error", e)
        }
    }

    private fun applyLyricToViews(jo: JSONObject) {
        var mainText = jo.optString("l", "").trim()
        var subText = jo.optString("s", "").trim()
        val title = jo.optString("title", "").trim()
        if (title.isNotBlank()) lastSongTitle = title

        if (mainText.isEmpty() && title.isNotEmpty()) {
            mainText = title
        }

        if (cfgSwapLyric && subText.isNotBlank()) {
            val tmp = mainText
            mainText = subText
            subText = tmp
        }

        val hasSub = subText.isNotBlank()
        if (mainText.isEmpty()) {
            sLyricContainer?.visibility = View.GONE
            return
        }

        sLyricContainer?.visibility = View.VISIBLE
        sMainLyric?.text = mainText
        sSubLyric?.text = subText
        sSubLyric?.visibility = if (hasSub) View.VISIBLE else View.GONE

        logI("applyLyric: main=$mainText sub=$subText")
    }

    private fun readCurrentMediaTitle(ctx: Context): String {
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? android.media.session.MediaSessionManager ?: return ""
            val sessions = try {
                msm.getActiveSessions(null)
            } catch (_: Throwable) {
                emptyList()
            }
            for (controller in sessions) {
                val title = controller.metadata?.getString(
                    android.media.MediaMetadata.METADATA_KEY_TITLE
                )
                if (!title.isNullOrBlank()) return title
            }
            ""
        } catch (_: Throwable) {
            ""
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            refreshLyric()
            handler.postDelayed(this, 500L)
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(pollRunnable)
    }

    private fun dp(density: Float, dp: Float): Float = dp * density

    private fun logI(msg: String) {
        android.util.Log.i(TAG, msg)
    }

    private fun logE(msg: String, e: Throwable? = null) {
        android.util.Log.e(TAG, msg, e)
    }
}
