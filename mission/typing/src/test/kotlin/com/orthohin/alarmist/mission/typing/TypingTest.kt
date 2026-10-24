package com.orthohin.alarmist.mission.typing

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingTest {
    private val phrase = "Rise and shine, it's time"

    @Test
    fun `case, curly quotes and extra spaces don't matter`() {
        assertTrue(Typing.isDone(phrase, "rise and shine, it’s time"))
        assertTrue(Typing.isDone(phrase, "  RISE  and shine,   it's time "))
    }

    @Test
    fun `punctuation and spelling do`() {
        assertFalse(Typing.isDone(phrase, "rise and shine its time"))
        assertFalse(Typing.isDone(phrase, "rise and shine, it's tim"))
        assertFalse(Typing.isDone(phrase, "rise and shine, it's times"))
        assertFalse(Typing.isDone("", ""))
    }

    @Test
    fun `matched counts the right characters from the start`() {
        assertEquals(0, Typing.matched(phrase, ""))
        assertEquals(4, Typing.matched(phrase, "rise"))
        assertEquals(5, Typing.matched(phrase, "rise  "))
        assertEquals(6, Typing.matched(phrase, "rise abd"))
    }

    @Test
    fun `an unfinished phrase is not an error, a wrong character is`() {
        assertFalse(Typing.hasError(phrase, "rise an"))
        assertFalse(Typing.hasError(phrase, " rise "))
        assertTrue(Typing.hasError(phrase, "rise abd"))
        assertTrue(Typing.hasError(phrase, "x"))
    }

    @Test
    fun `clean trims and collapses spaces`() {
        assertEquals("a b c", Typing.clean("  a \n b\tc "))
    }

    @Test
    fun `pick shuffles without repeats unless the pool is too small`() {
        val pool = listOf("a", "b", "c", "d")
        val three = Typing.pick(pool, 3, Random(1))
        assertEquals(3, three.toSet().size)
        assertEquals(listOf("x", "x", "x"), Typing.pick(listOf(" x ", ""), 3, Random(1)))
        assertEquals(emptyList<String>(), Typing.pick(listOf(" "), 2, Random(1)))
        assertEquals(Typing.pick(pool, 4, Random(9)), Typing.pick(pool, 4, Random(9)))
    }
}
