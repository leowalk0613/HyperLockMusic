package com.leowalk.musiclockscreen

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.leowalk.musiclockscreen.xposed.MagazineModePolicy
import org.json.JSONObject

/**
 * 画报左滑/右划入口的自建页：由 SystemUI Hook 改写 Intent 后启动。
 * 媒体与歌词在本进程读取（通知使用权 + LyricDataProvider）。
 */
class MagazineMusicActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var lyricView: TextView? = null
    private var artView: ImageView? = null
    private var playPauseBtn: ImageButton? = null
    private var lastLyricVersion: Int = -1

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshFromSources()
            mainHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(buildUi())
        applyIntentExtras(intent)
        refreshFromSources()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
        refreshFromSources()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun applyIntentExtras(intent: android.content.Intent?) {
        if (intent == null) return
        val title = intent.getStringExtra(MagazineModePolicy.EXTRA_SONG_TITLE).orEmpty()
        val artist = intent.getStringExtra(MagazineModePolicy.EXTRA_SONG_ARTIST).orEmpty()
        val lyric = intent.getStringExtra(MagazineModePolicy.EXTRA_LYRIC_LINE).orEmpty()
        if (title.isNotBlank()) titleView?.text = title
        if (artist.isNotBlank()) artistView?.text = artist
        if (lyric.isNotBlank()) lyricView?.text = lyric
    }

    private fun buildUi(): View {
        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        artView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xFF1A1A1A.toInt())
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
        }
        root.addView(
            artView,
            LinearLayout.LayoutParams(dp(220), dp(220)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(48)
            },
        )

        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            text = "正在播放"
        }
        root.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(28) },
        )

        artistView = TextView(this).apply {
            setTextColor(0xFFB0B0B0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
        }
        root.addView(
            artistView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        lyricView = TextView(this).apply {
            setTextColor(0xFFE8E8E8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            minHeight = dp(72)
        }
        root.addView(
            lyricView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(36)
                weight = 1f
            },
        )

        root.addView(buildTransportRow(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(16)
            bottomMargin = dp(32)
        })

        return root
    }

    private fun buildTransportRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun btn(label: String, onClick: () -> Unit): ImageButton {
            return ImageButton(this@MagazineMusicActivity).apply {
                contentDescription = label
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                setImageResource(
                    when (label) {
                        "prev" -> android.R.drawable.ic_media_previous
                        "next" -> android.R.drawable.ic_media_next
                        else -> android.R.drawable.ic_media_play
                    },
                )
                setOnClickListener { onClick() }
                val s = dp(56)
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                }
            }
        }
        row.addView(btn("prev") { preferredController()?.transportControls?.skipToPrevious() })
        playPauseBtn = btn("play") { togglePlayPause() }
        row.addView(playPauseBtn)
        row.addView(btn("next") { preferredController()?.transportControls?.skipToNext() })
        return row
    }

    private fun togglePlayPause() {
        val ctrl = preferredController() ?: return
        val state = ctrl.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
            ctrl.transportControls.pause()
        } else {
            ctrl.transportControls.play()
        }
        refreshTransportIcon(ctrl)
    }

    private fun refreshFromSources() {
        val ctrl = preferredController()
        val meta = ctrl?.metadata
        if (meta != null) {
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
                .ifBlank { meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.trim().orEmpty() }
            if (title.isNotBlank()) titleView?.text = title
            if (artist.isNotBlank()) artistView?.text = artist
            bindArt(meta)
        }
        refreshTransportIcon(ctrl)
        refreshLyric()
    }

    private fun bindArt(meta: MediaMetadata) {
        val bmp: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (bmp != null && !bmp.isRecycled) {
            artView?.setImageDrawable(BitmapDrawable(resources, bmp))
        }
    }

    private fun refreshTransportIcon(ctrl: MediaController?) {
        val playing = ctrl?.playbackState?.state == PlaybackState.STATE_PLAYING
        playPauseBtn?.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
    }

    private fun refreshLyric() {
        try {
            val versions = contentResolver.call(LyricDataProvider.URI, "versions", null, null)
            val v = versions?.getInt("lyric") ?: -1
            if (v == lastLyricVersion && v >= 0) return
            lastLyricVersion = v
            val bundle = contentResolver.call(LyricDataProvider.URI, "lyric", null, null) ?: return
            val json = bundle.getString("n") ?: return
            val jo = JSONObject(json)
            val line = jo.optString("l", "").trim()
                .ifBlank { jo.optString("s", "").trim() }
            if (line.isNotBlank()) {
                lyricView?.text = line
            }
            val title = jo.optString("title", "").trim()
            val artist = jo.optString("artist", "").trim()
            if (title.isNotBlank() && titleView?.text.isNullOrBlank()) titleView?.text = title
            if (artist.isNotBlank() && artistView?.text.isNullOrBlank()) artistView?.text = artist
        } catch (_: Throwable) {
        }
    }

    private fun preferredController(): MediaController? =
        MediaSessionAccess.getActiveControllers(this).firstOrNull()

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
