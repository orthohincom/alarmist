package com.orthohin.alarmist.mission.steps

import kotlin.math.sqrt

/**
 * Counts steps from accelerometer samples (m/s², gravity included); runs beside the step detector, which some phones
 * have silent while the phone is in the hand. Each step bounces the phone: the smoothed force rises [threshold] above
 * gravity once per step. One count per bounce (it must fall back below gravity first), at most one per [minGapNanos]
 * (a fast walk is ~2.5 steps a second). Tuned on real walks (`GaitReplayTest`): a phone held in the hand bounces
 * ~1 m/s², so 1.5 missed half the steps of a gentle walk; strong bounces have a second bump ~350 ms into the step.
 * It errs towards counting: a missed step frustrates, an extra one doesn't.
 */
class StepCounter(private val threshold: Float = 0.8f, private val minGapNanos: Long = 350_000_000L) {
    private var smoothed: Float? = null
    private var armed = true
    private var lastStep: Long? = null

    /** True if this sample completes a step. */
    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        // A low-pass filter: drops hand tremor and sensor noise, keeps the ~2 Hz walking bounce.
        val force = (smoothed?.let { it + SMOOTHING * (magnitude - it) } ?: magnitude).also { smoothed = it } - GRAVITY
        if (!armed) {
            if (force < 0) armed = true
            return false
        }
        val last = lastStep
        if (force < threshold || (last != null && timestampNanos - last < minGapNanos)) return false
        armed = false
        lastStep = timestampNanos
        return true
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val SMOOTHING = 0.3f
    }
}
