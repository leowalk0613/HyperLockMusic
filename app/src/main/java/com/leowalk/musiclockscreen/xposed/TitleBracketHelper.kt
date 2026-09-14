package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号拆分：括号内容抽到副标题，主标题去掉括号段。
 * 由内向外剥嵌套，并清掉剥离后残留的孤立括号字符。
 */
object TitleBracketHelper {

    /**
     * 只匹配「最内层」一对：内容里不再含开括号，避免 `（名（副））` 留下孤立 `）`。
     */
    private val INNERMOST_BRACKET_RE =
        Regex("[（(【\\[]([^（）()【\\]】]*)[）)\\】\\]]")

    /** 剥离后仍可能残留的括号字形。 */
    private val STRAY_BRACKET_CHARS = Regex("[（）()【】\\[\\]]")

    fun splitBrackets(title: String?): Pair<String, String> {
        if (title.isNullOrEmpty()) return "" to ""
        var working = title.replace(Regex("\\s+"), " ").trim()
        if (working.isEmpty()) return "" to ""
        val subParts = ArrayList<String>(4)
        // 限制迭代，防止异常输入死循环
        repeat(32) {
            val match = INNERMOST_BRACKET_RE.find(working) ?: return@repeat
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) subParts.add(inner)
            working = (working.substring(0, match.range.first) +
                working.substring(match.range.last + 1))
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        working = STRAY_BRACKET_CHARS.replace(working, "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return working to subParts.joinToString(" ")
    }
}
