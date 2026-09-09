package com.leowalk.musiclockscreen

import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

/** 专辑封面（普通模式）：大专辑 / 沉浸封面 二选一；画报模式请在主界面切换。 */
class AlbumStyleActivity : BaseScrollingActivity() {

    private var styleSegment: LinearLayout? = null
    private var stylePreview: LinearLayout? = null
    private var bigAlbumOnlyBlock: LinearLayout? = null
    private var immersiveOnlyBlock: LinearLayout? = null
    private var networkHdRow: LinearLayout? = null
    private var modeHint: TextView? = null

    override fun titleText() = "专辑封面"

    override fun buildContent(list: LinearLayout) {
        if (ModuleConfig.isMagazineMode) {
            list.addView(
                M3.card(
                    this,
                    M3.tipContent(
                        this,
                        "当前为「画报模式」。封面大专辑 / 沉浸样式仅在「普通模式」生效；" +
                            "请回主界面切回普通模式后再调这里。",
                    ),
                ),
            )
        }

        val card = M3.cardContent(this)

        card.addView(sectionLabel("封面样式（普通模式二选一）"))
        val styleIndex = when (ModuleConfig.lockscreenChrome) {
            ModuleConfig.CHROME_IMMERSIVE -> 1
            ModuleConfig.CHROME_MAGAZINE -> when (ModuleConfig.lastNormalChrome) {
                ModuleConfig.CHROME_IMMERSIVE -> 1
                else -> 0
            }
            else -> 0
        }
        styleSegment = M3.segmentGroup(
            this,
            listOf("大专辑", "沉浸封面"),
            styleIndex,
            2,
        ) { index ->
            if (ModuleConfig.isMagazineMode) {
                // 画报模式下只记下偏好，真正样式仍由主界面模式开关控制
                ModuleConfig.lastNormalChrome =
                    if (index == 1) ModuleConfig.CHROME_IMMERSIVE else ModuleConfig.CHROME_BIG_ALBUM
                ModuleConfig.push(this)
                refreshModeUi()
                return@segmentGroup
            }
            val chrome = if (index == 1) {
                ModuleConfig.CHROME_IMMERSIVE
            } else {
                ModuleConfig.CHROME_BIG_ALBUM
            }
            ModuleConfig.applyChromeStyle(chrome)
            ModuleConfig.push(this)
            refreshModeUi()
        }
        card.addView(styleSegment)
        stylePreview = M3.stylePreviewRow(
            this,
            listOf("大专辑", "沉浸封面"),
            intArrayOf(
                R.drawable.preview_album_big,
                R.drawable.preview_album_immersive,
            ),
        )
        card.addView(stylePreview)

        modeHint = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
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
        immersiveOnlyBlock!!.addView(M3.switchRow(
            this, "上下暗角渐变",
            "壁纸顶部与底部暗色渐变，范围随专辑位置变化",
            ModuleConfig.immersiveAlbumEdgeGradient,
        ) { checked ->
            ModuleConfig.immersiveAlbumEdgeGradient = checked
            ModuleConfig.push(this)
        })
        card.addView(immersiveOnlyBlock)

        networkHdRow = M3.switchRow(
            this, "官方高清封面",
            "网易云 / QQ 音乐 / 小米音乐：识别当前曲目后拉取官方高清图替换前景大专辑；其他播放器不匹配",
            ModuleConfig.albumNetworkHd
        ) { checked ->
            ModuleConfig.albumNetworkHd = checked
            ModuleConfig.push(this)
        }
        card.addView(networkHdRow)

        list.addView(M3.card(this, card))
        list.addView(M3.card(this, M3.tipContent(this,
            "绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                "「画报模式」在主界面切换：不改媒体控件 / 通知 / 勿扰，走杂志层 + 划入自建页。\n\n" +
                "大专辑底边与沉浸封面位置互不共用。灰显项表示当前样式下不生效。\n\n" +
                "「官方高清封面」在网易云音乐、QQ 音乐或小米音乐（QQ 曲库）播放时生效：" +
                "从媒体会话识别当前曲目后拉取官方高清图替换前景大专辑，" +
                "无需 hook 播放器；其他播放器不会去匹配这些封面；沉浸封面取色仍用系统封面。\n" +
                "相关数据归各平台所有，图像版权归原作者所有；仅供个人学习与本机显示，与网易云音乐、QQ 音乐、小米音乐官方无关。")))

        refreshModeUi()
    }

    private fun refreshModeUi() {
        val chrome = if (ModuleConfig.isMagazineMode) {
            ModuleConfig.lastNormalChrome
        } else {
            ModuleConfig.lockscreenChrome
        }
        val immersive = chrome == ModuleConfig.CHROME_IMMERSIVE
        val bigAlbum = chrome == ModuleConfig.CHROME_BIG_ALBUM
        val editable = !ModuleConfig.isMagazineMode
        M3.setControlsEnabled(styleSegment, true)
        M3.setControlsEnabled(stylePreview, editable)
        M3.setControlsEnabled(networkHdRow, editable && bigAlbum)
        M3.setControlsEnabled(bigAlbumOnlyBlock, editable && bigAlbum)
        M3.setControlsEnabled(immersiveOnlyBlock, editable && immersive)
        modeHint?.text = when {
            ModuleConfig.isMagazineMode ->
                "当前主界面为画报模式。此处选择会在切回普通模式后生效。"
            immersive -> "当前：沉浸封面。用「专辑位置」调竖直中心；大小/圆角/底边仅大专辑可用。"
            else -> "当前：大专辑。大小/圆角/底边作用于方形封面（沉浸歌词开启时也用大小与底边定歌词区块）。"
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, M3.CARD_DESC_SP)
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
