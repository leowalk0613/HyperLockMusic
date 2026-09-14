package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号拆分。
 *
 * 免处理规则（严格）：
 * - 仅当标题中**每一个**括号内容都是已开启的免处理整词时，整段标题不拆、不改；
 * - 一旦夹杂任何非免处理括号，**全部**括号都按普通逻辑拆出（免处理词也不再特殊保留）。
 */
object TitleBracketHelper {

    /** 半角/全角圆括号、方括号。 */
    internal val BRACKET_RE = Regex("[（(【\\[]([^）)\\】\\]]*)[）)\\】\\]]")

    fun splitBrackets(
        title: String?,
        keepWords: Collection<String> = emptyList(),
    ): Pair<String, String> {
        if (title.isNullOrEmpty()) return "" to ""
        val normalized = title.replace(Regex("\\s+"), " ").trim()
        if (TitleBracketKeepWordsPolicy.shouldPreserveTitleUnprocessed(normalized, keepWords)) {
            return normalized to ""
        }
        // 混杂或无免词：一律普通拆分
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
