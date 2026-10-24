package com.orthohin.alarmist.mission.steps

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GaitCheckTest {
    /** Hand motion (m/s², without gravity) and rotation (rad/s) at time t (s). */
    private class Signal(val accel: (Double) -> Triple<Double, Double, Double>, val gyro: (Double) -> Double = { 0.1 })

    private class Result(val credited: Int, val shaking: Boolean)

    /** Runs [seconds] of 50 Hz samples, with gravity on z; steps come from [stepTimes] or, if null, [StepCounter]. */
    private fun run(signal: Signal, seconds: Double = 10.0, stepTimes: List<Double>? = null): Result {
        val check = GaitCheck(hasGyro = true)
        val counter = StepCounter()
        val pending = stepTimes.orEmpty().toMutableList()
        var credited = 0
        var shaking = false
        for (i in 0 until (seconds * 50).toInt()) {
            val t = i / 50.0
            val nanos = (t * 1e9).toLong()
            val (x, y, z) = signal.accel(t)
            check.onAccel(x.toFloat(), y.toFloat(), (z + 9.81).toFloat(), nanos)
            val w = signal.gyro(t).toFloat()
            check.onGyro(w, 0f, 0f, nanos)
            val step = if (stepTimes == null) {
                counter.onSample(x.toFloat(), y.toFloat(), (z + 9.81).toFloat(), nanos)
            } else {
                pending.firstOrNull()?.takeIf { it <= t }?.let { pending.removeAt(0) } != null
            }
            if (step) {
                val verdict = check.onStep(nanos)
                credited += verdict.credited
                shaking = shaking || verdict.shaking != null
            }
        }
        return Result(credited, shaking)
    }

    private fun wave(hz: Double, amplitude: Double, phase: Double = 0.0) =
        { t: Double -> amplitude * sin(2 * PI * hz * t + phase) }

    /** Phone held in front while walking at [hz] steps a second: a bounce, some push forward, a slow sway. */
    private fun walking(hz: Double = 2.0) = Signal(
        accel = { t -> Triple(wave(hz, 1.2, 1.0)(t), wave(hz / 2, 0.6)(t), wave(hz, 3.0)(t)) },
        gyro = { t -> 0.4 + wave(hz / 2, 0.3)(t) }
    )

    private fun every(seconds: Double, from: Double = 0.25, to: Double = 10.0) =
        generateSequence(from) { it + seconds }.takeWhile { it < to }.toList()

    @Test
    fun `walking counts every step, from the step detector or the accelerometer`() {
        assertEquals(20, run(walking(), stepTimes = every(0.5)).credited)
        assertTrue(run(walking()).credited >= 18)
        assertTrue(run(walking(hz = 1.5)).credited >= 13)
    }

    @Test
    fun `the first steps are held back until the rhythm is steady`() {
        val result = run(walking(), seconds = 2.0, stepTimes = every(0.5, to = 2.0))
        assertEquals(GaitCheck.STREAK, result.credited)
        assertEquals(0, run(walking(), seconds = 1.6, stepTimes = every(0.5, to = 1.6)).credited)
    }

    @Test
    fun `shaking side to side doesn't count, even when the step detector reports steps`() {
        val sideways = Signal(accel = { t -> Triple(wave(4.0, 12.0)(t), 0.0, wave(4.0, 2.0)(t)) })
        val detector = run(sideways, stepTimes = every(0.5))
        assertEquals(0, detector.credited)
        assertTrue(detector.shaking)
        assertEquals(0, run(sideways).credited)
    }

    @Test
    fun `a twisting wrist doesn't count`() {
        val twist = Signal(accel = { t -> Triple(0.0, 0.0, wave(2.0, 3.0)(t)) }, gyro = { t -> wave(4.0, 6.0)(t) })
        assertEquals(0, run(twist, stepTimes = every(0.5)).credited)
    }

    @Test
    fun `violent up-and-down shaking doesn't count`() {
        val pump = Signal(accel = { t -> Triple(0.0, 0.0, wave(2.5, 25.0)(t)) })
        assertEquals(0, run(pump, stepTimes = every(0.4)).credited)
        assertEquals(0, run(pump).credited)
    }

    @Test
    fun `steps faster than about three a second don't count`() {
        val result = run(walking(hz = 5.0), stepTimes = every(0.2))
        assertEquals(0, result.credited)
        assertTrue(result.shaking)
    }

    @Test
    fun `an erratic rhythm doesn't count, a pause starts the streak again`() {
        val erratic = generateSequence(0.25 to true) { (t, short) -> t + (if (short) 0.35 else 0.9) to !short }
            .map { it.first }.takeWhile { it < 10 }.toList()
        assertEquals(0, run(walking(), stepTimes = erratic).credited)
        val paused = listOf(0.5, 1.0, 1.5, 4.0, 4.5, 5.0)
        assertEquals(0, run(walking(), stepTimes = paused).credited)
    }

    @Test
    fun `a missed or doubled step, or a change of pace, doesn't throw away steps`() {
        val missed = every(0.5).filter { it !in listOf(1.25, 3.75, 6.25) }
        assertEquals(missed.size, run(walking(), stepTimes = missed).credited)
        val doubled = (every(0.5) + listOf(2.27, 5.27)).sorted()
        assertEquals(20, run(walking(), stepTimes = doubled).credited)
        val slower = every(0.5, to = 5.0) + every(0.8, from = 5.05)
        assertEquals(slower.size, run(walking(), stepTimes = slower).credited)
    }

    @Test
    fun `a still phone counts nothing`() {
        assertEquals(0, run(Signal(accel = { Triple(0.0, 0.0, 0.0) })).credited)
    }
}
