package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号拆分。
 * 已开启的免处理词：括号段原样留在主标题，不进入副标题，因而也不被隐藏 / 缩小 / 分行改写。
 */
object TitleBracketHelper {

    /** 半角/全角圆括号、方括号、书名号式方括号。 */
    private val BRACKET_RE = Regex("[（(【\\[]([^）)\\】\\]]*)[）)\\】\\]]")

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
                // 原样保留括号及内部文本，不进 sub
                main.append(title, match.range.first, match.range.last + 1)
            } else if (inner.isNotEmpty()) {
                if (sub.isNotEmpty()) sub.append(' ')
                sub.append(inner)
            } else {
                // 空括号也留在主标题，避免吞掉
                main.append(title, match.range.first, match.range.last + 1)
            }
            last = match.range.last + 1
        }
        main.append(title.substring(last))
        return main.toString().replace(Regex("\\s+"), " ").trim() to sub.toString()
    }
}
