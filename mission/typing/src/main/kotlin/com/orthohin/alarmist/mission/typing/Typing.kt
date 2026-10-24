package com.orthohin.alarmist.mission.typing

import kotlin.random.Random

/**
 * How typed text is checked against a phrase. Case, curly vs straight quotes, leading spaces and repeated spaces
 * don't count: phone keyboards capitalise and "smarten" text on their own.
 */
object Typing {
    private val spaces = Regex("\\s+")

    /** A phrase as shown and compared: trimmed, single spaces. */
    fun clean(text: String): String = text.trim().replace(spaces, " ")

    /** How many characters of [phrase] (already [clean]) [typed] gets right from the start. */
    fun matched(phrase: String, typed: String): Int {
        val t = normalized(typed)
        var i = 0
        while (i < phrase.length && i < t.length && same(phrase[i], t[i])) i++
        return i
    }

    /** True if [typed] has a wrong character (not just an unfinished phrase). */
    fun hasError(phrase: String, typed: String): Boolean = matched(phrase, typed) < normalized(typed).trimEnd().length

    fun isDone(phrase: String, typed: String): Boolean =
        phrase.isNotEmpty() && clean(typed).length == phrase.length && matched(phrase, typed) == phrase.length

    /** [count] phrases from [pool] in random order; repeats only when the pool is smaller than [count]. */
    fun pick(pool: List<String>, count: Int, random: Random): List<String> {
        val shuffled = pool.map(::clean).filter(String::isNotEmpty).shuffled(random)
        return if (shuffled.isEmpty()) emptyList() else List(count) { shuffled[it % shuffled.size] }
    }

    private fun normalized(typed: String): String = typed.trimStart().replace(spaces, " ")

    private fun same(a: Char, b: Char): Boolean = fold(a) == fold(b)

    private fun fold(c: Char): Char = when (c) {
        '‘', '’', 'ʼ' -> '\''
        '“', '”' -> '"'
        else -> c.lowercaseChar()
    }
}
