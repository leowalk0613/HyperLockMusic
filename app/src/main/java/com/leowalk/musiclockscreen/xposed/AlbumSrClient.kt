package com.leowalk.musiclockscreen.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import java.io.ByteArrayOutputStream

/**
 * SystemUI 侧调用模块进程做专辑超分（RealCUGAN models-nose）。
 */
object AlbumSrClient {

    private const val TAG = "MusicLockScreen_AlbumSrClient"
    private val URI: Uri = Uri.parse("content://com.leowalk.musiclockscreen.config/config")

    fun enhanceTo720(context: Context, source: Bitmap, trackKey: String?): Bitmap? {
        return try {
            val jpeg = ByteArrayOutputStream().use { bos ->
                source.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                bos.toByteArray()
            }
            val extras = Bundle().apply {
                putByteArray("jpeg", jpeg)
                putString("track_key", trackKey)
            }
            val result = context.contentResolver.call(URI, "enhanceAlbum", null, extras)
                ?: return null
            val out = result.getByteArray("jpeg") ?: return null
            BitmapFactory.decodeByteArray(out, 0, out.size)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "enhanceTo720 ipc failed", e)
            null
        }
    }
}
