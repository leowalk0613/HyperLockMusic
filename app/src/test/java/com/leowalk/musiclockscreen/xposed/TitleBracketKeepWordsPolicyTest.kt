package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBracketKeepWordsPolicyTest {

    @Test
    fun sharedAcrossModes_noMagazineSplit() {
        assertTrue(TitleBracketKeepWordsPolicy.isSharedAcrossModes())
        // 与 DualMode 前缀策略对照：词库键不加 magazine_
        assertEquals(
            "title_bracket_keep_words",
            DualModeSettingsPolicy.prefKey("title_bracket_keep_words", false),
        )
        assertEquals(
            "magazine_title_bracket_keep_words",
            DualModeSettingsPolicy.prefKey("title_bracket_keep_words", true),
        )
        // 产品约定：不使用 magazine_ 分档，两边读写同一 prefs 键
        assertTrue(TitleBracketKeepWordsPolicy.isSharedAcrossModes())
    }

    @Test
    fun defaults_matchIgnoreCase() {
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("LIVE"))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Live"))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("live"))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("inst"))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("INST"))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Instrumental"))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("instrumental"))
    }

    @Test
    fun nonKeep_words_notMatched() {
        assertFalse(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("现场版"))
        assertFalse(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Remix"))
        assertFalse(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("instrumental mix"))
    }

    @Test
    fun customWords_exactMatchIgnoreCase() {
        assertTrue(
            TitleBracketKeepWordsPolicy.shouldKeepBracketContent(
                "Demo",
                listOf("demo"),
            ),
        )
        assertFalse(
            TitleBracketKeepWordsPolicy.shouldKeepBracketContent(
                "Demo Version",
                listOf("demo"),
            ),
        )
    }

    @Test
    fun parseAndSerialize_roundTrip() {
        val parsed = TitleBracketKeepWordsPolicy.parseCustomWords("  Demo , foo\nBAR;demo ")
        assertEquals(listOf("Demo", "foo", "BAR"), parsed)
        assertEquals(
            "Demo,foo,BAR",
            TitleBracketKeepWordsPolicy.serializeCustomWords(parsed),
        )
    }

    @Test
    fun tryAdd_skipsDefaultAndDuplicates() {
        val (addedDefault, _) = TitleBracketKeepWordsPolicy.tryAddCustomWord(emptyList(), "live")
        assertFalse(addedDefault)
        val (added, next) = TitleBracketKeepWordsPolicy.tryAddCustomWord(emptyList(), "Demo")
        assertTrue(added)
        assertEquals(listOf("Demo"), next)
        val (dup, next2) = TitleBracketKeepWordsPolicy.tryAddCustomWord(next, "demo")
        assertFalse(dup)
        assertEquals(listOf("Demo"), next2)
    }
}
