package com.orthohin.alarmist.mission.photo

import kotlin.math.cos
import kotlin.math.sin

/** An 8-bit grey image, row by row. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray = ByteArray(width * height)) {
    init {
        require(pixels.size == width * height) { "${pixels.size} pixels for $width×$height" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    /** Scaled down so neither side is longer than [maxSide]; itself if it already fits. */
    fun fitWithin(maxSide: Int): GrayImage {
        val longSide = maxOf(width, height)
        if (longSide <= maxSide) return this
        return scaled(maxOf(1, width * maxSide / longSide), maxOf(1, height * maxSide / longSide))
    }

    /** Resized (down) to [newWidth]×[newHeight]: each pixel is the mean of the source pixels it covers. */
    fun scaled(newWidth: Int, newHeight: Int): GrayImage {
        val xStart = IntArray(newWidth + 1) { it * width / newWidth }
        val yStart = IntArray(newHeight + 1) { it * height / newHeight }
        val out = ByteArray(newWidth * newHeight)
        for (oy in 0 until newHeight) {
            val y0 = yStart[oy]
            val y1 = maxOf(yStart[oy + 1], y0 + 1)
            for (ox in 0 until newWidth) {
                val x0 = xStart[ox]
                val x1 = maxOf(xStart[ox + 1], x0 + 1)
                var sum = 0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) sum += pixels[row + x].toInt() and 0xFF
                }
                out[oy * newWidth + ox] = (sum / ((y1 - y0) * (x1 - x0))).toByte()
            }
        }
        return GrayImage(newWidth, newHeight, out)
    }

    /** Turned clockwise by [degrees], a multiple of 90. */
    fun rotated(degrees: Int): GrayImage {
        val out = ByteArray(pixels.size)
        when (Math.floorMod(degrees, 360)) {
            0 -> return this

            180 -> return GrayImage(width, height, pixels.reversedArray())

            90 -> for (y in 0 until height) {
                for (x in 0 until width) out[x * height + (height - 1 - y)] = pixels[y * width + x]
            }

            270 -> for (y in 0 until height) {
                for (x in 0 until width) out[(width - 1 - x) * height + y] = pixels[y * width + x]
            }

            else -> throw IllegalArgumentException("Not a right angle: $degrees")
        }
        return GrayImage(height, width, out)
    }

    /** Turned clockwise by [degrees] and magnified by [zoom] about the middle, same size; outside is grey. */
    fun turned(degrees: Double, zoom: Double): GrayImage {
        val angle = Math.toRadians(degrees)
        val c = cos(angle) / zoom
        val s = sin(angle) / zoom
        val out = ByteArray(pixels.size)
        for (v in 0 until height) {
            for (u in 0 until width) {
                // The inverse map: where in this image each output pixel comes from.
                val du = u - width / 2.0
                val dv = v - height / 2.0
                val x = c * du + s * dv + width / 2.0
                val y = -s * du + c * dv + height / 2.0
                out[v * width + u] = sample(x, y).toByte()
            }
        }
        return GrayImage(width, height, out)
    }

    /** Bilinear; 128 outside. */
    private fun sample(x: Double, y: Double): Int {
        if (x < 0 || y < 0 || x > width - 1.0 || y > height - 1.0) return 128
        val ix = minOf(x.toInt(), width - 2)
        val iy = minOf(y.toInt(), height - 2)
        val fx = x - ix
        val fy = y - iy
        val top = this[ix, iy] * (1 - fx) + this[ix + 1, iy] * fx
        val bottom = this[ix, iy + 1] * (1 - fx) + this[ix + 1, iy + 1] * fx
        return (top * (1 - fy) + bottom * fy + 0.5).toInt()
    }

    /** Smoothed with a 7-tap Gaussian (σ = 2) each way; the edges repeat. */
    fun blurred(): GrayImage {
        val across = IntArray(pixels.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var sum = 0
                for (k in -3..3) sum += BLUR[k + 3] * (pixels[row + (x + k).coerceIn(0, width - 1)].toInt() and 0xFF)
                across[row + x] = sum
            }
        }
        val out = ByteArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0L
                for (k in -3..3) sum += BLUR[k + 3].toLong() * across[(y + k).coerceIn(0, height - 1) * width + x]
                out[y * width + x] = (sum / BLUR_TOTAL).toInt().toByte()
            }
        }
        return GrayImage(width, height, out)
    }

    private companion object {
        // exp(-k² / 8) × 256.
        val BLUR = intArrayOf(83, 155, 226, 256, 226, 155, 83)
        val BLUR_TOTAL = BLUR.sum().toLong().let { it * it }
    }
}
