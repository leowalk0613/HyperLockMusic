package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleBracketHelperTest {

    @Test
    fun asciiAndFullwidthBrackets_splitToSubtitle() {
        assertEquals(
            "Song Name" to "LIVE",
            TitleBracketHelper.splitBrackets("Song Name (LIVE)"),
        )
        assertEquals(
            "曲" to "Instrumental",
            TitleBracketHelper.splitBrackets("曲（Instrumental）"),
        )
        assertEquals(
            "A" to "LIVE inst",
            TitleBracketHelper.splitBrackets("A (LIVE) (inst)"),
        )
    }

    @Test
    fun chineseBracket_splitAsSubtitle() {
        val (main, sub) = TitleBracketHelper.splitBrackets("歌名（现场版）")
        assertEquals("歌名", main)
        assertEquals("现场版", sub)
    }

    @Test
    fun squareBrackets_split() {
        assertEquals(
            "Song" to "TV Size",
            TitleBracketHelper.splitBrackets("Song [TV Size]"),
        )
    }

    @Test
    fun nestedBrackets_leaveNoStrayClose() {
        // 旧正则会把「名（副」整段抠走，主标题残留孤立「）」
        assertEquals(
            "歌" to "副 名",
            TitleBracketHelper.splitBrackets("歌（名（副））"),
        )
        assertEquals(
            "A" to "C B",
            TitleBracketHelper.splitBrackets("A (B (C))"),
        )
    }

    @Test
    fun emptyTitle() {
        assertEquals("" to "", TitleBracketHelper.splitBrackets(null))
        assertEquals("" to "", TitleBracketHelper.splitBrackets(""))
    }
}
