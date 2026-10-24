package com.orthohin.alarmist.mission.memory

import kotlin.random.Random

/** [key] is stored in the mission config: never change it. */
enum class MemoryDifficulty(val key: String, val gridSize: Int, val tiles: Int) {
    EASY("easy", 3, 3),
    NORMAL("normal", 4, 5),
    HARD("hard", 5, 7),
    VERY_HARD("very_hard", 5, 10);

    /** How long the tiles stay lit: more tiles, more time. */
    val showMillis: Long get() = 1000L + 250L * tiles

    companion object {
        /** Unknown or missing keys (a newer or broken config) fall back to [NORMAL]. */
        fun fromKey(key: String?): MemoryDifficulty = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** [MemoryDifficulty.tiles] distinct tile indexes, row by row from 0. */
fun memoryPattern(difficulty: MemoryDifficulty, random: Random): Set<Int> =
    (0 until difficulty.gridSize * difficulty.gridSize).shuffled(random).take(difficulty.tiles).toSet()

/** One attempt at a pattern. Replays taps, so the UI only needs to save the tap list. */
data class MemoryRound(val pattern: Set<Int>, val found: Set<Int> = emptySet(), val misses: Set<Int> = emptySet()) {
    val solved: Boolean get() = found.size == pattern.size

    /** A tile already tapped counts once. */
    fun tap(tile: Int): MemoryRound = when (tile) {
        in found, in misses -> this
        in pattern -> copy(found = found + tile)
        else -> copy(misses = misses + tile)
    }

    fun failed(mistakesAllowed: Int): Boolean = misses.size > mistakesAllowed

    companion object {
        fun replay(pattern: Set<Int>, taps: List<Int>): MemoryRound =
            taps.fold(MemoryRound(pattern)) { round, tile -> round.tap(tile) }
    }
}
