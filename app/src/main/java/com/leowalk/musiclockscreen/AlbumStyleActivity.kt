package com.leowalk.musiclockscreen

import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

/** 专辑封面：大专辑 / 沉浸封面互斥；按模式灰显无效项。 */
class AlbumStyleActivity : BaseScrollingActivity() {

    private var styleSegment: LinearLayout? = null
    private var stylePreview: LinearLayout? = null
    private var bigAlbumOnlyBlock: LinearLayout? = null
    private var immersiveOnlyBlock: LinearLayout? = null
    private var networkHdRow: LinearLayout? = null
    private var modeHint: TextView? = null

    override fun titleText() = "专辑封面"

    override fun buildContent(list: LinearLayout) {
        val card = M3.cardContent(this)
        card.addView(M3.title(this, "封面显示"))

        card.addView(M3.switchRow(
            this, "显示封面", "关闭后锁屏不绘制大专辑 / 沉浸封面",
            ModuleConfig.showBigAlbum
        ) { checked ->
            ModuleConfig.showBigAlbum = checked
            ModuleConfig.push(this)
            refreshModeUi()
        })

        card.addView(sectionLabel("封面样式（二选一）"))
        val styleIndex = if (ModuleConfig.immersiveAlbum) 1 else 0
        styleSegment = M3.segmentGroup(this, listOf("大专辑", "沉浸封面"), styleIndex, 2) { index ->
            val immersive = index == 1
            ModuleConfig.showBigAlbum = true
            ModuleConfig.immersiveAlbum = immersive
            ModuleConfig.applyAlbumLyricBinding(immersive)
            ModuleConfig.push(this)
            refreshModeUi()
        }
        card.addView(styleSegment)
        stylePreview = M3.stylePreviewRow(
            this,
            listOf("大专辑", "沉浸封面"),
            intArrayOf(R.drawable.preview_album_big, R.drawable.preview_album_immersive),
        )
        card.addView(stylePreview)

        modeHint = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(
                M3.attrColor(
                    this@AlbumStyleActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt()
                )
            )
            setPadding(0, 0, 0, M3.dp(this@AlbumStyleActivity, 8f))
        }
        card.addView(modeHint)

        // 仅大专辑：大小 / 圆角 / 底边（沉浸封面不共用）
        bigAlbumOnlyBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bigAlbumOnlyBlock!!.addView(sectionLabel("大专辑专用"))
        bigAlbumOnlyBlock!!.addView(M3.sliderRow(
            this, "专辑图大小", 20f, 90f, ModuleConfig.albumSize.coerceIn(20f, 90f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.albumSize = v
            ModuleConfig.push(this)
        })
        bigAlbumOnlyBlock!!.addView(M3.sliderRow(
            this, "专辑图圆角", 0f, 60f, ModuleConfig.albumCorner.coerceIn(0f, 60f),
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.albumCorner = v
            ModuleConfig.push(this)
        })
        bigAlbumOnlyBlock!!.addView(M3.sliderRow(
            this, "底边位置", 30f, 80f, migrateAlbumAnchor(ModuleConfig.albumAnchorY),
            { "${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.albumAnchorY = v
            ModuleConfig.push(this)
        })
        card.addView(bigAlbumOnlyBlock)

        // 仅沉浸封面：竖直中心（与大专辑底边独立）
        immersiveOnlyBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        immersiveOnlyBlock!!.addView(sectionLabel("沉浸封面专用"))
        immersiveOnlyBlock!!.addView(M3.sliderRow(
            this, "专辑位置", 20f, 55f,
            ModuleConfig.immersiveAlbumCenterY.coerceIn(20f, 55f),
            { "中心 ${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.immersiveAlbumCenterY = v
            ModuleConfig.push(this)
        })
        card.addView(immersiveOnlyBlock)

        networkHdRow = M3.switchRow(
            this, "网易云高清封面",
            "仅在网易云音乐播放时生效：识别当前曲目后拉取官方高清图替换前景大专辑，无需 hook 网易云；其他播放器不匹配",
            ModuleConfig.albumNetworkHd
        ) { checked ->
            ModuleConfig.albumNetworkHd = checked
            ModuleConfig.push(this)
        }
        card.addView(networkHdRow)

        list.addView(M3.card(this, card))
        list.addView(M3.card(this, M3.tipContent(this,
            "绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                "大专辑底边与沉浸封面位置互不共用。灰显项表示当前样式下不生效。\n\n" +
                "「网易云高清封面」仅在网易云音乐播放时生效：从媒体会话识别当前曲目后拉取官方高清图替换前景大专辑，" +
                "无需 hook 网易云；其他播放器不会匹配网易云封面；沉浸封面取色仍用系统封面。\n" +
                "相关数据归平台所有，图像版权归原作者所有；仅供个人学习与本机显示，与网易云音乐官方无关。")))

        refreshModeUi()
    }

    private fun refreshModeUi() {
        val show = ModuleConfig.showBigAlbum
        val immersive = ModuleConfig.immersiveAlbum
        M3.setControlsEnabled(styleSegment, show)
        M3.setControlsEnabled(stylePreview, show)
        M3.setControlsEnabled(networkHdRow, show)
        M3.setControlsEnabled(bigAlbumOnlyBlock, show && !immersive)
        M3.setControlsEnabled(immersiveOnlyBlock, show && immersive)
        modeHint?.text = when {
            !show -> "封面已关闭，样式设置暂不生效。"
            immersive -> "当前：沉浸封面。用「专辑位置」调竖直中心；大小/圆角/底边仅大专辑可用。"
            else -> "当前：大专辑。大小/圆角/底边作用于方形封面（沉浸歌词开启时也用大小与底边定歌词区块）。"
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(
                M3.attrColor(
                    this@AlbumStyleActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0xFFCAC4D0.toInt()
                )
            )
            setPadding(0, M3.dp(this@AlbumStyleActivity, 8f), 0, M3.dp(this@AlbumStyleActivity, 4f))
        }
    }

    private fun migrateAlbumAnchor(raw: Float): Float {
        if (raw > 100f || raw < 0f) {
            ModuleConfig.albumAnchorY = 55f
            ModuleConfig.push(this)
            return 55f
        }
        return raw.coerceIn(30f, 80f)
    }
}
