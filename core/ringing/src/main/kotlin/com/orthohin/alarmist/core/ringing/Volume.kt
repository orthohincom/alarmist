package com.orthohin.alarmist.core.ringing

import kotlin.math.ceil
import kotlin.math.pow

/** Player gain during the ramp-up: from -40 dB to full over [rampUpSeconds], even steps to the ear. */
internal fun rampGain(elapsedMillis: Long, rampUpSeconds: Int): Float {
    if (rampUpSeconds <= 0) return 1f
    val progress = (elapsedMillis.toDouble() / (rampUpSeconds * 1000L)).coerceIn(0.0, 1.0)
    return 10.0.pow(2 * (progress - 1)).toFloat()
}

/** The alarm stream index for [percent] of its range: at least [min] and 1, so it is never muted. */
internal fun streamVolume(percent: Int, min: Int, max: Int): Int =
    ceil(max * percent / 100.0).toInt().coerceIn(maxOf(min, 1), max)
