package com.leowalk.musiclockscreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** 彩蛋壁纸预览：应用内简化合成，与锁屏 / BlurUtils 实机链路脱钩。 */
object WallpaperMakerDemoRenderer {

    const val DEMO_WIDTH = 1080
    const val DEMO_HEIGHT = 2400

    fun albumTopPx(canvasHeight: Int, coverSide: Int, centerYPercent: Float): Float {
        val centerY = canvasHeight * (centerYPercent / 100f)
        return centerY - coverSide / 2f
    }

    fun render(source: Bitmap, albumCenterYPercent: Float): Bitmap {
        val out = Bitmap.createBitmap(DEMO_WIDTH, DEMO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(18, 18, 20))

        val coverSide = (DEMO_WIDTH * 0.72f).toInt()
        val scaled = Bitmap.createScaledBitmap(source, coverSide, coverSide, true)
        canvas.drawBitmap(scaled, (DEMO_WIDTH - coverSide) / 2f, albumTopPx(DEMO_HEIGHT, coverSide, albumCenterYPercent), null)
        if (scaled !== source) scaled.recycle()

        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, DEMO_WIDTH.toFloat(), DEMO_HEIGHT.toFloat(), shade)
        return out
    }
}
