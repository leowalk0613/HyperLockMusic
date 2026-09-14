package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBracketKeepWordsPolicyTest {

    @Test
    fun sharedAcrossModes_noMagazineSplit() {
        assertTrue(TitleBracketKeepWordsPolicy.isSharedAcrossModes())
    }

    @Test
    fun emptyStored_defaultsAllEnabled() {
        val enabled = TitleBracketKeepWordsPolicy.enabledWords("")
        assertTrue(enabled.containsAll(listOf("LIVE", "inst", "Instrumental")))
        assertTrue(
            TitleBracketKeepWordsPolicy.shouldKeepBracketContent("live", enabled),
        )
    }

    @Test
    fun disableBuiltin_stopsKeep() {
        val stored = TitleBracketKeepWordsPolicy.setEnabled("", "LIVE", false)
        val enabled = TitleBracketKeepWordsPolicy.enabledWords(stored)
        assertFalse(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("LIVE", enabled))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("inst", enabled))
    }

    @Test
    fun customToggleAndRemove() {
        val (added, withDemo) = TitleBracketKeepWordsPolicy.tryAddCustom("", "Demo")
        assertTrue(added)
        var enabled = TitleBracketKeepWordsPolicy.enabledWords(withDemo)
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("demo", enabled))

        val off = TitleBracketKeepWordsPolicy.setEnabled(withDemo, "Demo", false)
        enabled = TitleBracketKeepWordsPolicy.enabledWords(off)
        assertFalse(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Demo", enabled))

        val removed = TitleBracketKeepWordsPolicy.removeCustom(off, "Demo")
        assertFalse(
            TitleBracketKeepWordsPolicy.resolveEntries(removed).any {
                TitleBracketKeepWordsPolicy.normalizeKey(it.word) == "demo"
            },
        )
    }

    @Test
    fun legacyCommaCustoms_defaultsStayOn() {
        val enabled = TitleBracketKeepWordsPolicy.enabledWords("Demo,Foo")
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("LIVE", enabled))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Demo", enabled))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Foo", enabled))
    }

    @Test
    fun serializeRoundTrip_preservesDisabledBuiltin() {
        val stored = "LIVE=0,inst=1,Instrumental=1,Remix=1"
        val again = TitleBracketKeepWordsPolicy.serializeEntries(
            TitleBracketKeepWordsPolicy.resolveEntries(stored),
        )
        val enabled = TitleBracketKeepWordsPolicy.enabledWords(again)
        assertFalse(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("LIVE", enabled))
        assertTrue(TitleBracketKeepWordsPolicy.shouldKeepBracketContent("Remix", enabled))
        assertEquals(
            listOf("inst", "Instrumental", "Remix").map {
                TitleBracketKeepWordsPolicy.normalizeKey(it)
            }.toSet(),
            enabled.map { TitleBracketKeepWordsPolicy.normalizeKey(it) }.toSet(),
        )
    }
}
