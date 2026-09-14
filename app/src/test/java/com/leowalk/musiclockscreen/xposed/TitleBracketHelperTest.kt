package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleBracketHelperTest {

    private val defaultsOn = TitleBracketKeepWordsPolicy.enabledWords("")

    @Test
    fun keepLive_staysInMain_unaffectedBySplit() {
        val (main, sub) = TitleBracketHelper.splitBrackets("Song Name (LIVE)", defaultsOn)
        assertEquals("Song Name (LIVE)", main)
        assertEquals("", sub)
    }

    @Test
    fun keepLive_fullwidthAndSquareBrackets() {
        val (a, asub) = TitleBracketHelper.splitBrackets("曲（LIVE）", defaultsOn)
        assertEquals("曲（LIVE）", a)
        assertEquals("", asub)
        val (b, bsub) = TitleBracketHelper.splitBrackets("曲【LIVE】名", defaultsOn)
        assertEquals("曲【LIVE】名", b)
        assertEquals("", bsub)
    }

    @Test
    fun keepInst_withTrailingDot() {
        val (main, sub) = TitleBracketHelper.splitBrackets("Track (inst.)", defaultsOn)
        assertEquals("Track (inst.)", main)
        assertEquals("", sub)
    }

    @Test
    fun disabledLive_goesToSubtitle() {
        val stored = TitleBracketKeepWordsPolicy.setEnabled("", "LIVE", false)
        val (main, sub) = TitleBracketHelper.splitBrackets(
            "Song Name (LIVE)",
            TitleBracketKeepWordsPolicy.enabledWords(stored),
        )
        assertEquals("Song Name", main)
        assertEquals("LIVE", sub)
    }

    @Test
    fun keepInstrumental_andSplitRemix() {
        val (main, sub) = TitleBracketHelper.splitBrackets(
            "Track (Instrumental) (Remix)",
            defaultsOn,
        )
        assertEquals("Track (Instrumental)", main)
        assertEquals("Remix", sub)
    }

    @Test
    fun chineseBracket_splitAsSubtitle() {
        val (main, sub) = TitleBracketHelper.splitBrackets("歌名（现场版）", defaultsOn)
        assertEquals("歌名", main)
        assertEquals("现场版", sub)
    }

    @Test
    fun customKeepWord_sameAsBuiltin() {
        val stored = TitleBracketKeepWordsPolicy.tryAddCustom("", "Demo").second
        val (main, sub) = TitleBracketHelper.splitBrackets(
            "A (Demo) (现场版)",
            TitleBracketKeepWordsPolicy.enabledWords(stored),
        )
        assertEquals("A (Demo)", main)
        assertEquals("现场版", sub)
    }

    @Test
    fun hideShrinkLine_modesKeepEnabledWordsInMain() {
        val raw = "曲名 (LIVE)（现场版）"
        val (main, sub) = TitleBracketHelper.splitBrackets(raw, defaultsOn)
        assertEquals("曲名 (LIVE)", main)
        assertEquals("现场版", sub)

        val hide = MagazinePageChromePolicy.resolveTitleDisplay(raw, "hide", defaultsOn)
        assertEquals("曲名 (LIVE)", hide.first)
        assertEquals("", hide.second)

        val shrink = MagazinePageChromePolicy.resolveTitleDisplay(raw, "shrink", defaultsOn)
        assertEquals("曲名 (LIVE)", shrink.first)
        assertEquals("现场版", shrink.second)

        val line = MagazinePageChromePolicy.resolveTitleDisplay(raw, "line", defaultsOn)
        assertEquals("曲名 (LIVE)", line.first)
        assertEquals("现场版", line.second)
        assertTrue(line.third)

        // 仅免处理词：三种模式都不得拆掉括号
        val onlyKeep = "歌名（LIVE）"
        assertEquals(onlyKeep, MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "hide", defaultsOn).first)
        assertEquals(onlyKeep, MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "shrink", defaultsOn).first)
        assertEquals(onlyKeep, MagazinePageChromePolicy.resolveTitleDisplay(onlyKeep, "line", defaultsOn).first)
    }

    @Test
    fun emptyTitle() {
        assertEquals("" to "", TitleBracketHelper.splitBrackets(null))
        assertEquals("" to "", TitleBracketHelper.splitBrackets(""))
    }

    private fun assertTrue(v: Boolean) {
        org.junit.Assert.assertTrue(v)
    }
}
