package com.orthohin.alarmist.mission.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathProblemsTest {
    /** Evaluates the shown text, so the answer always matches what the user sees. */
    private fun evaluate(text: String): Int = text.split(" + ").sumOf { term ->
        if (term.startsWith("(")) {
            term.removeSurrounding("(", ")").split(" × ").map(String::toInt).reduce(Int::times)
        } else {
            term.toInt()
        }
    }

    private fun numbers(text: String): List<Int> = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()

    @Test
    fun `the answer matches the text for every difficulty`() {
        for (difficulty in MathDifficulty.entries) {
            for (problem in mathProblems(difficulty, 200, seed = difficulty.ordinal.toLong())) {
                assertEquals(problem.text, evaluate(problem.text), problem.answer)
                assertTrue(problem.text, problem.answer >= 0)
            }
        }
    }

    @Test
    fun `difficulties have the expected shape`() {
        fun check(difficulty: MathDifficulty, vararg ranges: IntRange) {
            for (problem in mathProblems(difficulty, 200, seed = 7)) {
                val numbers = numbers(problem.text)
                assertEquals(problem.text, ranges.size, numbers.size)
                numbers.zip(ranges).forEach { (n, range) -> assertTrue(problem.text, n in range) }
            }
        }
        check(MathDifficulty.VERY_EASY, 1..9, 1..9)
        check(MathDifficulty.EASY, 10..99, 10..99)
        check(MathDifficulty.NORMAL, 10..99, 10..99, 10..99)
        check(MathDifficulty.HARD, 10..99, 3..9, 10..99)
        check(MathDifficulty.VERY_HARD, 100..999, 3..9, 100..999)
    }

    @Test
    fun `the same seed gives the same problems`() {
        assertEquals(mathProblems(MathDifficulty.HARD, 5, 42), mathProblems(MathDifficulty.HARD, 5, 42))
        assertEquals(3, mathProblems(MathDifficulty.EASY, 3, 1).size)
    }

    @Test
    fun `stored keys round-trip and unknown ones fall back to normal`() {
        MathDifficulty.entries.forEach { assertEquals(it, MathDifficulty.fromKey(it.key)) }
        assertEquals(MathDifficulty.NORMAL, MathDifficulty.fromKey("impossible"))
        assertEquals(MathDifficulty.NORMAL, MathDifficulty.fromKey(null))
    }
}
