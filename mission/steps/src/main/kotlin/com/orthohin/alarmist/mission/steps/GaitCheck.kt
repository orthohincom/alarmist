package com.orthohin.alarmist.mission.steps

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tells walking from shaking, so the steps mission can't be solved by shaking the phone. Feed it every
 * accelerometer sample (m/s², gravity included) and, if the phone has one, every gyroscope sample (rad/s); then ask
 * [onStep] about each step the step detector or [StepCounter] reports.
 *
 * Walking with the phone in hand: a gentle bounce along gravity, little rotation, a steady rhythm under ~3 steps a
 * second. Shaking: fast rotation, motion across gravity (side to side, back and forth), violent peaks, or a rhythm
 * that's too fast or erratic. Steps count only in a steady streak: the first [STREAK] are held back and credited
 * together, so a short burst of shaking never counts. A steady up-and-down arm pump can still pass; that takes
 * real effort. Once a streak is credited, a change of pace or a missed step doesn't end it; a pause or shaking does.
 */
class GaitCheck(private val hasGyro: Boolean) {
    /**
     * What [onStep] decided: steps to add now, why it looked like shaking (null if it didn't), why a step was dropped
     * or the streak started again without shaking, and the step's interval and motion, for the log.
     */
    data class Verdict(
        val credited: Int,
        val shaking: String? = null,
        val restart: String? = null,
        val stats: String = ""
    ) {
        /** One logcat line per step, to tune the thresholds from real walks. */
        fun describe(): String = when {
            shaking != null -> "Step rejected: $shaking ($stats)"
            restart != null -> "Step not counted: $restart ($stats)"
            credited > 0 -> "Step counted: +$credited ($stats)"
            else -> "Step held for the streak ($stats)"
        }
    }

    private val gravity = FloatArray(3)
    private var lastAccel: Long? = null
    private val motion = ArrayDeque<Motion>()
    private val rotation = ArrayDeque<Pair<Long, Float>>()

    private var lastStep: Long? = null
    private var lastInterval: Long? = null
    private var pending = 0
    private var fast = 0

    fun onAccel(x: Float, y: Float, z: Float, timestampNanos: Long) {
        val last = lastAccel
        lastAccel = timestampNanos
        if (last == null) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            return
        }
        // A slow low-pass filter follows gravity (the phone's tilt); what's left is the hand's motion.
        val dt = (timestampNanos - last).coerceAtLeast(0) / 1e9f
        val alpha = dt / (GRAVITY_SECONDS + dt)
        gravity[0] += alpha * (x - gravity[0])
        gravity[1] += alpha * (y - gravity[1])
        gravity[2] += alpha * (z - gravity[2])
        val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (g < 1f) return
        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val vertical = (lx * gravity[0] + ly * gravity[1] + lz * gravity[2]) / g
        val total = lx * lx + ly * ly + lz * lz
        motion.addLast(Motion(timestampNanos, vertical * vertical, max(0f, total - vertical * vertical), total))
        trim(motion, timestampNanos) { it.time }
    }

    fun onGyro(x: Float, y: Float, z: Float, timestampNanos: Long) {
        rotation.addLast(timestampNanos to x * x + y * y + z * z)
        trim(rotation, timestampNanos) { it.first }
    }

    fun onStep(timestampNanos: Long): Verdict {
        val previous = lastStep
        val interval = previous?.let { timestampNanos - it }
        lastStep = timestampNanos
        val window = window()
        val stats = "${interval?.let { "${it / 1_000_000} ms" } ?: "first step"}, $window"
        if (interval != null && interval < MIN_INTERVAL_NANOS) {
            // One quick step can be the detector reporting a step twice; two in a row is shaking.
            if (++fast < 2) return Verdict(0, restart = "too fast once", stats = stats)
        } else {
            fast = 0
        }
        val shaking = if (fast >= 2) "too fast" else window.shaking()
        if (shaking != null) {
            pending = 0
            lastInterval = null
            return Verdict(0, shaking, stats = stats)
        }
        val reference = lastInterval
        val steady = interval != null && interval <= MAX_INTERVAL_NANOS && (
            // Once the streak is credited, only a pause or shaking ends it; while it builds, the rhythm must hold
            // (a step the detector missed, twice the last interval, still fits).
            pending >= STREAK || reference == null ||
                abs(interval - reference) <= reference * MAX_RHYTHM_CHANGE ||
                abs(interval - 2 * reference) <= reference * MISSED_STEP_CHANGE
            )
        if (!steady) {
            // A pause or a change of pace: this step starts a new streak.
            val why = when {
                interval == null -> null
                interval > MAX_INTERVAL_NANOS -> "pause, streak restarted"
                else -> "off rhythm (last ${reference?.let { it / 1_000_000 }} ms), streak restarted"
            }
            pending = 1
            lastInterval = null
            return Verdict(0, restart = why, stats = stats)
        }
        // A missed step doesn't change the pace.
        lastInterval = if (reference != null && interval > reference * (1 + MAX_RHYTHM_CHANGE)) reference else interval
        pending++
        val credited = when {
            pending < STREAK -> 0
            pending == STREAK -> STREAK
            else -> 1
        }
        return Verdict(credited, stats = stats)
    }

    /** The hand's motion over the last [WINDOW_NANOS]. */
    private fun window(): Window {
        if (motion.isEmpty()) return Window(0.0, 0.0, 0.0, 0.0)
        val spin = if (hasGyro &&
            rotation.isNotEmpty()
        ) {
            sqrt(rotation.sumOf { it.second.toDouble() } / rotation.size)
        } else {
            0.0
        }
        return Window(
            vertical = sqrt(motion.sumOf { it.vertical.toDouble() } / motion.size),
            across = sqrt(motion.sumOf { it.across.toDouble() } / motion.size),
            peak = sqrt(motion.maxOf { it.total }.toDouble()),
            spin = spin
        )
    }

    /** RMS m/s² along and across gravity, peak m/s², RMS rad/s. */
    private class Window(val vertical: Double, val across: Double, val peak: Double, val spin: Double) {
        /** Why this looks like shaking, or null. */
        fun shaking(): String? = when {
            spin > MAX_ROTATION -> "spinning"
            across > MIN_ACROSS && across > vertical * MAX_ACROSS_RATIO -> "sideways"
            peak > MAX_PEAK -> "violent"
            else -> null
        }

        override fun toString() =
            "vertical %.1f, across %.1f, peak %.1f m/s², rotation %.1f rad/s".format(vertical, across, peak, spin)
    }

    private inline fun <T> trim(samples: ArrayDeque<T>, now: Long, time: (T) -> Long) {
        while (samples.isNotEmpty() && now - time(samples.first()) > WINDOW_NANOS) samples.removeFirst()
    }

    /** Squared hand acceleration: along gravity, across it, and in total. */
    private class Motion(val time: Long, val vertical: Float, val across: Float, val total: Float)

    companion object {
        /** Steps held back until the rhythm is steady. */
        const val STREAK = 4

        private const val GRAVITY_SECONDS = 1f
        private const val WINDOW_NANOS = 1_500_000_000L

        /** ~3.3 steps a second: faster is running at best, usually shaking. */
        private const val MIN_INTERVAL_NANOS = 300_000_000L

        /** A longer gap is a pause: the streak starts again. A slow walker with one missed step stays below it. */
        private const val MAX_INTERVAL_NANOS = 2_000_000_000L

        /** While a streak builds, how much one step interval may differ from the last (fraction). */
        private const val MAX_RHYTHM_CHANGE = 0.5f

        /** How close to twice the last interval a step after a missed one must be (fraction of the last). */
        private const val MISSED_STEP_CHANGE = 0.25f

        /** RMS rad/s: a phone held while walking turns slowly; a shaken one spins. */
        private const val MAX_ROTATION = 3.5

        /** RMS m/s² across gravity, and its ratio to the motion along it. */
        private const val MIN_ACROSS = 2.5
        private const val MAX_ACROSS_RATIO = 2.0

        /** m/s² of hand motion: walking stays well below this. */
        private const val MAX_PEAK = 20.0
    }
}
