package com.leowalk.musiclockscreen.xposed.wallpaper

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * 串行锁屏壁纸写入队列：同一时刻只有一个 setBitmap。
 * 过期任务在写入前取消。调用方须在 [write] 内再次校验并完成 commit（持锁）。
 */
class SerialWallpaperApplier(
    private val shouldWrite: (jobId: Long) -> Boolean,
    executor: ExecutorService? = null
) {
    private val ownExecutor = executor == null
    private val worker: ExecutorService = executor ?: Executors.newSingleThreadExecutor(
        object : ThreadFactory {
            private val n = AtomicInteger()
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "HyperLockMusic-WpApply-${n.incrementAndGet()}").apply {
                    isDaemon = true
                }
            }
        }
    )

    /**
     * @param write 实际 WallpaperManager.setBitmap + commit；仅在预检通过后调用。
     *              若 write 内发现已过期应直接 return（不抛错）。
     * @param onCommitted write 正常返回后回调（调用方在 write 内已 commit 状态）
     * @param onCancelled 预检失败，未调用 write
     */
    fun enqueue(
        jobId: Long,
        write: () -> Boolean,
        onCommitted: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        worker.execute {
            try {
                if (!shouldWrite(jobId)) {
                    onCancelled?.invoke()
                    return@execute
                }
                val wrote = write()
                if (wrote) {
                    onCommitted?.invoke()
                } else {
                    onCancelled?.invoke()
                }
            } catch (t: Throwable) {
                onError?.invoke(t)
            }
        }
    }

    fun shutdown() {
        if (ownExecutor) {
            worker.shutdownNow()
        }
    }
}
