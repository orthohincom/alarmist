package com.orthohin.alarmist.mission.shake

import kotlin.math.abs
import kotlin.math.sqrt

/** [key] is stored in the mission config: never change it. [threshold]: m/s² away from gravity that counts. */
enum class ShakeStrength(val key: String, val threshold: Float) {
    GENTLE("gentle", 5f),
    NORMAL("normal", 9f),
    STRONG("strong", 14f);

    companion object {
        /** Unknown or missing keys (a newer or broken config) fall back to [NORMAL]. */
        fun fromKey(key: String?): ShakeStrength = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/**
 * Counts shakes from accelerometer samples (m/s², gravity included). A shake is a sample at least [threshold] away
 * from gravity; the next one counts only after the force has dropped below half of it (one count per swing) and
 * [minGapNanos] has passed (no double counts from a jittery peak).
 */
class ShakeCounter(private val threshold: Float, private val minGapNanos: Long = 150_000_000L) {
    private var armed = true
    private var lastShake: Long? = null

    /** True if this sample completes a shake. */
    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long): Boolean {
        val force = abs(sqrt(x * x + y * y + z * z) - GRAVITY)
        if (!armed) {
            if (force < threshold / 2) armed = true
            return false
        }
        val last = lastShake
        if (force < threshold || (last != null && timestampNanos - last < minGapNanos)) return false
        armed = false
        lastShake = timestampNanos
        return true
    }

    private companion object {
        const val GRAVITY = 9.81f
    }
}
