package com.leowalk.musiclockscreen

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 彩蛋：沉浸封面壁纸制作（独立演示 UI，不调用锁屏 / SystemUI 渲染链路）。
 */
class WallpaperMakerActivity : BaseScrollingActivity() {

    private var sourceBitmap: Bitmap? = null
    private var resultBitmap: Bitmap? = null

    /** 专辑竖直中心占屏高百分比（彩蛋页本地参数，不写模块配置） */
    private var albumCenterY = 38f

    private lateinit var previewView: ImageView
    private lateinit var progressBar: ProgressBar
    private val worker = Executors.newSingleThreadExecutor()

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) loadSource(uri)
    }

    override fun titleText() = "制作壁纸"

    override fun onDestroy() {
        worker.shutdownNow()
        sourceBitmap?.recycle()
        resultBitmap?.recycle()
        super.onDestroy()
    }

    override fun buildContent(list: LinearLayout) {
        val previewCard = M3.cardContent(this)
        previewCard.addView(M3.title(this, "预览"))

        previewView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF121212.toInt())
            minimumHeight = M3.dp(this@WallpaperMakerActivity, 320f)
        }
        previewCard.addView(
            previewView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = android.view.View.GONE
        }
        previewCard.addView(progressBar)
        list.addView(M3.card(this, previewCard))

        list.addView(M3.clickRow(this, "选择图片", "从相册挑选专辑封面或任意图片") {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })

        val paramCard = M3.cardContent(this)
        paramCard.addView(M3.title(this, "参数"))
        paramCard.addView(M3.sliderRow(
            this, "专辑位置", 20f, 55f, albumCenterY,
            { "中心 ${it.toInt()}% 屏高" },
        ) { v ->
            albumCenterY = v
        })
        list.addView(M3.card(this, paramCard))

        list.addView(M3.clickRow(this, "生成预览", "彩蛋演示预览（与锁屏渲染独立）") {
            generatePreview()
        })

        list.addView(M3.clickRow(this, "保存到相册", "将预览图保存为 PNG") {
            saveToGallery()
        })

        list.addView(M3.card(this, M3.tipContent(this,
            "这是隐藏彩蛋页：界面与参数仅供把玩，不会写入模块配置，也不会影响锁屏壁纸。\n\n" +
                "预览使用应用内简化合成，与 SystemUI 沉浸封面算法脱钩。")))
    }

    private fun loadSource(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: run {
                    toast("无法读取图片")
                    return
                }
                sourceBitmap?.recycle()
                resultBitmap?.recycle()
                resultBitmap = null
                sourceBitmap = bmp
                previewView.setImageBitmap(bmp)
                toast("已选择 ${bmp.width}×${bmp.height}")
            }
        } catch (e: Exception) {
            toast("读取失败：${e.message}")
        }
    }

    private fun generatePreview() {
        val src = sourceBitmap
        if (src == null || src.isRecycled) {
            toast("请先选择图片")
            return
        }
        setBusy(true)
        val centerY = albumCenterY
        worker.execute {
            try {
                val out = WallpaperMakerDemoRenderer.render(src, centerY)
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        if (!out.isRecycled) out.recycle()
                        return@runOnUiThread
                    }
                    resultBitmap?.recycle()
                    resultBitmap = out
                    previewView.setImageBitmap(out)
                    setBusy(false)
                    toast("彩蛋预览已生成 ${out.width}×${out.height}")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    toast("生成失败：${e.message}")
                }
            }
        }
    }

    private fun saveToGallery() {
        val bmp = resultBitmap
        if (bmp == null || bmp.isRecycled) {
            toast("请先生成预览")
            return
        }
        try {
            val name = "hyperlockmusic_easter_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/HyperLockMusic",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: run {
                    toast("无法创建文件")
                    return
                }
            resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: run {
                toast("写入失败")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            toast("已保存：Pictures/HyperLockMusic/$name")
        } catch (e: Exception) {
            toast("保存失败：${e.message}")
        }
    }

    private fun setBusy(busy: Boolean) {
        progressBar.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
