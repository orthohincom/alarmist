package com.orthohin.alarmist.mission.photo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Keypoints of a [width]×[height] image (positions in its pixels) with their 256-bit descriptors,
 * [DESCRIPTOR_LONGS] longs each.
 */
class Features(val width: Int, val height: Int, val x: FloatArray, val y: FloatArray, val descriptors: LongArray) {
    val size: Int get() = x.size

    companion object {
        const val DESCRIPTOR_LONGS = 4
    }
}

/**
 * ORB (Rublee et al., 2011): FAST corners at [LEVELS] scales, ranked by Harris response and spread over the image,
 * each with a BRIEF descriptor (256 brightness comparisons in its neighbourhood) turned to the corner's own
 * orientation, so it survives the phone being held at another angle or distance. Stateless and thread-safe.
 */
object Orb {
    private const val LEVELS = 4
    private const val SCALE = 1.3
    private const val PATCH = 15
    private const val EDGE = PATCH + 1
    private const val FAST_THRESHOLD = 20

    // Retried with this for dim, low-contrast scenes (a dark bedroom) that have too few corners.
    private const val FAST_MIN_THRESHOLD = 7
    private const val ARC = 9
    private const val GRID = 4
    private const val ANGLE_BINS = 30
    private const val BITS = 256

    /** Up to [maxFeatures] keypoints of [image]. */
    fun detect(image: GrayImage, maxFeatures: Int = 500): Features {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val descriptors = ArrayList<Long>()
        // Fewer features on the smaller levels, in proportion to their area, as OpenCV does.
        val factor = 1 / SCALE
        var wanted = maxFeatures * (1 - factor) / (1 - factor.pow(LEVELS))
        for (levelIndex in 0 until LEVELS) {
            val scale = SCALE.pow(levelIndex)
            val level = if (levelIndex == 0) {
                image
            } else {
                image.scaled((image.width / scale).roundToInt(), (image.height / scale).roundToInt())
            }
            if (level.width <= 2 * EDGE || level.height <= 2 * EDGE) break
            val corners = corners(level, wanted.roundToInt())
            val smooth = level.blurred()
            val pattern = patternOffsets(level.width)
            val sx = image.width.toFloat() / level.width
            val sy = image.height.toFloat() / level.height
            for (i in corners) {
                val px = i % level.width
                val py = i / level.width
                xs += px * sx
                ys += py * sy
                describe(smooth, i, orientation(level, px, py), pattern, descriptors)
            }
            wanted *= factor
        }
        return Features(image.width, image.height, xs.toFloatArray(), ys.toFloatArray(), descriptors.toLongArray())
    }

    /** Pixel indices of up to [wanted] corners: strongest Harris response first, at most twice a fair share per cell. */
    private fun corners(image: GrayImage, wanted: Int): List<Int> {
        var candidates = fastCorners(image, FAST_THRESHOLD)
        if (candidates.size < wanted) candidates = fastCorners(image, FAST_MIN_THRESHOLD)
        val response = DoubleArray(candidates.size) { harris(image, candidates[it]) }
        val order = candidates.indices.filter { response[it] > 0 }.sortedByDescending { response[it] }
        val perCell = maxOf(1, 2 * wanted / (GRID * GRID))
        val inCell = IntArray(GRID * GRID)
        val chosen = ArrayList<Int>(wanted)
        val taken = BooleanArray(candidates.size)
        for (k in order) {
            if (chosen.size >= wanted) break
            val i = candidates[k]
            val cell = (i / image.width) * GRID / image.height * GRID + (i % image.width) * GRID / image.width
            if (inCell[cell] < perCell) {
                inCell[cell]++
                chosen += i
                taken[k] = true
            }
        }
        // Texture in one corner of the scene (a poster on a bare wall): fill up with the best of the rest.
        for (k in order) {
            if (chosen.size >= wanted) break
            if (!taken[k]) chosen += candidates[k]
        }
        return chosen
    }

    /** FAST-9 corners with [threshold], local maxima of their score only, at least [EDGE] from the border. */
    private fun fastCorners(image: GrayImage, threshold: Int): IntArray {
        val w = image.width
        val p = image.pixels
        val circle = IntArray(CIRCLE_X.size) { CIRCLE_Y[it] * w + CIRCLE_X[it] }
        val score = IntArray(p.size)
        for (y in EDGE - 1 until image.height - EDGE + 1) {
            for (x in EDGE - 1 until w - EDGE + 1) score[y * w + x] = fastScore(p, y * w + x, circle, threshold)
        }
        val corners = ArrayList<Int>()
        for (y in EDGE until image.height - EDGE) {
            for (x in EDGE until w - EDGE) {
                val i = y * w + x
                val s = score[i]
                // Ties go to the first in reading order.
                if (s > 0 &&
                    s > score[i - w - 1] && s > score[i - w] && s > score[i - w + 1] && s > score[i - 1] &&
                    s >= score[i + 1] && s >= score[i + w - 1] && s >= score[i + w] && s >= score[i + w + 1]
                ) {
                    corners += i
                }
            }
        }
        return corners.toIntArray()
    }

    /** 0 unless [ARC] contiguous pixels of the circle are all brighter or all darker than the centre by [t]. */
    private fun fastScore(p: ByteArray, i: Int, circle: IntArray, t: Int): Int {
        val center = p[i].toInt() and 0xFF
        // Any arc of 9 covers at least two of the four compass points.
        var bright = 0
        var dark = 0
        for (k in 0 until 16 step 4) {
            val v = p[i + circle[k]].toInt() and 0xFF
            if (v > center + t) {
                bright++
            } else if (v < center - t) {
                dark++
            }
        }
        if (bright < 2 && dark < 2) return 0
        val sign = when {
            hasArc(p, i, circle, center, t, 1) -> 1
            hasArc(p, i, circle, center, t, -1) -> -1
            else -> return 0
        }
        var score = 0
        for (offset in circle) score += maxOf(0, sign * ((p[i + offset].toInt() and 0xFF) - center) - t)
        return score
    }

    private fun hasArc(p: ByteArray, i: Int, circle: IntArray, center: Int, t: Int, sign: Int): Boolean {
        var run = 0
        // Going round one and a half times finds arcs that wrap past the start.
        for (k in 0 until 16 + ARC - 1) {
            if (sign * ((p[i + circle[k % 16]].toInt() and 0xFF) - center) > t) {
                if (++run >= ARC) return true
            } else {
                run = 0
            }
        }
        return false
    }

    /** Harris corner response over a 7×7 window. */
    private fun harris(image: GrayImage, i: Int): Double {
        val w = image.width
        val p = image.pixels
        var xx = 0L
        var yy = 0L
        var xy = 0L
        for (dy in -3..3) {
            for (dx in -3..3) {
                val j = i + dy * w + dx
                val gx = (p[j + 1].toInt() and 0xFF) - (p[j - 1].toInt() and 0xFF)
                val gy = (p[j + w].toInt() and 0xFF) - (p[j - w].toInt() and 0xFF)
                xx += gx * gx
                yy += gy * gy
                xy += gx * gy
            }
        }
        val trace = (xx + yy).toDouble()
        return xx.toDouble() * yy - xy.toDouble() * xy - HARRIS_K * trace * trace
    }

    /** The direction from the corner to the centroid of brightness in its patch, in radians. */
    private fun orientation(image: GrayImage, x: Int, y: Int): Double {
        var m10 = 0L
        var m01 = 0L
        for (dy in -PATCH..PATCH) {
            val half = HALF_WIDTH[kotlin.math.abs(dy)]
            for (dx in -half..half) {
                val v = image[x + dx, y + dy]
                m10 += dx * v
                m01 += dy * v
            }
        }
        return atan2(m01.toDouble(), m10.toDouble())
    }

    private fun describe(smooth: GrayImage, i: Int, angle: Double, pattern: IntArray, out: MutableList<Long>) {
        val bin = Math.floorMod((angle / (2 * PI) * ANGLE_BINS).roundToInt(), ANGLE_BINS)
        val base = bin * BITS * 2
        val p = smooth.pixels
        val words = LongArray(Features.DESCRIPTOR_LONGS)
        for (bit in 0 until BITS) {
            val a = p[i + pattern[base + 2 * bit]].toInt() and 0xFF
            val b = p[i + pattern[base + 2 * bit + 1]].toInt() and 0xFF
            if (a < b) words[bit / 64] = words[bit / 64] or (1L shl (bit % 64))
        }
        words.forEach { out += it }
    }

    /** The test pairs as pixel offsets for an image [width] wide, for each angle bin. */
    private fun patternOffsets(width: Int): IntArray {
        val offsets = IntArray(ANGLE_BINS * BITS * 2)
        for (bin in 0 until ANGLE_BINS) {
            val angle = 2 * PI * bin / ANGLE_BINS
            val c = cos(angle)
            val s = sin(angle)
            for (k in 0 until BITS * 2) {
                val dx = (PATTERN_X[k] * c - PATTERN_Y[k] * s).roundToInt()
                val dy = (PATTERN_X[k] * s + PATTERN_Y[k] * c).roundToInt()
                offsets[bin * BITS * 2 + k] = dy * width + dx
            }
        }
        return offsets
    }

    private const val HARRIS_K = 0.04

    // Bresenham circle of radius 3, clockwise from the top.
    private val CIRCLE_X = intArrayOf(0, 1, 2, 3, 3, 3, 2, 1, 0, -1, -2, -3, -3, -3, -2, -1)
    private val CIRCLE_Y = intArrayOf(-3, -3, -2, -1, 0, 1, 2, 3, 3, 3, 2, 1, 0, -1, -2, -3)

    private val HALF_WIDTH = IntArray(PATCH + 1) { sqrt((PATCH * PATCH - it * it).toDouble()).toInt() }

    // BRIEF's best layout: both points of a pair Gaussian around the corner (σ = patch / 5). Kept within radius 13,
    // so a turned point stays inside the patch. A fixed seed: the reference and the camera frames must agree.
    private val PATTERN_X = DoubleArray(BITS * 2)
    private val PATTERN_Y = DoubleArray(BITS * 2)

    init {
        val random = Random(0x0A1A)
        val sigma = (2 * PATCH + 1) / 5.0
        for (k in 0 until BITS * 2) {
            do {
                // Box–Muller.
                val r = sigma * sqrt(-2 * ln(1 - random.nextDouble()))
                val theta = 2 * PI * random.nextDouble()
                PATTERN_X[k] = r * cos(theta)
                PATTERN_Y[k] = r * sin(theta)
            } while (PATTERN_X[k] * PATTERN_X[k] + PATTERN_Y[k] * PATTERN_Y[k] > 13.0 * 13.0)
        }
    }
}
