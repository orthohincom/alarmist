package com.orthohin.alarmist.mission.squats

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class SquatCounterTest {
    private val rate = 50 // SENSOR_DELAY_GAME is about 50 Hz.

    /**
     * Feeds [seconds] of samples at [rate] Hz: gravity [g] plus [vertical] (upward acceleration at time t, m/s²) on
     * the phone's y axis, with the phone held a little tilted. Returns how many squats counted.
     */
    private fun count(seconds: Double, g: Float = 9.81f, noise: Float = 0f, vertical: (Double) -> Double): Int {
        val counter = SquatCounter()
        val random = Random(1)
        return (0 until (seconds * rate).toInt()).count { i ->
            val t = i.toDouble() / rate
            val a = g + vertical(t).toFloat() + noise * (random.nextFloat() * 2 - 1)
            counter.onSample(0.2f * a, 0.97f * a, 0.1f * a, (t * 1e9).toLong())
        }
    }

    /**
     * [reps] squats of [period] s each, [depth] m down and back up, after [rest] s standing still (and [rest] s
     * after each one): the phone's height is depth·(1 − cos ωt)/2 below the start, so its upward acceleration is
     * −depth·ω²/2·cos ωt.
     */
    private fun squats(reps: Int, period: Double, depth: Double = 0.45, rest: Double = 1.0): (Double) -> Double {
        val omega = 2 * PI / period
        return { t ->
            val inCycle = (t - rest).mod(period + rest)
            if (t < rest || t - rest >= reps * (period + rest) || inCycle >= period) {
                0.0
            } else {
                -depth * omega * omega / 2 * cos(omega * inCycle)
            }
        }
    }

    @Test
    fun `a phone held still never counts`() {
        assertEquals(0, count(20.0, noise = 0.3f) { 0.0 })
    }

    @Test
    fun `squats count once each at any normal pace`() {
        for (period in listOf(1.2, 1.5, 2.0, 2.5, 3.0)) {
            assertEquals("period $period", 5, count(5 * (period + 1) + 2, vertical = squats(5, period)))
        }
    }

    @Test
    fun `squats without a pause between them count`() {
        assertEquals(6, count(6 * 2.0 + 3, vertical = squats(6, 2.0, rest = 0.0)))
    }

    @Test
    fun `a sensor with gravity a little off and some tremor still counts`() {
        assertEquals(4, count(15.0, g = 9.6f, noise = 0.3f, vertical = squats(4, 2.0)))
    }

    @Test
    fun `squats right after a hard shake count`() {
        // 1.2 s of shaking (30 and 0 m/s², as a clipped sensor reads it), 2 s still, then 4 squats.
        val squats = squats(4, 1.6, rest = 1.2)
        val shakeThenSquats = { t: Double ->
            when {
                t < 1.2 -> if ((t * 10).toInt() % 2 == 0) 20.2 else -9.81
                t < 3.2 -> 0.0
                else -> squats(t - 3.2)
            }
        }
        assertEquals(4, count(3.2 + 4 * 2.8 + 1, vertical = shakeThenSquats))
    }

    @Test
    fun `shallow knee bends don't count`() {
        assertEquals(0, count(16.0, vertical = squats(5, 2.0, depth = 0.15)))
    }

    @Test
    fun `half squats count`() {
        assertEquals(4, count(16.0, vertical = squats(4, 2.0, depth = 0.25)))
        assertEquals(4, count(20.0, vertical = squats(4, 3.0, depth = 0.35)))
    }

    @Test
    fun `walking doesn't count, slow or fast`() {
        // (steps per second, bounce in m/s²)
        for ((rate, bounce) in listOf(1.0 to 1.5, 1.3 to 2.0, 1.8 to 2.5, 2.2 to 3.5)) {
            assertEquals("walk $rate", 0, count(20.0, noise = 0.3f) { t -> bounce * sin(2 * PI * rate * t) })
        }
    }

    @Test
    fun `shaking doesn't count`() {
        for ((rate, force) in listOf(2.0 to 15.0, 3.0 to 20.0, 4.0 to 15.0)) {
            assertEquals("shake $rate", 0, count(10.0) { t -> force * sin(2 * PI * rate * t) })
        }
    }
}
