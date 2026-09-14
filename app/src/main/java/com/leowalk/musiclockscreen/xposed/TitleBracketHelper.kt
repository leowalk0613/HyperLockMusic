package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号拆分。
 * [keepWords] 为已开启的「免处理」词：匹配的括号段原样留在主标题，
 * 不受隐藏 / 缩小 / 分行影响。
 */
object TitleBracketHelper {

    private val BRACKET_RE = Regex("[（(]([^（）()]*)[）)]")

    fun splitBrackets(
        title: String?,
        keepWords: Collection<String> = emptyList(),
    ): Pair<String, String> {
        if (title.isNullOrEmpty()) return "" to ""
        val main = StringBuilder()
        val sub = StringBuilder()
        var last = 0
        for (match in BRACKET_RE.findAll(title)) {
            main.append(title.substring(last, match.range.first))
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty() &&
                TitleBracketKeepWordsPolicy.shouldKeepBracketContent(inner, keepWords)
            ) {
                main.append(match.value)
            } else if (inner.isNotEmpty()) {
                if (sub.isNotEmpty()) sub.append(' ')
                sub.append(inner)
            }
            last = match.range.last + 1
        }
        main.append(title.substring(last))
        return main.toString().replace(Regex("\\s+"), " ").trim() to sub.toString()
    }
}
