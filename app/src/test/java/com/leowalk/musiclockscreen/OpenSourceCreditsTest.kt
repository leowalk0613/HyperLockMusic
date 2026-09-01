package com.leowalk.musiclockscreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceCreditsTest {

    @Test
    fun credits_excludeAuthorOtherApps() {
        val blob = OpenSourceCredits.entries.joinToString("\n") {
            "${it.name}\n${it.role}\n${it.url}"
        }.lowercase()
        assertFalse(blob.contains("lyricfocus"))
        assertFalse(blob.contains("aodchange"))
        assertFalse(blob.contains("aod change"))
    }

    @Test
    fun credits_coverKnownDependencies() {
        val names = OpenSourceCredits.entries.map { it.name }
        assertTrue(names.any { it.contains("LSPosed", ignoreCase = true) })
        assertTrue(names.any { it.contains("Material Components", ignoreCase = true) })
        assertTrue(names.any { it.contains("Material Color Utilities", ignoreCase = true) })
        assertTrue(names.any { it.contains("StackBlur", ignoreCase = true) })
        assertTrue(names.any { it.contains("AndroidX", ignoreCase = true) || it.contains("Jetpack", ignoreCase = true) })
        assertTrue(names.any { it.contains("HyperLyric", ignoreCase = true) })
    }

    @Test
    fun credits_includeHyperLyricAodMediaReference() {
        val hyper = OpenSourceCredits.entries.first { it.name.contains("HyperLyric", ignoreCase = true) }
        assertTrue(hyper.role.contains("折叠") || hyper.role.contains("媒体"))
        assertTrue(hyper.url.contains("limczhh/HyperLyric"))
    }

    @Test
    fun projectUrl_isPlainHttps() {
        assertTrue(OpenSourceCredits.PROJECT_REPO_URL.startsWith("https://"))
        assertFalse(OpenSourceCredits.aboutBody().contains("<a "))
    }
}
