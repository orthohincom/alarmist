package com.orthohin.alarmist.core.sounds

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFileNamesTest {
    @Test
    fun `safe keeps ordinary names`() {
        assertEquals("Morning song.mp3", SoundFileNames.safe("Morning song.mp3"))
    }

    @Test
    fun `safe replaces path and reserved characters and can't hide the file`() {
        assertEquals("a_b_c_.ogg", SoundFileNames.safe("a/b\\c?.ogg"))
        assertEquals("hidden.ogg", SoundFileNames.safe("..hidden.ogg"))
    }

    @Test
    fun `safe names blank or missing names`() {
        assertEquals("sound", SoundFileNames.safe(null))
        assertEquals("sound", SoundFileNames.safe("  "))
        assertEquals("mp3", SoundFileNames.safe(".mp3"))
    }

    @Test
    fun `safe shortens long names and keeps the extension`() {
        val name = SoundFileNames.safe("x".repeat(200) + ".flac")
        assertEquals(80, name.length)
        assertEquals(".flac", name.takeLast(5))
    }

    @Test
    fun `unique numbers taken names`() {
        assertEquals("a.mp3", SoundFileNames.unique("a.mp3", setOf("b.mp3")))
        assertEquals("a (2).mp3", SoundFileNames.unique("a.mp3", setOf("a.mp3")))
        assertEquals("a (3).mp3", SoundFileNames.unique("a.mp3", setOf("a.mp3", "a (2).mp3")))
        assertEquals("song (2)", SoundFileNames.unique("song", setOf("song")))
    }

    @Test
    fun `title drops the extension`() {
        assertEquals("Morning song", SoundFileNames.title("Morning song.mp3"))
        assertEquals("song", SoundFileNames.title("song"))
    }
}
