package com.orthohin.alarmist.mission.photo

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Compares a camera frame with the registered photo: keypoint pairs with clearly similar descriptors (Lowe's ratio
 * test), then the largest group of pairs that agree on one perspective transform (a homography found by RANSAC).
 * The score is the size of that group: about 11 at most for unrelated scenes, 50+ for the same scene from a step
 * aside in other light (PhotoMatcherTest).
 */
object PhotoMatcher {
    /** Both the registered photo and camera frames are compared at this size (long side, px). */
    const val WORKING_SIZE = 480

    private const val SELF_TURN = 12.0
    private const val SELF_ZOOM = 1.2
    private const val MAX_DISTANCE = 64
    private const val RATIO = 0.8

    // Pairs farther apart than this (in the reference) are different places, not the same corner at two scales.
    private const val SAME_PLACE = 8f

    // Of the long side: about 10 px at 480.
    private const val INLIER_DISTANCE = 0.02
    private const val MAX_ITERATIONS = 2000
    private const val CONFIDENCE = 0.99

    // A homography can't stretch the scene more than 3× either way: farther than that isn't the same view.
    private const val MAX_AREA_CHANGE = 9.0

    /** The score that counts as a match. Unrelated scenes score up to about 11. */
    enum class Level(val minScore: Int) { EASY(15), NORMAL(25), STRICT(45) }

    /** Below this [distinctiveness], a photo can't be told apart reliably and isn't accepted for registering. */
    const val MIN_DISTINCTIVENESS = 50

    /**
     * How recognisable [photo] (at [WORKING_SIZE]) is: its score against itself turned and seen closer. A bare wall
     * or a repeating pattern (tiles, a grid) scores low: every corner looks like the others. A good scene scores 100+.
     */
    fun distinctiveness(photo: GrayImage): Int =
        score(Orb.detect(photo), Orb.detect(photo.turned(SELF_TURN, SELF_ZOOM)))

    /** How many keypoints of [query] agree with [reference] on one view of the same scene. */
    fun score(reference: Features, query: Features, random: Random = Random(1)): Int {
        val pairs = pairs(reference, query)
        if (pairs.size < 4) return 0
        val refScale = maxOf(reference.width, reference.height).toDouble()
        val queryScale = maxOf(query.width, query.height).toDouble()
        // Centred and scaled to the long side, so the numbers stay well-conditioned and thresholds are relative.
        val sx = DoubleArray(pairs.size) { (reference.x[pairs[it].first] - reference.width / 2.0) / refScale }
        val sy = DoubleArray(pairs.size) { (reference.y[pairs[it].first] - reference.height / 2.0) / refScale }
        val dx = DoubleArray(pairs.size) { (query.x[pairs[it].second] - query.width / 2.0) / queryScale }
        val dy = DoubleArray(pairs.size) { (query.y[pairs[it].second] - query.height / 2.0) / queryScale }
        val halfWidth = reference.width / 2.0 / refScale
        val halfHeight = reference.height / 2.0 / refScale
        var best = 0
        var iterations = MAX_ITERATIONS
        var done = 0
        val sample = IntArray(4)
        while (done < iterations) {
            done++
            pick(random, pairs.size, sample)
            if (!sameTurn(sample, sx, sy, dx, dy)) continue
            val h = homography(sample, sx, sy, dx, dy) ?: continue
            if (!plausible(h, halfWidth, halfHeight)) continue
            var inliers = 0
            for (k in pairs.indices) {
                val w = h[6] * sx[k] + h[7] * sy[k] + 1
                if (w <= 0) continue
                val ex = (h[0] * sx[k] + h[1] * sy[k] + h[2]) / w - dx[k]
                val ey = (h[3] * sx[k] + h[4] * sy[k] + h[5]) / w - dy[k]
                if (ex * ex + ey * ey < INLIER_DISTANCE * INLIER_DISTANCE) inliers++
            }
            if (inliers > best) {
                best = inliers
                val allGood = (best.toDouble() / pairs.size).pow(4)
                if (allGood >= 1.0) break
                iterations = minOf(MAX_ITERATIONS, (ln(1 - CONFIDENCE) / ln(1 - allGood)).toInt() + 1)
            }
        }
        return best
    }

    /**
     * (reference, query) keypoint index pairs that are each other's best match, with that match clearly better than any
     * other place.
     */
    internal fun pairs(reference: Features, query: Features): List<Pair<Int, Int>> {
        val r = reference.descriptors
        val q = query.descriptors
        val n = Features.DESCRIPTOR_LONGS
        val distance = IntArray(reference.size * query.size)
        val bestQuery = IntArray(reference.size) { -1 }
        for (i in 0 until reference.size) {
            var bestDistance = Int.MAX_VALUE
            for (j in 0 until query.size) {
                var d = 0
                for (k in 0 until n) d += (r[i * n + k] xor q[j * n + k]).countOneBits()
                distance[i * query.size + j] = d
                if (d < bestDistance) {
                    bestDistance = d
                    bestQuery[i] = j
                }
            }
        }
        val result = ArrayList<Pair<Int, Int>>()
        for (j in 0 until query.size) {
            var best = -1
            var bestDistance = Int.MAX_VALUE
            for (i in 0 until reference.size) {
                val d = distance[i * query.size + j]
                if (d < bestDistance) {
                    bestDistance = d
                    best = i
                }
            }
            if (best < 0 || bestDistance > MAX_DISTANCE || bestQuery[best] != j) continue
            // The runner-up must be another place: the same corner is often found at two scales.
            var second = Int.MAX_VALUE
            for (i in 0 until reference.size) {
                if (abs(reference.x[i] - reference.x[best]) < SAME_PLACE &&
                    abs(reference.y[i] - reference.y[best]) < SAME_PLACE
                ) {
                    continue
                }
                second = minOf(second, distance[i * query.size + j])
            }
            if (bestDistance < RATIO * second) result += best to j
        }
        return result
    }

    private fun pick(random: Random, size: Int, into: IntArray) {
        for (k in into.indices) {
            var i: Int
            do i = random.nextInt(size) while ((0 until k).any { into[it] == i })
            into[k] = i
        }
    }

    /**
     * True if every three of the four points turn the same way in both images and aren't in a line: a mirror image or
     * a fold isn't a view of the same scene.
     */
    private fun sameTurn(s: IntArray, sx: DoubleArray, sy: DoubleArray, dx: DoubleArray, dy: DoubleArray): Boolean {
        for (skip in 0 until 4) {
            val (a, b, c) = s.filterIndexed { index, _ -> index != skip }
            val src = cross(sx, sy, a, b, c)
            val dst = cross(dx, dy, a, b, c)
            if (abs(src) < MIN_AREA || abs(dst) < MIN_AREA || (src > 0) != (dst > 0)) return false
        }
        return true
    }

    private fun cross(x: DoubleArray, y: DoubleArray, a: Int, b: Int, c: Int): Double =
        (x[b] - x[a]) * (y[c] - y[a]) - (y[b] - y[a]) * (x[c] - x[a])

    /** The homography (h33 = 1) mapping the four sample points; null if they don't fix one. */
    private fun homography(
        s: IntArray,
        sx: DoubleArray,
        sy: DoubleArray,
        dx: DoubleArray,
        dy: DoubleArray
    ): DoubleArray? {
        val a = Array(8) { DoubleArray(9) }
        for ((row, k) in s.withIndex()) {
            val x = sx[k]
            val y = sy[k]
            val u = dx[k]
            val v = dy[k]
            a[2 * row] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * row + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        return solve(a)
    }

    /** Gaussian elimination with partial pivoting on an n×(n+1) augmented matrix. */
    private fun solve(a: Array<DoubleArray>): DoubleArray? {
        val n = a.size
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(a[it][col]) }
            if (abs(a[pivot][col]) < 1e-12) return null
            a[col] = a[pivot].also { a[pivot] = a[col] }
            for (row in col + 1 until n) {
                val f = a[row][col] / a[col][col]
                if (f == 0.0) continue
                for (k in col..n) a[row][k] -= f * a[col][k]
            }
        }
        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = a[row][n]
            for (k in row + 1 until n) sum -= a[row][k] * x[k]
            x[row] = sum / a[row][row]
        }
        return x
    }

    /**
     * A view a phone could have taken: the reference's corners land in front of the camera as a convex shape, and the
     * middle of the scene is scaled by at most 3× (area 9×) either way.
     */
    private fun plausible(h: DoubleArray, halfWidth: Double, halfHeight: Double): Boolean {
        // Jacobian at the reference's centre (0, 0), where w = 1.
        val area = (h[0] - h[2] * h[6]) * (h[4] - h[5] * h[7]) - (h[1] - h[2] * h[7]) * (h[3] - h[5] * h[6])
        if (area < 1 / MAX_AREA_CHANGE || area > MAX_AREA_CHANGE) return false
        val cornersX = doubleArrayOf(-halfWidth, halfWidth, halfWidth, -halfWidth)
        val cornersY = doubleArrayOf(-halfHeight, -halfHeight, halfHeight, halfHeight)
        val px = DoubleArray(4)
        val py = DoubleArray(4)
        for (k in 0 until 4) {
            val w = h[6] * cornersX[k] + h[7] * cornersY[k] + 1
            if (w <= 0) return false
            px[k] = (h[0] * cornersX[k] + h[1] * cornersY[k] + h[2]) / w
            py[k] = (h[3] * cornersX[k] + h[4] * cornersY[k] + h[5]) / w
        }
        // Clockwise on screen (y down), like the corners above.
        for (k in 0 until 4) {
            if (cross(px, py, k, (k + 1) % 4, (k + 2) % 4) <= 0) return false
        }
        return true
    }

    private const val MIN_AREA = 1e-3
}
