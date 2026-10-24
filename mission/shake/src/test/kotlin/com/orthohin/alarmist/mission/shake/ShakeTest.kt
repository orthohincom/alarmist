package com.orthohin.alarmist.mission.shake

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeTest {
    private val ms = 1_000_000L

    /** Feeds (y acceleration, time in ms) samples with gravity on z; returns how many shakes counted. */
    private fun count(strength: ShakeStrength, vararg samples: Pair<Float, Long>): Int {
        val counter = ShakeCounter(strength.threshold)
        return samples.count { (y, t) -> counter.onSample(0f, y, 9.81f, t * ms) }
    }

    private val rest = 0f

    @Test
    fun `a phone lying still never counts`() {
        assertEquals(0, count(ShakeStrength.GENTLE, *Array(100) { rest to it * 20L }))
    }

    @Test
    fun `each swing counts once`() {
        assertEquals(3, count(ShakeStrength.NORMAL, 25f to 0, rest to 100, -25f to 200, rest to 300, 25f to 400))
    }

    @Test
    fun `holding the force without letting go counts once`() {
        assertEquals(1, count(ShakeStrength.NORMAL, 25f to 0, 25f to 500, 25f to 1000))
    }

    @Test
    fun `swings closer than the minimum gap count once`() {
        assertEquals(1, count(ShakeStrength.NORMAL, 25f to 0, rest to 40, 25f to 80))
        assertEquals(2, count(ShakeStrength.NORMAL, 25f to 0, rest to 80, 25f to 160))
    }

    @Test
    fun `strength sets how hard a shake must be`() {
        // |(0, 12, 9.81)| ≈ 15.5, so 5.7 away from gravity.
        val medium = arrayOf(12f to 0L, rest to 200L, 12f to 400L)
        assertEquals(2, count(ShakeStrength.GENTLE, *medium))
        assertEquals(0, count(ShakeStrength.NORMAL, *medium))
        assertEquals(0, count(ShakeStrength.STRONG, 20f to 0, rest to 200))
        assertEquals(1, count(ShakeStrength.STRONG, 30f to 0, rest to 200))
    }

    @Test
    fun `stored keys round-trip and unknown ones fall back to normal`() {
        ShakeStrength.entries.forEach { assertEquals(it, ShakeStrength.fromKey(it.key)) }
        assertEquals(ShakeStrength.NORMAL, ShakeStrength.fromKey(null))
    }
}
