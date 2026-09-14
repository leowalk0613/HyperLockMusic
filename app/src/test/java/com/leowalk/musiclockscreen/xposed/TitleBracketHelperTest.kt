package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBracketHelperTest {

    private val defaultsOn = TitleBracketKeepWordsPolicy.enabledWords("")

    @Test
    fun soleKeepWord_preservesEntireTitle() {
        assertEquals(
            "Song Name (LIVE)" to "",
            TitleBracketHelper.splitBrackets("Song Name (LIVE)", defaultsOn),
        )
        assertEquals(
            "曲（Instrumental）" to "",
            TitleBracketHelper.splitBrackets("曲（Instrumental）", defaultsOn),
        )
        assertEquals(
            "A (LIVE) (inst)" to "",
            TitleBracketHelper.splitBrackets("A (LIVE) (inst)", defaultsOn),
        )
    }

    @Test
    fun mixedKeepAndOther_processesAllBrackets() {
        // 夹杂非免处理括号：LIVE 也不再特殊保留
        val (main, sub) = TitleBracketHelper.splitBrackets(
            "曲名 (LIVE)（现场版）",
            defaultsOn,
        )
        assertEquals("曲名", main)
        assertEquals("LIVE 现场版", sub)
        assertFalse(
            TitleBracketKeepWordsPolicy.shouldPreserveTitleUnprocessed(
                "曲名 (LIVE)（现场版）",
                defaultsOn,
            ),
        )
    }

    @Test
    fun keepWordMixedInsideSameBracket_notPreserved() {
        val (main, sub) = TitleBracketHelper.splitBrackets(
            "Song (LIVE Remix)",
            defaultsOn,
        )
        assertEquals("Song", main)
        assertEquals("LIVE Remix", sub)
    }

    @Test
    fun disabledLive_goesToSubtitle() {
        val stored = TitleBracketKeepWordsPolicy.setEnabled("", "LIVE", false)
        val words = TitleBracketKeepWordsPolicy.enabledWords(stored)
        assertFalse(TitleBracketKeepWordsPolicy.shouldPreserveTitleUnprocessed("Song (LIVE)", words))
        val (main, sub) = TitleBracketHelper.splitBrackets("Song (LIVE)", words)
        assertEquals("Song", main)
        assertEquals("LIVE", sub)
    }

    @Test
    fun chineseBracket_splitAsSubtitle() {
        val (main, sub) = TitleBracketHelper.splitBrackets("歌名（现场版）", defaultsOn)
        assertEquals("歌名", main)
        assertEquals("现场版", sub)
    }

    @Test
    fun modes_soleKeep_untouched() {
        val onlyKeep = "歌名（LIVE）"
        assertEquals(onlyKeep, MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "hide", defaultsOn).first)
        assertEquals(onlyKeep, MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "shrink", defaultsOn).first)
        assertEquals(onlyKeep, MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "line", defaultsOn).first)
        assertFalse(MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "line", defaultsOn).third)
    }

    @Test
    fun modes_mixed_processesKeepToo() {
        val raw = "曲名 (LIVE)（现场版）"
        val hide = MagazinePageChromePolicy.resolveTitleDisplay(raw, "hide", defaultsOn)
        assertEquals("曲名", hide.first)

        val line = MagazinePageChromePolicy.resolveTitleDisplay(raw, "line", defaultsOn)
        assertEquals("曲名", line.first)
        assertEquals("LIVE 现场版", line.second)
        assertTrue(line.third)

        val shrink = MagazinePageChromePolicy.resolveTitleDisplay(raw, "shrink", defaultsOn)
        assertEquals("曲名", shrink.first)
        assertEquals("LIVE 现场版", shrink.second)
    }

    @Test
    fun soleKeep_withPrefixSymbol_stillPreserved() {
        val title = "熱視線DAZZLING☆(Instrumental)"
        assertTrue(
            TitleBracketKeepWordsPolicy.shouldPreserveTitleUnprocessed(title, defaultsOn),
        )
        assertEquals(
            title to "",
            TitleBracketHelper.splitBrackets(title, defaultsOn),
        )
    }

    @Test
    fun emptyTitle() {
        assertEquals("" to "", TitleBracketHelper.splitBrackets(null))
        assertEquals("" to "", TitleBracketHelper.splitBrackets(""))
    }
}
