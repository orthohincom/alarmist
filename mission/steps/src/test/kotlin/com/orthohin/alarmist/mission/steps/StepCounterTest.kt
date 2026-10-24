package com.orthohin.alarmist.mission.steps

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCounterTest {
    /** [seconds] of 50 Hz samples: gravity on z plus a vertical bounce of [amplitude] m/s² at [hz]. */
    private fun steps(hz: Double, amplitude: Double, seconds: Int = 10): Int {
        val counter = StepCounter()
        return (0 until seconds * 50).count { i ->
            val t = i / 50.0
            val z = 9.81 + amplitude * sin(2 * PI * hz * t)
            counter.onSample(0f, 0f, z.toFloat(), (t * 1e9).toLong())
        }
    }

    @Test
    fun `a still phone counts nothing`() {
        assertEquals(0, steps(hz = 2.0, amplitude = 0.0))
    }

    @Test
    fun `walking counts one step per bounce, even a gentle one with the phone in the hand`() {
        assertEquals(20, steps(hz = 2.0, amplitude = 3.0))
        assertEquals(15, steps(hz = 1.5, amplitude = 2.5))
        assertEquals(20, steps(hz = 2.0, amplitude = 1.0))
    }

    @Test
    fun `hand tremor and sensor noise don't count`() {
        assertEquals(0, steps(hz = 10.0, amplitude = 1.5))
        assertEquals(0, steps(hz = 2.0, amplitude = 0.5))
    }

    @Test
    fun `no more than about three steps a second`() {
        assertTrue(steps(hz = 8.0, amplitude = 15.0) <= 29)
    }
}
