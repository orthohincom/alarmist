package com.orthohin.alarmist.mission.camera

import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Copies a camera plane (e.g. CameraX's Y plane) into a tight [width]×[height] array, dropping the row padding
 * ([rowStride] ≥ width) and skipping [pixelStride] bytes per pixel.
 */
fun lumaFromPlane(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int = 1): ByteArray {
    val luma = ByteArray(width * height)
    val source = buffer.duplicate()
    if (pixelStride == 1 && rowStride == width) {
        source.rewind()
        source.get(luma)
        return luma
    }
    val row = ByteArray(rowStride)
    for (y in 0 until height) {
        source.position(y * rowStride)
        // The last row may be shorter than rowStride.
        val length = minOf(rowStride, source.remaining())
        source.get(row, 0, length)
        for (x in 0 until width) luma[y * width + x] = row[x * pixelStride]
    }
    return luma
}

/** [luma] ([width]×[height]) turned 90° clockwise: the result is height×width. */
fun rotate90(luma: ByteArray, width: Int, height: Int): ByteArray {
    val rotated = ByteArray(luma.size)
    for (y in 0 until height) {
        for (x in 0 until width) rotated[x * height + (height - 1 - y)] = luma[y * width + x]
    }
    return rotated
}

/**
 * Tells whether the camera image changed since the last frame (the phone is being carried), from the average
 * brightness of a [GRID]×[GRID] grid of cells. [threshold] is the mean change per cell, 0–255.
 */
class MotionDetector(private val threshold: Int = 12) {
    private var last: IntArray? = null

    fun moved(luma: ByteArray, width: Int, height: Int): Boolean {
        val cells = IntArray(GRID * GRID)
        val counts = IntArray(GRID * GRID)
        // Every 4th pixel each way is plenty for a coarse brightness map.
        for (y in 0 until height step 4) {
            val cellRow = y * GRID / height * GRID
            for (x in 0 until width step 4) {
                val cell = cellRow + x * GRID / width
                cells[cell] += luma[y * width + x].toInt() and 0xFF
                counts[cell]++
            }
        }
        for (i in cells.indices) cells[i] /= maxOf(counts[i], 1)
        val previous = last
        last = cells
        return previous != null && cells.indices.sumOf { abs(cells[it] - previous[it]) } / cells.size >= threshold
    }

    private companion object {
        const val GRID = 8
    }
}
