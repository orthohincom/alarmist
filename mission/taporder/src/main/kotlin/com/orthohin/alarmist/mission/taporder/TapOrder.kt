package com.orthohin.alarmist.mission.taporder

import kotlin.random.Random

/** The board is split into this many columns and rows; each number takes one cell. */
const val TAP_ORDER_GRID = 6

/**
 * A number on the board, in cell ([column], [row]). [dx] and [dy] (0 until 1) shift it inside its cell, so the
 * numbers look scattered rather than lined up.
 */
data class TapTarget(val number: Int, val column: Int, val row: Int, val dx: Float, val dy: Float)

/** Numbers 1..[count], each in its own random cell, so they never overlap. */
fun tapOrderBoard(count: Int, random: Random): List<TapTarget> {
    require(count in 1..TAP_ORDER_GRID * TAP_ORDER_GRID) { "count $count doesn't fit the board" }
    return (0 until TAP_ORDER_GRID * TAP_ORDER_GRID).shuffled(random).take(count).mapIndexed { index, cell ->
        TapTarget(index + 1, cell % TAP_ORDER_GRID, cell / TAP_ORDER_GRID, random.nextFloat(), random.nextFloat())
    }
}

/** How far the user got: numbers 1..[found] are tapped, [next] is the one to tap now. */
data class TapProgress(val count: Int, val found: Int = 0) {
    val next: Int get() = found + 1

    val solved: Boolean get() = found >= count

    /** Only the next number counts; any other tap changes nothing. */
    fun tap(number: Int): TapProgress = if (!solved && number == next) copy(found = found + 1) else this
}
