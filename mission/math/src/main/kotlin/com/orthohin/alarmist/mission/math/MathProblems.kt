package com.orthohin.alarmist.mission.math

import kotlin.random.Random

/** [key] is stored in the mission config: never change it. Answers are never negative. */
enum class MathDifficulty(val key: String) {
    /** 3 + 5 */
    VERY_EASY("very_easy"),

    /** 23 + 48 */
    EASY("easy"),

    /** 23 + 48 + 17 */
    NORMAL("normal"),

    /** (23 × 7) + 48 */
    HARD("hard"),

    /** (234 × 7) + 486 */
    VERY_HARD("very_hard");

    fun problem(random: Random): MathProblem = when (this) {
        VERY_EASY -> sum(random, 2, 1..9)
        EASY -> sum(random, 2, 10..99)
        NORMAL -> sum(random, 3, 10..99)
        HARD -> productPlus(random, 10..99, 100)
        VERY_HARD -> productPlus(random, 100..999, 1000)
    }

    companion object {
        /** Unknown or missing keys (a newer or broken config) fall back to [NORMAL]. */
        fun fromKey(key: String?): MathDifficulty = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

data class MathProblem(val text: String, val answer: Int)

/** The same [seed] gives the same problems, so a run survives the screen being recreated. */
fun mathProblems(difficulty: MathDifficulty, count: Int, seed: Long): List<MathProblem> {
    val random = Random(seed)
    return List(count) { difficulty.problem(random) }
}

private fun sum(random: Random, terms: Int, range: IntRange): MathProblem {
    val numbers = List(terms) { range.random(random) }
    return MathProblem(numbers.joinToString(" + "), numbers.sum())
}

/** `(a × b) + c` with b in 3..9 (× 2 is too easy) and c the same size as a. */
private fun productPlus(random: Random, range: IntRange, limit: Int): MathProblem {
    val a = range.random(random)
    val b = (3..9).random(random)
    val c = (limit / 10 until limit).random(random)
    return MathProblem("($a × $b) + $c", a * b + c)
}
