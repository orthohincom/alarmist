package com.orthohin.alarmist.mission.steps

import kotlin.math.max

/**
 * Counts steps from both sources at once: the phone's step detector (if it's used) and [StepCounter] on the
 * accelerometer, each through its own [GaitCheck]. The mission shows whichever has counted more, so a detector that
 * stays silent (some phones' do while the phone is held in the hand) or misses steps never blocks the mission.
 * The runner and `GaitReplayTest` both feed it, so recordings replay exactly as the phone counted.
 */
class StepSources(
    hasGyro: Boolean,
    /** Every step's verdict, for the log; `leading` is true if this source has counted at least as many as the other. */
    private val onVerdict: (source: String, verdict: GaitCheck.Verdict, leading: Boolean) -> Unit = { _, _, _ -> }
) {
    private val counter = StepCounter()
    private val counterGait = GaitCheck(hasGyro)
    private val detectorGait = GaitCheck(hasGyro)
    private var fromCounter = 0
    private var fromDetector = 0

    /** Steps counted so far: the larger of the two sources. */
    val steps: Int get() = max(fromCounter, fromDetector)

    /** Returns how many steps to add now. */
    fun onAccel(x: Float, y: Float, z: Float, timestampNanos: Long): Int {
        counterGait.onAccel(x, y, z, timestampNanos)
        detectorGait.onAccel(x, y, z, timestampNanos)
        if (!counter.onSample(x, y, z, timestampNanos)) return 0
        return add {
            fromCounter += judge(counterGait, "accelerometer", fromCounter >= fromDetector, timestampNanos)
        }
    }

    fun onGyro(x: Float, y: Float, z: Float, timestampNanos: Long) {
        counterGait.onGyro(x, y, z, timestampNanos)
        detectorGait.onGyro(x, y, z, timestampNanos)
    }

    /** A step detector event. Returns how many steps to add now. */
    fun onDetectorStep(timestampNanos: Long): Int =
        add { fromDetector += judge(detectorGait, "detector", fromDetector >= fromCounter, timestampNanos) }

    private fun judge(gait: GaitCheck, source: String, leading: Boolean, timestampNanos: Long): Int {
        val verdict = gait.onStep(timestampNanos)
        onVerdict(source, verdict, leading)
        return verdict.credited
    }

    private inline fun add(count: () -> Unit): Int {
        val before = steps
        count()
        return steps - before
    }
}
