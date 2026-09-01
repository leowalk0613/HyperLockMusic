package com.leowalk.musiclockscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/** 样式效果图与设置项对应关系（资源名约定）。 */
class StylePreviewMappingTest {

    @Test
    fun albumStylePreview_resourceNamesMatchLabels() {
        val mapping = listOf(
            "大专辑" to "preview_album_big",
            "沉浸封面" to "preview_album_immersive",
        )
        assertEquals(2, mapping.size)
        assertEquals("preview_album_big", mapping[0].second)
        assertEquals("preview_album_immersive", mapping[1].second)
    }

    @Test
    fun lyricStylePreview_resourceNamesMatchLabels() {
        val mapping = listOf(
            "普通歌词" to "preview_lyric_normal",
            "沉浸歌词" to "preview_lyric_immersive",
        )
        assertEquals(2, mapping.size)
        assertEquals("preview_lyric_normal", mapping[0].second)
        assertEquals("preview_lyric_immersive", mapping[1].second)
    }
}
