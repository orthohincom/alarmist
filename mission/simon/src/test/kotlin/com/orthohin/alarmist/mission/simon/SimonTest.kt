package com.orthohin.alarmist.mission.simon

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimonTest {
    @Test
    fun `sequences have the right length and use every pad`() {
        repeat(100) { seed ->
            val sequence = simonSequence(12, Random(seed))
            assertEquals(12, sequence.size)
            assertTrue(sequence.all { it in 0 until SIMON_PADS })
        }
        assertEquals(setOf(0, 1, 2, 3), simonSequence(200, Random(1)).toSet())
        assertEquals(simonSequence(8, Random(5)), simonSequence(8, Random(5)))
    }

    @Test
    fun `the first pattern is three pads, or the whole sequence when shorter`() {
        assertEquals(3, simonStart(10))
        assertEquals(2, simonStart(2))
    }

    @Test
    fun `taps are checked against the shown part only`() {
        val sequence = listOf(2, 0, 3, 1, 1)
        assertEquals(SimonResult.PARTIAL, simonCheck(sequence, 3, emptyList()))
        assertEquals(SimonResult.PARTIAL, simonCheck(sequence, 3, listOf(2, 0)))
        assertEquals(SimonResult.CORRECT, simonCheck(sequence, 3, listOf(2, 0, 3)))
        assertEquals(SimonResult.WRONG, simonCheck(sequence, 3, listOf(2, 1)))
        assertEquals(SimonResult.WRONG, simonCheck(sequence, 3, listOf(2, 0, 3, 1)))
        assertEquals(SimonResult.CORRECT, simonCheck(sequence, 5, sequence))
    }

    @Test
    fun `stored keys round-trip and unknown ones fall back to normal`() {
        SimonSpeed.entries.forEach { assertEquals(it, SimonSpeed.fromKey(it.key)) }
        assertEquals(SimonSpeed.NORMAL, SimonSpeed.fromKey(null))
    }
}
