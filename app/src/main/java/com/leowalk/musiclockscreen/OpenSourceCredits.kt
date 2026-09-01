package com.leowalk.musiclockscreen

/**
 * 本模块引用的第三方开源能力致谢文案（不含作者另两款软件）。
 */
object OpenSourceCredits {

    const val PROJECT_REPO_URL = "https://github.com/leowalk0613/HyperLockMusic"

    data class Entry(
        val name: String,
        val role: String,
        val url: String,
    )

    val entries: List<Entry> = listOf(
        Entry(
            name = "LSPosed / libxposed API",
            role = "提供 Xposed 模块运行时与挂钩能力，本模块以此注入 SystemUI / AOD",
            url = "https://github.com/LSPosed/LSPosed",
        ),
        Entry(
            name = "Android Jetpack（AndroidX）",
            role = "应用层基础组件（Core KTX、AppCompat 等）",
            url = "https://developer.android.com/jetpack",
        ),
        Entry(
            name = "Material Components for Android",
            role = "设置页 Material 3 控件与主题",
            url = "https://github.com/material-components/material-components-android",
        ),
        Entry(
            name = "Material Color Utilities",
            role = "沉浸封面等场景的 Monet / HCT 取色",
            url = "https://github.com/material-foundation/material-color-utilities",
        ),
        Entry(
            name = "StackBlur（Mario Klingemann）",
            role = "专辑壁纸模糊所用的高效盒式模糊算法",
            url = "http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html",
        ),
        Entry(
            name = "HyperLyric",
            role = "AOD「完整媒体控件」参考其禁用媒体卡片折叠的实现思路",
            url = "https://github.com/limczhh/HyperLyric",
        ),
    )

    fun aboutBody(): String {
        return buildString {
            append("感谢以下开源项目：")
            for (e in entries) {
                append("\n\n• ")
                append(e.name)
                append("\n")
                append(e.role)
                append("\n")
                append(e.url)
            }
        }
    }

    fun readmeMarkdown(): String {
        return buildString {
            appendLine("感谢以下开源项目：")
            appendLine()
            for (e in entries) {
                append("- **")
                append(e.name)
                append("** — ")
                append(e.role)
                append("（")
                append(e.url)
                appendLine("）")
            }
        }.trimEnd()
    }
}
