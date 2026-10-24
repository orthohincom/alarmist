package com.orthohin.alarmist.mission.taporder

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapOrderTest {
    @Test
    fun `boards hold each number once, in distinct cells inside the grid`() {
        for (count in TapOrderConfig.COUNT) {
            repeat(50) { seed ->
                val board = tapOrderBoard(count, Random(seed))
                assertEquals((1..count).toList(), board.map { it.number })
                assertEquals(count, board.map { it.column to it.row }.toSet().size)
                assertTrue(board.all { it.column in 0 until TAP_ORDER_GRID && it.row in 0 until TAP_ORDER_GRID })
                assertTrue(board.all { it.dx >= 0f && it.dx < 1f && it.dy >= 0f && it.dy < 1f })
            }
        }
        assertEquals(tapOrderBoard(12, Random(7)), tapOrderBoard(12, Random(7)))
    }

    @Test
    fun `only the next number advances`() {
        var progress = TapProgress(3)
        progress = progress.tap(2)
        assertEquals(1, progress.next)
        progress = progress.tap(1).tap(1).tap(3)
        assertEquals(2, progress.next)
        progress = progress.tap(2).tap(3)
        assertTrue(progress.solved)
        assertEquals(progress, progress.tap(4))
    }
}
