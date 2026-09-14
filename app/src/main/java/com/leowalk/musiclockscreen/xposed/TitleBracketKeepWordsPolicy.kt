package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号内容「不分离」词表：匹配时整段括号留在主标题，不进副标题。
 * 内置词大小写不敏感；自定义词同样按 trim + ignoreCase 精确匹配括号内全文。
 *
 * 普通锁屏与画报页**共用同一词库**（无 magazine_ 分档）。
 */
internal object TitleBracketKeepWordsPolicy {

    /** 内置默认（展示用）；匹配时忽略大小写。 */
    val DEFAULT_WORDS: List<String> = listOf(
        "LIVE",
        "inst",
        "Instrumental",
    )

    /** 词库在普通 / 画报设置页互通，不走 DualMode 前缀。 */
    fun isSharedAcrossModes(): Boolean = true

    fun parseCustomWords(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    fun serializeCustomWords(words: Collection<String>): String =
        words.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(",")

    fun normalizeKey(word: String): String = word.trim().lowercase()

    fun isDefaultWord(word: String): Boolean {
        val key = normalizeKey(word)
        if (key.isEmpty()) return false
        return DEFAULT_WORDS.any { normalizeKey(it) == key }
    }

    /** 合并内置 + 自定义后的匹配集合（小写）。 */
    fun keepKeys(customWords: Collection<String>): Set<String> {
        val keys = LinkedHashSet<String>()
        DEFAULT_WORDS.forEach { keys.add(normalizeKey(it)) }
        customWords.forEach { w ->
            val k = normalizeKey(w)
            if (k.isNotEmpty()) keys.add(k)
        }
        return keys
    }

    fun shouldKeepBracketContent(
        inner: String,
        customWords: Collection<String> = emptyList(),
    ): Boolean {
        val key = normalizeKey(inner)
        if (key.isEmpty()) return false
        return key in keepKeys(customWords)
    }

    /** 添加自定义词：与内置重复则忽略；返回是否写入成功。 */
    fun tryAddCustomWord(
        currentCustom: Collection<String>,
        candidate: String,
    ): Pair<Boolean, List<String>> {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return false to parseCustomWords(serializeCustomWords(currentCustom))
        if (isDefaultWord(trimmed)) {
            return false to parseCustomWords(serializeCustomWords(currentCustom))
        }
        val next = parseCustomWords(serializeCustomWords(currentCustom + trimmed))
        val added = next.any { normalizeKey(it) == normalizeKey(trimmed) } &&
            currentCustom.none { normalizeKey(it) == normalizeKey(trimmed) }
        return added to next
    }
}
