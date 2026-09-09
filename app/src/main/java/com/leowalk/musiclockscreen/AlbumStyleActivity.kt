package com.leowalk.musiclockscreen

import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

/** 专辑封面：普通模式改锁屏样式；画报模式只改右划页样式（锁屏本体不动）。 */
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
                        "当前为「画报模式」：下面样式只作用于锁屏右划进入的自建页，" +
                            "不会改锁屏本体的媒体控件 / 通知 / 勿扰，也不会往锁屏烘焙大专辑。",
                    ),
                ),
            )
        }

        val card = M3.cardContent(this)

        card.addView(
            sectionLabel(
                if (ModuleConfig.isMagazineMode) "画报页封面样式（二选一）" else "封面样式（普通模式二选一）",
            ),
        )
        val styleIndex = when (
            if (ModuleConfig.isMagazineMode) ModuleConfig.magazinePageChrome else ModuleConfig.lockscreenChrome
        ) {
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
            val chrome = if (index == 1) {
                ModuleConfig.CHROME_IMMERSIVE
            } else {
                ModuleConfig.CHROME_BIG_ALBUM
            }
            if (ModuleConfig.isMagazineMode) {
                ModuleConfig.applyMagazinePageChrome(chrome)
                ModuleConfig.push(this)
                refreshModeUi()
                return@segmentGroup
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
            this, "专辑图大小", 20f, 90f, ModuleConfig.editAlbumSize.coerceIn(20f, 90f),
            { "${it.toInt()}% 屏宽" }
        ) { v ->
            ModuleConfig.editAlbumSize = v
            ModuleConfig.push(this)
        })
        bigAlbumOnlyBlock!!.addView(M3.sliderRow(
            this, "专辑图圆角", 0f, 60f, ModuleConfig.editAlbumCorner.coerceIn(0f, 60f),
            { "${it.toInt()} dp" }
        ) { v ->
            ModuleConfig.editAlbumCorner = v
            ModuleConfig.push(this)
        })
        bigAlbumOnlyBlock!!.addView(M3.sliderRow(
            this, "底边位置", 10f, 95f, migrateAlbumAnchor(ModuleConfig.editAlbumAnchorY).coerceIn(10f, 95f),
            { "${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.editAlbumAnchorY = v
            ModuleConfig.push(this)
        })
        card.addView(bigAlbumOnlyBlock)

        immersiveOnlyBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        immersiveOnlyBlock!!.addView(sectionLabel("沉浸封面专用"))
        immersiveOnlyBlock!!.addView(M3.sliderRow(
            this, "专辑位置", 20f, 55f,
            ModuleConfig.editImmersiveAlbumCenterY.coerceIn(20f, 55f),
            { "中心 ${it.toInt()}% 屏高" }
        ) { v ->
            ModuleConfig.editImmersiveAlbumCenterY = v
            ModuleConfig.push(this)
        })
        immersiveOnlyBlock!!.addView(M3.switchRow(
            this, "上下暗角渐变",
            "壁纸顶部与底部暗色渐变，范围随专辑位置变化",
            ModuleConfig.editImmersiveAlbumEdgeGradient,
        ) { checked ->
            ModuleConfig.editImmersiveAlbumEdgeGradient = checked
            ModuleConfig.push(this)
        })
        card.addView(immersiveOnlyBlock)

        networkHdRow = M3.switchRow(
            this, "官方高清封面",
            "网易云 / QQ 音乐 / 小米音乐：识别当前曲目后拉取官方高清图替换前景大专辑；其他播放器不匹配",
            ModuleConfig.editAlbumNetworkHd
        ) { checked ->
            ModuleConfig.editAlbumNetworkHd = checked
            ModuleConfig.push(this)
        }
        card.addView(networkHdRow)

        list.addView(M3.card(this, card))
        list.addView(M3.card(this, M3.tipContent(this,
            if (ModuleConfig.isMagazineMode) {
                "画报模式绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                    "本页与「歌词样式」只改右划自建页，与普通锁屏档案相互独立。\n\n" +
                    "大专辑底边与沉浸封面位置互不共用。灰显项表示当前样式下不生效。"
            } else {
                "普通模式绑定：大专辑 ↔ 沉浸歌词；沉浸封面 ↔ 普通歌词（无背景）。\n" +
                    "与画报页档案相互独立。\n\n" +
                    "大专辑底边与沉浸封面位置互不共用。灰显项表示当前样式下不生效。\n\n" +
                    "「官方高清封面」主要用于大专辑；网易云 / QQ / 小米音乐识别曲目后拉取官方高清图。"
            })))

        refreshModeUi()
    }

    private fun refreshModeUi() {
        val chrome = if (ModuleConfig.isMagazineMode) {
            ModuleConfig.magazinePageChrome
        } else {
            ModuleConfig.lockscreenChrome
        }
        val immersive = chrome == ModuleConfig.CHROME_IMMERSIVE
        val bigAlbum = chrome == ModuleConfig.CHROME_BIG_ALBUM
        M3.setControlsEnabled(styleSegment, true)
        M3.setControlsEnabled(stylePreview, true)
        // 画报页大专辑/沉浸都要用官方高清；普通模式仍主要在大专辑生效
        M3.setControlsEnabled(networkHdRow, true)
        M3.setControlsEnabled(bigAlbumOnlyBlock, bigAlbum)
        M3.setControlsEnabled(immersiveOnlyBlock, immersive)
        modeHint?.text = when {
            ModuleConfig.isMagazineMode && immersive ->
                "画报页：沉浸封面。用「专辑位置」调竖直中心；不影响锁屏。"
            ModuleConfig.isMagazineMode ->
                "画报页：大专辑模糊壁纸。大小/圆角/底边作用于右划页；不影响锁屏。"
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
            ModuleConfig.editAlbumAnchorY = 55f
            ModuleConfig.push(this)
            return 55f
        }
        return raw.coerceIn(30f, 80f)
    }
}
