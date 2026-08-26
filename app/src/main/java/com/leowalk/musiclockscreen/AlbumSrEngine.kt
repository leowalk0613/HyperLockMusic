package com.leowalk.musiclockscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 专辑超分引擎（4x-UltraSharp via realsr-ncnn）
 *
 * 模型：assets/realsr/models-Real-ESRGAN/x4.{param,bin}
 * （Kim2091 4x-UltraSharp fp16；目录名需含 models- 以兼容 realsr-ncnn）
 * CLI/so 放 assets（不进 jniLibs）；解压后经 su 执行。
 * ×4 后短边不足 1080 才放大，更大不缩小。
 */
object AlbumSrEngine {

    private const val TAG = "MusicLockScreen_AlbumSr"
    /** 最低目标边长；超分结果更大时保留，不强制缩小 */
    const val TARGET = 1080
    private const val ASSETS_ROOT = "realsr"
    private const val BIN_NAME = "realsr-ncnn"
    private const val MODEL_DIR = "models-Real-ESRGAN"
    private const val READY_MARKER = ".ready_v7_ultrasharp_as_realesrgan"

    @Volatile
    private var ready = false

    fun ensureReady(context: Context): Boolean {
        if (ready) return true
        return try {
            val dir = workDir(context)
            val marker = File(dir, READY_MARKER)
            val bin = File(dir, BIN_NAME)
            val modelParam = File(dir, "$MODEL_DIR/x4.param")
            if (!marker.exists() || !bin.exists() || !modelParam.exists()) {
                extractAssets(context, dir)
                chmodViaSu(bin.absolutePath, "755")
                listOf("libc++_shared.so", "libncnn.so", "libomp.so").forEach { name ->
                    val f = File(dir, name)
                    if (f.exists()) chmodViaSu(f.absolutePath, "644")
                }
                marker.writeText("ok")
            }
            ready = bin.exists() && modelParam.exists()
            if (!ready) {
                Log.e(TAG, "not ready bin=${bin.exists()} model=${modelParam.exists()}")
            } else {
                Log.i(TAG, "ready UltraSharp x4 bin=${bin.absolutePath}")
            }
            ready
        } catch (e: Throwable) {
            Log.e(TAG, "ensureReady failed", e)
            false
        }
    }

    /**
     * 专辑图 UltraSharp ×4。短边已 ≥ [TARGET] 则拷贝返回；
     * 超分后短边仍不足则放大到 TARGET；大于 TARGET 不缩小。
     */
    fun enhanceTo720(context: Context, source: Bitmap): Bitmap? {
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        if (!ensureReady(context)) return null

        if (minOf(source.width, source.height) >= TARGET) {
            return if (source.width == source.height) {
                source.copy(Bitmap.Config.ARGB_8888, false)
                    ?: scaleCenterCrop(source, source.width, source.height)
            } else {
                val side = minOf(source.width, source.height)
                scaleCenterCrop(source, side, side)
            }
        }

        val workDir = workDir(context)
        val inFile = File(workDir, "in_${Thread.currentThread().id}.png")
        val outFile = File(workDir, "out_${Thread.currentThread().id}.png")
        return try {
            FileOutputStream(inFile).use { out ->
                source.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!runRealSr(workDir, inFile, outFile)) {
                Log.e(TAG, "realsr-ncnn UltraSharp failed")
                return null
            }
            val decoded = BitmapFactory.decodeFile(outFile.absolutePath) ?: return null
            val out = finalizeSize(decoded)
            if (out !== decoded && !decoded.isRecycled) decoded.recycle()
            Log.i(TAG, "UltraSharp x4 done -> ${out.width}x${out.height}")
            out
        } catch (e: Throwable) {
            Log.e(TAG, "enhanceTo720 error", e)
            null
        } finally {
            inFile.delete()
            outFile.delete()
        }
    }

    private fun finalizeSize(src: Bitmap): Bitmap {
        val minSide = minOf(src.width, src.height)
        return if (minSide < TARGET) {
            val out = scaleCenterCrop(src, TARGET, TARGET)
            if (out !== src && !src.isRecycled) src.recycle()
            out
        } else if (src.width != src.height) {
            val side = minSide
            val out = scaleCenterCrop(src, side, side)
            if (out !== src && !src.isRecycled) src.recycle()
            out
        } else {
            src
        }
    }

    private fun runRealSr(workDir: File, input: File, output: File): Boolean {
        output.delete()
        val bin = File(workDir, BIN_NAME).absolutePath
        val modelPath = File(workDir, MODEL_DIR).absolutePath
        // realsr-ncnn: -m <dir> -s 4 → 读取 dir/x4.param + x4.bin
        val shell = buildString {
            append("export LD_LIBRARY_PATH='").append(workDir.absolutePath).append("'; ")
            append("cd '").append(workDir.absolutePath).append("' && ")
            append("exec '").append(bin).append("'")
            append(" -i '").append(input.absolutePath).append("'")
            append(" -o '").append(output.absolutePath).append("'")
            append(" -m '").append(modelPath).append("'")
            append(" -s 4 -g 0")
        }
        val pb = ProcessBuilder("su", "0", "sh", "-c", shell)
            .redirectErrorStream(true)
        val process = pb.start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0 || !output.exists() || output.length() < 32) {
            // GPU 失败时回退 CPU
            Log.w(TAG, "realsr GPU failed exit=$code, fallback CPU. log=${log.take(400)}")
            output.delete()
            val shellCpu = shell.replace(" -g 0", " -g -1")
            val pb2 = ProcessBuilder("su", "0", "sh", "-c", shellCpu)
                .redirectErrorStream(true)
            val p2 = pb2.start()
            val log2 = p2.inputStream.bufferedReader().use { it.readText() }
            val code2 = p2.waitFor()
            if (code2 != 0 || !output.exists() || output.length() < 32) {
                Log.e(TAG, "realsr CPU exit=$code2 log=${log2.take(800)}")
                return false
            }
            Log.i(TAG, "UltraSharp CPU ok -> ${output.length()} bytes")
            return true
        }
        Log.i(TAG, "UltraSharp GPU ok -> ${output.length()} bytes")
        return true
    }

    private fun chmodViaSu(path: String, mode: String) {
        try {
            ProcessBuilder("su", "0", "chmod", mode, path)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Throwable) {
            try {
                File(path).setExecutable(true, false)
                File(path).setReadable(true, false)
            } catch (_: Throwable) {
            }
        }
    }

    private fun extractAssets(context: Context, destDir: File) {
        destDir.mkdirs()
        val am = context.assets
        fun copyTree(assetPath: String, dest: File) {
            val children = am.list(assetPath) ?: emptyArray()
            if (children.isEmpty()) {
                dest.parentFile?.mkdirs()
                am.open(assetPath).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                return
            }
            dest.mkdirs()
            for (name in children) {
                val childAsset = if (assetPath.isEmpty()) name else "$assetPath/$name"
                copyTree(childAsset, File(dest, name))
            }
        }
        copyTree(ASSETS_ROOT, destDir)
    }

    private fun workDir(context: Context): File {
        return File(context.filesDir, "realsr").also { it.mkdirs() }
    }

    private fun scaleCenterCrop(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = maxOf(dstW / srcW, dstH / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - dstW) / 2).coerceAtLeast(0)
        val y = ((scaledH - dstH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        Canvas(cropped).drawBitmap(
            scaled,
            -x.toFloat(),
            -y.toFloat(),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        if (scaled !== src && !scaled.isRecycled) scaled.recycle()
        return cropped
    }
}
