package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号拆分：括号内容抽到副标题，主标题去掉括号段。
 */
object TitleBracketHelper {

    /** 半角/全角圆括号、方括号。 */
    private val BRACKET_RE = Regex("[（(【\\[]([^）)\\】\\]]*)[）)\\】\\]]")

    fun splitBrackets(title: String?): Pair<String, String> {
        if (title.isNullOrEmpty()) return "" to ""
        val normalized = title.replace(Regex("\\s+"), " ").trim()
        val main = StringBuilder()
        val sub = StringBuilder()
        var last = 0
        for (match in BRACKET_RE.findAll(normalized)) {
            main.append(normalized.substring(last, match.range.first))
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) {
                if (sub.isNotEmpty()) sub.append(' ')
                sub.append(inner)
            }
            last = match.range.last + 1
        }
        main.append(normalized.substring(last))
        return main.toString().replace(Regex("\\s+"), " ").trim() to sub.toString()
    }
}
