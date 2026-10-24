package com.orthohin.alarmist.mission.simon

import kotlin.random.Random

/** Pads 0..3: top left, top right, bottom left, bottom right. */
const val SIMON_PADS = 4

/** The first pattern is this long (or the whole sequence, if shorter); each one repeated right adds a pad. */
const val SIMON_START = 3

/** [key] is stored in the mission config: never change it. */
enum class SimonSpeed(val key: String, val flashMillis: Long, val gapMillis: Long) {
    SLOW("slow", 800L, 400L),
    NORMAL("normal", 550L, 250L),
    FAST("fast", 350L, 150L);

    companion object {
        /** Unknown or missing keys (a newer or broken config) fall back to [NORMAL]. */
        fun fromKey(key: String?): SimonSpeed = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** [length] random pads; patterns are its first n pads. */
fun simonSequence(length: Int, random: Random): List<Int> = List(length) { random.nextInt(SIMON_PADS) }

/** Length of the first pattern shown for a sequence of [length]. */
fun simonStart(length: Int): Int = minOf(SIMON_START, length)

enum class SimonResult { PARTIAL, CORRECT, WRONG }

/** Checks [taps] against the pattern: the first [shown] pads of [sequence]. */
fun simonCheck(sequence: List<Int>, shown: Int, taps: List<Int>): SimonResult = when {
    taps.withIndex().any { (i, pad) -> i >= shown || pad != sequence[i] } -> SimonResult.WRONG
    taps.size == shown -> SimonResult.CORRECT
    else -> SimonResult.PARTIAL
}
