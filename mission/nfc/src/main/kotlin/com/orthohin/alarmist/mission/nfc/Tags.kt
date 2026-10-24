package com.orthohin.alarmist.mission.nfc

import kotlin.math.abs
import kotlin.math.sqrt

/** A tag's ID (UID) as upper-case hex, e.g. "04A1B2C3D4E5F6": stored in the mission config, never change. */
fun tagId(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

/**
 * True if [bytes] can't identify a tag: empty, or an NFC-A random ID (4 bytes starting 0x08, ISO 14443-3), which
 * bank cards and phones make up anew each time.
 */
fun isRandomId(bytes: ByteArray): Boolean = bytes.isEmpty() || (bytes.size == 4 && bytes[0] == 0x08.toByte())

/**
 * Registering takes two separate taps with the same ID: a tag whose ID changes each time (beyond what [isRandomId]
 * catches) would make the alarm unsolvable.
 */
class TagConfirmer {
    enum class Result { FIRST, CONFIRMED, CHANGES }

    private var first: String? = null

    fun onTag(bytes: ByteArray): Result {
        if (isRandomId(bytes)) {
            first = null
            return Result.CHANGES
        }
        val id = tagId(bytes)
        val previous = first
        return when {
            previous == null -> {
                first = id
                Result.FIRST
            }

            previous == id -> Result.CONFIRMED

            else -> {
                // Maybe a second tag; or a tag that changes. Start over from this one.
                first = id
                Result.CHANGES
            }
        }
    }
}

/**
 * Tells from the accelerometer whether the phone is being carried (walking to the tag), at most once per [gapNanos]:
 * the acceleration's size differs from gravity by more than [threshold] m/s².
 */
class MotionGate(private val threshold: Float = 1.5f, private val gapNanos: Long = 1_000_000_000L) {
    private var last = Long.MIN_VALUE

    fun moved(x: Float, y: Float, z: Float, timeNanos: Long): Boolean {
        if (abs(sqrt(x * x + y * y + z * z) - GRAVITY) < threshold) return false
        if (last != Long.MIN_VALUE && timeNanos - last < gapNanos) return false
        last = timeNanos
        return true
    }

    private companion object {
        const val GRAVITY = 9.81f
    }
}
