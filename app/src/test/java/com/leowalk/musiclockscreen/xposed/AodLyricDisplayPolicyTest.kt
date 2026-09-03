package com.leowalk.musiclockscreen.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodLyricDisplayPolicyTest {

    @Test
    fun aodRefreshMode_whenScreenOffOnKeyguard() {
        assertTrue(
            AodLyricDisplayPolicy.isAodLyricRefreshMode(
                screenInteractive = false,
                onKeyguard = true,
            )
        )
    }

    @Test
    fun playbackOk_whenPlayingRegardlessOfScreen() {
        assertTrue(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = true,
                screenInteractive = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = false,
            )
        )
    }

    @Test
    fun playbackOk_onAodWhenMediaListenerReportsActive() {
        assertTrue(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = false,
                screenInteractive = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = true,
            )
        )
    }

    @Test
    fun playbackOk_onAodWhenLyricContentAlreadyLoaded() {
        assertTrue(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = false,
                screenInteractive = false,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = false,
                hasLyricData = true,
                hasDisplayableText = true,
            )
        )
    }

    @Test
    fun playbackNotOk_whenPausedOnInteractiveLockscreen() {
        assertFalse(
            AodLyricDisplayPolicy.isPlaybackOkForLyricDisplay(
                isPlaying = false,
                screenInteractive = true,
                musicLockscreenActive = true,
                onKeyguard = true,
                mediaPlaybackActive = false,
            )
        )
    }

    @Test
    fun lyricContentChanged_whenCurrentLineDiffers() {
        val current = AodLyricDisplayPolicy.LyricSnapshotFields(l = "new line")
        val previous = AodLyricDisplayPolicy.LyricSnapshotFields(l = "old line")
        assertTrue(AodLyricDisplayPolicy.lyricContentChangedFromFields(current, previous))
    }

    @Test
    fun lyricContentNotChanged_whenSnapshotMatches() {
        val fields = AodLyricDisplayPolicy.LyricSnapshotFields(l = "same line", title = "Song A")
        assertFalse(AodLyricDisplayPolicy.lyricContentChangedFromFields(fields, fields))
    }

    @Test
    fun lyricContentChanged_whenLightLineAdvances() {
        assertTrue(
            AodLyricDisplayPolicy.lyricContentChangedFromFields(
                AodLyricDisplayPolicy.LyricSnapshotFields(l = "line2"),
                AodLyricDisplayPolicy.LyricSnapshotFields(l = "line1"),
            )
        )
    }

    @Test
    fun sameSongPayload_whenTitlesMatch() {
        assertTrue(AodLyricDisplayPolicy.isSameSongLyricPayload("Song A", "Song A"))
    }

    @Test
    fun differentSongPayload_whenTitleChanges() {
        assertFalse(AodLyricDisplayPolicy.isSameSongLyricPayload("Song A", "Song B"))
    }

    @Test
    fun resetLyricOnlyOnRealTrackKeySwitch() {
        assertFalse(AodLyricDisplayPolicy.shouldResetLyricForTrackKeyChange(null, "k1"))
        assertFalse(AodLyricDisplayPolicy.shouldResetLyricForTrackKeyChange("k1", "k1"))
        assertTrue(AodLyricDisplayPolicy.shouldResetLyricForTrackKeyChange("k1", "k2"))
    }

    @Test
    fun lightLyricDisplay_treatsNonBlankSAsTranslation() {
        val display = AodLyricDisplayPolicy.resolveLightLyricDisplay("原文", "翻译")
        assertTrue(display.isTranslation)
        assertTrue(display.hasSecond)
        assertEquals("翻译", display.second)
    }

    @Test
    fun lightLyricDisplay_sMatchingNextLineIsNotTranslation() {
        val display = AodLyricDisplayPolicy.resolveLightLyricDisplay(
            l = "还有什么人在未来",
            s = "既然未知是唯一的期待",
            nextLineText = "既然未知是唯一的期待",
        )
        assertFalse(display.isTranslation)
        assertEquals("既然未知是唯一的期待", display.second)
    }

    @Test
    fun lightLyricDisplay_songWithoutTranslationNeverTreatsSAsTranslation() {
        val display = AodLyricDisplayPolicy.resolveLightLyricDisplay(
            l = "归零",
            s = "这世界从来没有如果",
            songHasTranslation = false,
        )
        assertFalse(display.isTranslation)
        val swapped = AodLyricDisplayPolicy.applyLyricSwap(
            rawMain = display.main,
            rawSecond = display.second,
            hasSecond = display.hasSecond,
            isTranslation = display.isTranslation,
            swapEnabled = true,
        )
        assertEquals("归零", swapped.main)
        assertEquals("这世界从来没有如果", swapped.second)
    }

    @Test
    fun cachedLineDisplay_usesLightTranslationWhenLineRIsEmpty() {
        val display = AodLyricDisplayPolicy.resolveCachedLineDisplay(
            currentText = "第一句原文",
            lineTranslation = "",
            nextLineText = "第二句原文",
            lightMain = "第一句原文",
            lightTranslation = "第一句翻译",
            immersiveLyric = false,
        )
        assertTrue(display.isTranslation)
        assertEquals("第一句翻译", display.second)
    }

    @Test
    fun cachedLineDisplay_doesNotTreatNextLineInSAsTranslation() {
        val display = AodLyricDisplayPolicy.resolveCachedLineDisplay(
            currentText = "还有什么人在未来",
            lineTranslation = "",
            nextLineText = "既然未知是唯一的期待",
            lightMain = "还有什么人在未来",
            lightTranslation = "既然未知是唯一的期待",
            immersiveLyric = false,
            songHasTranslation = false,
        )
        assertFalse(display.isTranslation)
        assertEquals("既然未知是唯一的期待", display.second)
        val swapped = AodLyricDisplayPolicy.applyLyricSwap(
            rawMain = display.main,
            rawSecond = display.second,
            hasSecond = display.hasSecond,
            isTranslation = display.isTranslation,
            swapEnabled = true,
        )
        assertEquals("还有什么人在未来", swapped.main)
        assertEquals("既然未知是唯一的期待", swapped.second)
    }

    @Test
    fun songHasTranslationFromRs_falseWhenAllEmpty() {
        assertFalse(AodLyricDisplayPolicy.songHasTranslationFromRs(listOf("", "  ", "")))
    }

    @Test
    fun songHasTranslationFromRs_trueWhenAnyPresent() {
        assertTrue(AodLyricDisplayPolicy.songHasTranslationFromRs(listOf("", "trans")))
    }

    @Test
    fun lyricSnapshotEmpty_forBlankOrBraces() {
        assertTrue(AodLyricDisplayPolicy.isLyricSnapshotEmpty("{}"))
        assertTrue(AodLyricDisplayPolicy.isLyricSnapshotEmpty("  "))
        assertFalse(AodLyricDisplayPolicy.isLyricSnapshotEmpty("""{"l":"hi"}"""))
    }

    @Test
    fun shouldReloadLyricFd_whenSnapshotEmptyEvenIfVersionUnchanged() {
        // 重启后误记 version、内容未写入：必须允许同 version 重拉
        assertTrue(
            AodLyricDisplayPolicy.shouldReloadLyricFd(
                oldVLyricFd = 10,
                newVLyricFd = 10,
                snapshotEmpty = true,
            )
        )
        assertFalse(
            AodLyricDisplayPolicy.shouldReloadLyricFd(
                oldVLyricFd = 10,
                newVLyricFd = 10,
                snapshotEmpty = false,
            )
        )
        assertTrue(
            AodLyricDisplayPolicy.shouldReloadLyricFd(
                oldVLyricFd = 9,
                newVLyricFd = 10,
                snapshotEmpty = false,
            )
        )
    }

    @Test
    fun shouldReloadLightLyric_whenSnapshotEmpty() {
        assertTrue(
            AodLyricDisplayPolicy.shouldReloadLightLyric(
                oldVLyric = 5,
                newVLyric = 5,
                snapshotEmpty = true,
                fdVersionUnchangedOrFdFailed = true,
            )
        )
        assertFalse(
            AodLyricDisplayPolicy.shouldReloadLightLyric(
                oldVLyric = 5,
                newVLyric = 5,
                snapshotEmpty = false,
                fdVersionUnchangedOrFdFailed = true,
            )
        )
    }

    @Test
    fun cachedLineDisplay_prefersLineTranslationOverLightS() {
        val display = AodLyricDisplayPolicy.resolveCachedLineDisplay(
            currentText = "line",
            lineTranslation = "from-r",
            nextLineText = "next",
            lightMain = "line",
            lightTranslation = "from-s",
            immersiveLyric = false,
        )
        assertEquals("from-r", display.second)
    }

    @Test
    fun cachedLineDisplay_ignoresStaleLightWhenMainDiffers() {
        val display = AodLyricDisplayPolicy.resolveCachedLineDisplay(
            currentText = "current line",
            lineTranslation = "",
            nextLineText = "next line",
            lightMain = "other line",
            lightTranslation = "stale trans",
            immersiveLyric = false,
        )
        assertFalse(display.isTranslation)
        assertEquals("next line", display.second)
    }

    @Test
    fun applyLyricSwap_swapsWhenTranslationPresent() {
        val swapped = AodLyricDisplayPolicy.applyLyricSwap(
            rawMain = "原文",
            rawSecond = "翻译",
            hasSecond = true,
            isTranslation = true,
            swapEnabled = true,
        )
        assertEquals("翻译", swapped.main)
        assertEquals("原文", swapped.second)
    }

    @Test
    fun applyLyricSwap_skipsWhenSecondIsNextLine() {
        val normal = AodLyricDisplayPolicy.applyLyricSwap(
            rawMain = "line1",
            rawSecond = "line2",
            hasSecond = true,
            isTranslation = false,
            swapEnabled = true,
        )
        assertEquals("line1", normal.main)
        assertEquals("line2", normal.second)
    }

    @Test
    fun providerLyricStale_whenTitleDiffersFromMedia() {
        assertTrue(
            AodLyricDisplayPolicy.isProviderLyricStaleForMedia("Song Old", "Song New")
        )
    }

    @Test
    fun providerLyricNotStale_whenTitleMatchesMedia() {
        assertFalse(AodLyricDisplayPolicy.isProviderLyricStaleForMedia("Song A", "Song A"))
    }

    @Test
    fun providerLyricNotStale_whenEitherTitleBlank() {
        assertFalse(AodLyricDisplayPolicy.isProviderLyricStaleForMedia("Song A", ""))
    }
}
