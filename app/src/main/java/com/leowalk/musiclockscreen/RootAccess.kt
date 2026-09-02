package com.leowalk.musiclockscreen

/**
 * 检测本机是否已向 Shell 授予 root（用于重启 SystemUI 等）。
 */
object RootAccess {

    @Volatile
    private var cachedGranted: Boolean? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private const val CACHE_MS = 5_000L

    fun invalidate() {
        cachedGranted = null
        cachedAtMs = 0L
    }

    fun isGranted(): Boolean {
        val now = System.currentTimeMillis()
        cachedGranted?.let { if (now - cachedAtMs < CACHE_MS) return it }
        return probeAndCache()
    }

    fun probeAndCache(): Boolean {
        val granted = probe()
        cachedGranted = granted
        cachedAtMs = System.currentTimeMillis()
        return granted
    }

    internal fun probe(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }
}
