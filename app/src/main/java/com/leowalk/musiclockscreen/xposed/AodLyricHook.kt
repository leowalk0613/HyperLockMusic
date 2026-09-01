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
    private var cfgShowLyric: Boolean = true
    private var awaitingFreshLyrics = false

    private var lastVLyric = -1
    private var lastVLyricFd = -1
    private var lastSongTitle = ""
    private var lastLyricJson = "{}"
    private var cachedLines: List<AodLyricLine> = emptyList()
    private var posBase: Long = 0L
    private var posBaseTime: Long = 0L
    private var polling = false
    private val handler = Handler(Looper.getMainLooper())

    private data class AodLyricLine(val time: Long, val text: String, val translation: String = "")

    companion object {
        private const val TAG = "HyperLockMusic_AodLyric"
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
                val swapIdx = cursor.getColumnIndex("swap_lyric")
                if (swapIdx >= 0) {
                    cfgSwapLyric = cursor.getInt(swapIdx) != 0
                    logI("swap_lyric config: $cfgSwapLyric")
                }
                val showIdx = cursor.getColumnIndex("show_lyric")
                if (showIdx >= 0) {
                    cfgShowLyric = cursor.getInt(showIdx) != 0
                    logI("show_lyric config: $cfgShowLyric")
                }
                cursor.close()
            }
            if (!cfgShowLyric) {
                sLyricContainer?.visibility = View.GONE
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
            if (!cfgShowLyric) {
                sLyricContainer?.visibility = View.GONE
                return
            }
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
            if (!versionsChanged && !titleChanged && !awaitingFreshLyrics) {
                // version 未变：用全量时间轴按播放进度刷当前行
                if (cachedLines.isNotEmpty()) {
                    applyCurrentLineFromCache(ctx)
                }
                return
            }

            val oldVLyric = lastVLyric
            val oldVFd = lastVLyricFd
            if (titleChanged) {
                lastLyricJson = "{}"
                cachedLines = emptyList()
                awaitingFreshLyrics = true
                sLyricContainer?.visibility = View.GONE
            }

            lastVLyric = vLyric
            lastVLyricFd = vFd
            if (mediaTitle.isNotBlank()) lastSongTitle = mediaTitle

            // 标题已变但 version 未涨：仍读轻量包，LyricFocus 可能就地更新
            if (titleChanged && !versionsChanged) {
                if (tryReadLyricWithoutVersionBump(ctx, uri, mediaTitle)) {
                    awaitingFreshLyrics = false
                }
                return
            }

            var fdRead = false
            if (oldVFd != vFd || titleChanged || awaitingFreshLyrics) {
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
                        val json = String(bos.toByteArray(), Charsets.UTF_8)
                        val jo = JSONObject(json)
                        if (!isProviderLyricStale(jo, mediaTitle)) {
                            lastLyricJson = json
                            fdRead = true
                        }
                    }
                } catch (e: Throwable) {
                    logE("refreshLyric lyric_fd error", e)
                }
            }

            if ((oldVLyric != vLyric || titleChanged || awaitingFreshLyrics) &&
                (oldVFd == vFd || !fdRead)
            ) {
                try {
                    val lb = ctx.contentResolver.call(uri, "lyric", null, null)
                    val j = lb?.getString("n")
                    if (j != null) {
                        val neu = JSONObject(j)
                        if (isProviderLyricStale(neu, mediaTitle)) {
                            lastLyricJson = "{}"
                        } else if (titleChanged || lastLyricJson == "{}" || awaitingFreshLyrics) {
                            lastLyricJson = j
                        } else {
                            try {
                                val old = JSONObject(lastLyricJson)
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

            val jo = JSONObject(lastLyricJson)
            if (!isProviderLyricStale(jo, mediaTitle) && hasDisplayableLyric(jo)) {
                awaitingFreshLyrics = false
            }
            rebuildCachedLines(jo)
            if (cachedLines.isNotEmpty()) {
                applyCurrentLineFromCache(ctx)
            } else {
                applyLyricToViews(jo, mediaTitle)
            }
        } catch (e: Throwable) {
            logE("refreshLyric error", e)
        }
    }

    private fun rebuildCachedLines(jo: JSONObject) {
        val ctxObj = jo.optJSONObject("ctx") ?: run {
            if (!jo.has("l") && !jo.has("s")) {
                // 空推可能清缓存；保留已有时间轴直到明确无词
            }
            return
        }
        val linesArr = ctxObj.optJSONArray("lines") ?: return
        if (linesArr.length() <= 0) return
        if (linesArr.optJSONObject(0)?.has("tm") != true) return
        val parsed = ArrayList<AodLyricLine>(linesArr.length())
        for (i in 0 until linesArr.length()) {
            val o = linesArr.optJSONObject(i) ?: continue
            val t = o.optString("t", "").trim()
            if (t.isEmpty()) continue
            parsed.add(
                AodLyricLine(
                    time = o.optLong("tm", Long.MAX_VALUE),
                    text = t,
                    translation = o.optString("r", "").trim(),
                )
            )
        }
        if (parsed.isNotEmpty()) cachedLines = parsed
    }

    private fun applyCurrentLineFromCache(ctx: Context) {
        if (!cfgShowLyric) {
            sLyricContainer?.visibility = View.GONE
            return
        }
        val lines = cachedLines
        if (lines.isEmpty()) return
        val pos = getCurrentPosition(ctx)
        var idx = if (pos >= 0) findCurrentLineIndex(lines, pos) else 0
        if (idx < 0) idx = 0
        val cur = lines[idx]
        var mainText = cur.text
        var subText = cur.translation.ifBlank {
            if (idx + 1 < lines.size) lines[idx + 1].text else ""
        }
        if (cfgSwapLyric && cur.translation.isNotBlank()) {
            val tmp = mainText
            mainText = cur.translation
            subText = tmp
        }
        if (mainText.isEmpty()) {
            sLyricContainer?.visibility = View.GONE
            return
        }
        sLyricContainer?.visibility = View.VISIBLE
        sMainLyric?.text = mainText
        sSubLyric?.text = subText
        sSubLyric?.visibility = if (subText.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun findCurrentLineIndex(lines: List<AodLyricLine>, pos: Long): Int {
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].time <= pos) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun getCurrentPosition(ctx: Context): Long {
        return try {
            val sessions = com.leowalk.musiclockscreen.MediaSessionAccess.getActiveControllers(ctx)
            val now = android.os.SystemClock.elapsedRealtime()
            for (controller in sessions) {
                val state = controller.playbackState ?: continue
                val playing = state.state == android.media.session.PlaybackState.STATE_PLAYING
                if (!playing && posBaseTime == 0L) continue
                val delta = now - posBaseTime
                if (delta > 800L || !playing || posBaseTime == 0L) {
                    val p = state.position
                    posBase = p
                    posBaseTime = now
                    return p
                }
                val speed = state.playbackSpeed.takeIf { it > 0f } ?: 1f
                return posBase + (delta * speed).toLong()
            }
            -1L
        } catch (_: Throwable) {
            -1L
        }
    }

    private fun tryReadLyricWithoutVersionBump(ctx: Context, uri: Uri, mediaTitle: String): Boolean {
        try {
            val lb = ctx.contentResolver.call(uri, "lyric", null, null)
            val j = lb?.getString("n") ?: return false
            val jo = JSONObject(j)
            if (isProviderLyricStale(jo, mediaTitle) || !hasDisplayableLyric(jo)) return false
            lastLyricJson = j
            rebuildCachedLines(jo)
            if (cachedLines.isNotEmpty()) {
                applyCurrentLineFromCache(ctx)
            } else {
                applyLyricToViews(jo, mediaTitle)
            }
            return true
        } catch (e: Throwable) {
            logE("tryReadLyricWithoutVersionBump error", e)
            return false
        }
    }

    private fun hasDisplayableLyric(jo: JSONObject): Boolean {
        if (jo.optString("l", "").trim().isNotEmpty()) return true
        if (jo.optString("s", "").trim().isNotEmpty()) return true
        val ctxObj = jo.optJSONObject("ctx") ?: return false
        val lines = ctxObj.optJSONArray("lines") ?: return false
        for (i in 0 until lines.length()) {
            if (lines.optJSONObject(i)?.optString("t", "")?.trim()?.isNotEmpty() == true) {
                return true
            }
        }
        return false
    }

    private fun isProviderLyricStale(json: JSONObject, mediaTitle: String): Boolean {
        val providerTitle = json.optString("title", "").trim()
        if (providerTitle.isBlank()) return false
        val expected = mediaTitle.trim().ifBlank { lastSongTitle.trim() }
        if (expected.isBlank()) return false
        if (providerTitle == expected) return false
        val p = providerTitle.replace(" ", "")
        val m = expected.replace(" ", "")
        if (p.contains(m) || m.contains(p)) return false
        return true
    }

    private fun applyLyricToViews(jo: JSONObject, mediaTitle: String? = null) {
        if (!cfgShowLyric) {
            sLyricContainer?.visibility = View.GONE
            return
        }
        val resolvedMediaTitle = mediaTitle ?: readCurrentMediaTitle(sRoot?.context ?: return)
        val title = jo.optString("title", "").trim()
        if (title.isNotBlank() && resolvedMediaTitle.isNotBlank() &&
            isProviderLyricStale(jo, resolvedMediaTitle)
        ) {
            sLyricContainer?.visibility = View.GONE
            return
        }

        var mainText = jo.optString("l", "").trim()
        var subText = jo.optString("s", "").trim()
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
            val sessions = com.leowalk.musiclockscreen.MediaSessionAccess.getActiveControllers(ctx)
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
            val interval = if (awaitingFreshLyrics) 300L else 500L
            handler.postDelayed(this, interval)
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
