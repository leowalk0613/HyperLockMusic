package com.leowalk.musiclockscreen

import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView

/** ???????????? */
class AboutActivity : BaseScrollingActivity() {

    private var versionTapCount = 0
    private var lastVersionTapMs = 0L

    override fun titleText() = "??"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "????"))

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        list.addView(M3.card(this, card.also { content ->
            val versionView = TextView(this).apply {
                text = "?? $version"
                setTextSize(15f)
                setTextColor(M3.attrColor(this@AboutActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFCAC4D0.toInt()))
                setOnClickListener { onVersionTapped() }
            }
            content.addView(versionView)
        }))

        list.addView(M3.card(this, M3.tipContent(this,
            "?? LSPosed ??????????? HyperOS ????????????????\n\n" +
                "?????? LSPosed ????????? SystemUI?????????????????????\n\n" +
                "??????????????????????????????")))
    }

    private fun onVersionTapped() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTapMs > 2000L) versionTapCount = 0
        lastVersionTapMs = now
        versionTapCount++
        if (versionTapCount >= 5) {
            versionTapCount = 0
            startActivity(Intent(this, WallpaperMakerActivity::class.java))
        }
    }
}
