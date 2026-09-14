package com.leowalk.musiclockscreen.xposed

/**
 * 歌名括号「免处理」词库：开启的词在隐藏 / 缩小 / 分行下仍原样留在主标题括号内。
 * 内置与自定义均可开关；自定义可删除。普通锁屏与画报共用同一份存储。
 *
 * 存储格式：`LIVE=1,inst=1,Instrumental=1,Demo=0`
 * 旧版仅自定义逗号列表时：视为内置全开 + 所列自定义全开。
 */
object TitleBracketKeepWordsPolicy {

    data class Entry(
        val word: String,
        val enabled: Boolean,
        val builtin: Boolean,
    )

    val DEFAULT_WORDS: List<String> = listOf(
        "LIVE",
        "inst",
        "Instrumental",
    )

    fun isSharedAcrossModes(): Boolean = true

    /** 设置页入口摘要。 */
    fun entryPageSummary(entries: List<Entry>): String {
        val on = entries.count { it.enabled }
        return "已开 $on/${entries.size} · 普通与画报共用"
    }

    fun normalizeKey(word: String): String = word.trim().lowercase()

    fun isDefaultWord(word: String): Boolean {
        val key = normalizeKey(word)
        if (key.isEmpty()) return false
        return DEFAULT_WORDS.any { normalizeKey(it) == key }
    }

    /** 供拆分匹配：仅返回已开启的词。 */
    fun enabledWords(stored: String?): List<String> =
        resolveEntries(stored).filter { it.enabled }.map { it.word }

    fun shouldKeepBracketContent(inner: String, enabledWords: Collection<String>): Boolean {
        val key = normalizeKey(inner)
        if (key.isEmpty()) return false
        return enabledWords.any { normalizeKey(it) == key }
    }

    /**
     * UI / 持久化用完整列表：始终含全部内置项，后接自定义。
     */
    fun resolveEntries(stored: String?): List<Entry> {
        val parsed = parseStoredEntries(stored)
        val byKey = LinkedHashMap<String, Entry>()
        for (e in parsed) {
            val k = normalizeKey(e.word)
            if (k.isEmpty()) continue
            byKey.putIfAbsent(k, e)
        }
        val out = ArrayList<Entry>(DEFAULT_WORDS.size + byKey.size)
        for (d in DEFAULT_WORDS) {
            val k = normalizeKey(d)
            val existing = byKey.remove(k)
            out.add(Entry(word = d, enabled = existing?.enabled ?: true, builtin = true))
        }
        for (e in byKey.values) {
            if (isDefaultWord(e.word)) continue
            out.add(Entry(word = e.word, enabled = e.enabled, builtin = false))
        }
        return out
    }

    fun serializeEntries(entries: Collection<Entry>): String {
        val byKey = LinkedHashMap<String, Entry>()
        for (e in entries) {
            val w = e.word.trim()
            if (w.isEmpty()) continue
            val k = normalizeKey(w)
            byKey.putIfAbsent(k, e.copy(word = w))
        }
        val parts = ArrayList<String>()
        for (d in DEFAULT_WORDS) {
            val enabled = byKey.remove(normalizeKey(d))?.enabled ?: true
            parts.add("$d=${if (enabled) 1 else 0}")
        }
        for (e in byKey.values) {
            if (isDefaultWord(e.word)) continue
            parts.add("${e.word.trim()}=${if (e.enabled) 1 else 0}")
        }
        return parts.joinToString(",")
    }

    fun setEnabled(stored: String?, word: String, enabled: Boolean): String {
        val next = resolveEntries(stored).map { e ->
            if (normalizeKey(e.word) == normalizeKey(word)) e.copy(enabled = enabled) else e
        }
        return serializeEntries(next)
    }

    fun tryAddCustom(stored: String?, candidate: String): Pair<Boolean, String> {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return false to serializeEntries(resolveEntries(stored))
        if (isDefaultWord(trimmed)) return false to serializeEntries(resolveEntries(stored))
        val cur = resolveEntries(stored)
        if (cur.any { normalizeKey(it.word) == normalizeKey(trimmed) }) {
            return false to serializeEntries(cur)
        }
        return true to serializeEntries(cur + Entry(trimmed, enabled = true, builtin = false))
    }

    fun removeCustom(stored: String?, word: String): String {
        if (isDefaultWord(word)) return serializeEntries(resolveEntries(stored))
        val next = resolveEntries(stored).filterNot {
            !it.builtin && normalizeKey(it.word) == normalizeKey(word)
        }
        return serializeEntries(next)
    }

    /** @deprecated 兼容旧测试/调用：仅解析逗号词列表 */
    fun parseCustomWords(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        if (raw.contains('=')) {
            return resolveEntries(raw).filter { !it.builtin }.map { it.word }
        }
        return raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    fun serializeCustomWords(words: Collection<String>): String =
        serializeEntries(
            DEFAULT_WORDS.map { Entry(it, true, true) } +
                words.map { Entry(it.trim(), true, false) }
                    .filter { it.word.isNotEmpty() && !isDefaultWord(it.word) },
        )

    private fun parseStoredEntries(stored: String?): List<Entry> {
        if (stored.isNullOrBlank()) return emptyList()
        if (!stored.contains('=')) {
            // 旧版：仅自定义词，视为全开
            return stored.split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .map { Entry(it, enabled = true, builtin = isDefaultWord(it)) }
        }
        return stored.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val word = part.substring(0, eq).trim()
                if (word.isEmpty()) return@mapNotNull null
                val flag = part.substring(eq + 1).trim()
                val enabled = flag == "1" || flag.equals("true", ignoreCase = true)
                Entry(word, enabled, builtin = isDefaultWord(word))
            }
            .distinctBy { normalizeKey(it.word) }
    }
}
