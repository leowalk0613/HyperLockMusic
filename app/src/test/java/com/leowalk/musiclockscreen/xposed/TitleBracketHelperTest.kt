package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleBracketHelperTest {

    @Test
    fun keepLive_staysInMain() {
        val (main, sub) = TitleBracketHelper.splitBrackets("Song Name (LIVE)")
        assertEquals("Song Name (LIVE)", main)
        assertEquals("", sub)
    }

    @Test
    fun keepInstrumental_andSplitRemix() {
        val (main, sub) = TitleBracketHelper.splitBrackets("Track (Instrumental) (Remix)")
        assertEquals("Track (Instrumental)", main)
        assertEquals("Remix", sub)
    }

    @Test
    fun chineseBracket_splitAsSubtitle() {
        val (main, sub) = TitleBracketHelper.splitBrackets("歌名（现场版）")
        assertEquals("歌名", main)
        assertEquals("现场版", sub)
    }

    @Test
    fun customKeepWord() {
        val (main, sub) = TitleBracketHelper.splitBrackets(
            "A (Demo) (现场版)",
            keepWords = listOf("Demo"),
        )
        assertEquals("A (Demo)", main)
        assertEquals("现场版", sub)
    }

    @Test
    fun emptyTitle() {
        assertEquals("" to "", TitleBracketHelper.splitBrackets(null))
        assertEquals("" to "", TitleBracketHelper.splitBrackets(""))
    }
}
