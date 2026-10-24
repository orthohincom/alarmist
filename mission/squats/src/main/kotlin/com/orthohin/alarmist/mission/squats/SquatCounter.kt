package com.orthohin.alarmist.mission.squats

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Counts squats from accelerometer samples (m/s², gravity included) while the phone is held at the chest.
 *
 * It estimates the phone's vertical speed: the force (smoothed magnitude minus a slow gravity baseline, so any
 * orientation works) integrated with a leak, and reset to zero whenever the phone is still for a moment, so sensor
 * offsets and earlier shaking don't build up. A squat is going down faster than [speed] m/s, then coming up faster
 * than [speed], [minGapNanos] to [maxGapNanos] later; it counts on the way up.
 * A squat moves the phone 0.25–0.5 m and reaches 0.4–1 m/s; a walking bounce moves it ~5 cm (~0.2 m/s), and a shake
 * swings too fast, so neither counts.
 */
class SquatCounter(
    private val speed: Float = 0.25f,
    private val minGapNanos: Long = 400_000_000L,
    private val maxGapNanos: Long = 4_000_000_000L
) {
    private var smoothed: Float? = null
    private var baseline = GRAVITY
    private var velocity = 0f
    private var stillSeconds = 0f
    private var lastSample: Long? = null
    private var downAt: Long? = null

    /** True if this sample completes a squat. */
    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        val dt = lastSample?.let { ((timestampNanos - it) / 1e9f).coerceIn(0f, MAX_DT) } ?: 0f
        lastSample = timestampNanos
        // The fast low-pass drops hand tremor; the slow one follows gravity (a sensor may read it a little off).
        val fast = smoothed?.let { it + (magnitude - it) * dt / (FAST_SECONDS + dt) } ?: magnitude
        smoothed = fast
        // Clamped, so hard shaking or walking doesn't drag the baseline away from gravity.
        baseline += (magnitude - baseline).coerceIn(-BASELINE_STEP, BASELINE_STEP) * dt / (SLOW_SECONDS + dt)
        val force = fast - baseline
        stillSeconds = if (abs(force) < STILL_FORCE) stillSeconds + dt else 0f
        velocity = if (stillSeconds >= STILL_SECONDS) 0f else velocity + (force - velocity / LEAK_SECONDS) * dt

        val down = downAt
        if (velocity < -speed) {
            // Keeps the start of this descent; one too old (a long pause at the bottom) starts over.
            if (down == null || timestampNanos - down > maxGapNanos) downAt = timestampNanos
        } else if (velocity > speed && down != null) {
            downAt = null
            return timestampNanos - down in minGapNanos..maxGapNanos
        }
        return false
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val FAST_SECONDS = 0.15f
        const val SLOW_SECONDS = 6f
        const val LEAK_SECONDS = 3f
        const val BASELINE_STEP = 1f

        /** Force below this (m/s²) for this long means the phone is still. */
        const val STILL_FORCE = 0.3f
        const val STILL_SECONDS = 0.4f

        /** A gap in the samples (the sensor paused) doesn't count as one long step for the filters. */
        const val MAX_DT = 0.2f
    }
}
