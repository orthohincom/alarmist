package com.orthohin.alarmist.mission.memory

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTest {
    @Test
    fun `patterns have the right number of tiles inside the grid`() {
        for (difficulty in MemoryDifficulty.entries) {
            repeat(100) { seed ->
                val pattern = memoryPattern(difficulty, Random(seed))
                assertEquals(difficulty.tiles, pattern.size)
                assertTrue(pattern.all { it in 0 until difficulty.gridSize * difficulty.gridSize })
            }
        }
        assertEquals(memoryPattern(MemoryDifficulty.HARD, Random(3)), memoryPattern(MemoryDifficulty.HARD, Random(3)))
    }

    @Test
    fun `finding every tile solves the round`() {
        val round = MemoryRound.replay(setOf(1, 4, 7), listOf(7, 1, 4))
        assertTrue(round.solved)
        assertFalse(round.failed(0))
    }

    @Test
    fun `repeated taps count once`() {
        val round = MemoryRound.replay(setOf(1, 4, 7), listOf(1, 1, 2, 2))
        assertEquals(setOf(1), round.found)
        assertEquals(setOf(2), round.misses)
        assertFalse(round.solved)
    }

    @Test
    fun `failing takes more mistakes than allowed`() {
        val round = MemoryRound.replay(setOf(1, 4, 7), listOf(0, 2))
        assertFalse(round.failed(2))
        assertTrue(round.failed(1))
        assertTrue(MemoryRound.replay(setOf(1), listOf(0)).failed(0))
    }

    @Test
    fun `stored keys round-trip and unknown ones fall back to normal`() {
        MemoryDifficulty.entries.forEach { assertEquals(it, MemoryDifficulty.fromKey(it.key)) }
        assertEquals(MemoryDifficulty.NORMAL, MemoryDifficulty.fromKey("nope"))
    }
}
