package com.orthohin.alarmist.mission.photo

import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Accuracy on made-up scenes seen as a sleepy person would: turned, nearer or farther, a step aside, tilted, in
 * other light, with sensor noise. Set PHOTO_SAMPLES to a folder of photos to also run on real images (each quarter
 * is a scene); stats print with `-i | grep PHOTOSTATS`.
 */
class PhotoMatcherTest {
    private val scenes = (1..6).map { Scenes.synthetic(it) }

    private fun features(image: GrayImage) = Orb.detect(image.fitWithin(PhotoMatcher.WORKING_SIZE))

    private fun registered(scene: GrayImage) = Scenes.look(scene, Scenes.View()).fitWithin(PhotoMatcher.WORKING_SIZE)

    private class Scores(val same: List<Int>, val other: List<Int>)

    /** Each scene from 4 [view]s, scored against its own photo and every other scene's. */
    private fun scores(name: String, scenes: List<GrayImage>, view: (Random) -> Scenes.View): Scores {
        val references = scenes.map { Orb.detect(registered(it)) }
        val random = Random(7)
        val same = ArrayList<Int>()
        val other = ArrayList<Int>()
        for ((i, scene) in scenes.withIndex()) {
            repeat(4) { k ->
                val query = features(Scenes.look(scene, view(random), seed = k))
                same += PhotoMatcher.score(references[i], query)
                for (j in scenes.indices) if (j != i) other += PhotoMatcher.score(references[j], query)
            }
        }
        println("PHOTOSTATS $name same=${same.sorted()}")
        println("PHOTOSTATS $name other: max ${other.max()} of ${other.size}")
        return Scores(same, other)
    }

    private val hard: (Random) -> Scenes.View = {
        Scenes.randomView(it, turn = 45.0, zoom = 1.6, shift = 100.0, tilt = 8e-4)
            .copy(gain = it.nextDouble(0.25, 0.5), noise = 8.0)
    }

    @Test
    fun `the same scene matches from a step aside in other light, unrelated scenes don't`() {
        val scores = scores("synthetic", scenes) { Scenes.randomView(it) }
        assertTrue(scores.same.min() >= PhotoMatcher.Level.STRICT.minScore)
        assertTrue(scores.other.max() < PhotoMatcher.Level.EASY.minScore)
    }

    @Test
    fun `most hard views still match - turned 45 degrees, dim and noisy`() {
        val scores = scores("synthetic-hard", scenes, hard)
        assertTrue(scores.same.count { it >= PhotoMatcher.Level.NORMAL.minScore } >= scores.same.size * 3 / 4)
        assertTrue(scores.other.max() < PhotoMatcher.Level.EASY.minScore)
    }

    @Test
    fun `a phone held sideways matches`() {
        val photo = registered(scenes[0])
        val sideways = Scenes.look(scenes[0], Scenes.View(gain = 0.8)).rotated(90).fitWithin(PhotoMatcher.WORKING_SIZE)
        assertTrue(PhotoMatcher.score(Orb.detect(photo), Orb.detect(sideways)) >= PhotoMatcher.Level.STRICT.minScore)
    }

    @Test
    fun `bare walls and repeating patterns can't be registered`() {
        for (scene in scenes) {
            assertTrue(PhotoMatcher.distinctiveness(registered(scene)) >= PhotoMatcher.MIN_DISTINCTIVENESS)
        }
        val wall = GrayImage(480, 360, ByteArray(480 * 360) { (150 + it % 480 / 60).toByte() })
        assertEquals(0, PhotoMatcher.distinctiveness(wall))
        // Bathroom tiles: dark squares in light grout.
        val tiles = GrayImage(
            480,
            360,
            ByteArray(480 * 360) {
                if (it % 480 % 40 < 4 ||
                    it / 480 % 40 < 4
                ) {
                    -56
                } else {
                    40
                }
            }
        )
        assertTrue(PhotoMatcher.distinctiveness(tiles) < PhotoMatcher.MIN_DISTINCTIVENESS)
    }

    @Test
    fun `real photos, when PHOTO_SAMPLES is set`() {
        val dir = System.getenv("PHOTO_SAMPLES")
        assumeTrue(dir != null)
        val real = File(dir!!).listFiles().orEmpty().sorted().mapNotNull { ImageIO.read(it) }.flatMap { image ->
            val w = image.width / 2
            val h = image.height / 2
            listOf(0 to 0, w to 0, 0 to h, w to h).map { (x, y) -> Scenes.fromImage(image.getSubimage(x, y, w, h)) }
        }
        // Only scenes that pass registration, as in the app.
        val usable = real.filter { PhotoMatcher.distinctiveness(registered(it)) >= PhotoMatcher.MIN_DISTINCTIVENESS }
        println("PHOTOSTATS real: ${usable.size} of ${real.size} scenes can be registered")
        val scores = scores("real", usable) { Scenes.randomView(it) }
        assertTrue(scores.same.min() >= PhotoMatcher.Level.NORMAL.minScore)
        assertTrue(scores.other.max() < PhotoMatcher.Level.EASY.minScore)
        scores("real-hard", usable, hard)
    }

    @Test
    fun `images scale by averaging and turn by right angles`() {
        val image = GrayImage(4, 2, byteArrayOf(0, 2, 4, 6, 10, 12, 14, 16))
        assertArrayEquals(byteArrayOf(6, 10), image.scaled(2, 1).pixels)
        assertEquals(2, image.fitWithin(2).width)
        assertEquals(image, image.fitWithin(4))
        val small = GrayImage(3, 2, byteArrayOf(1, 2, 3, 4, 5, 6))
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), small.rotated(90).pixels)
        assertArrayEquals(byteArrayOf(3, 6, 2, 5, 1, 4), small.rotated(270).pixels)
        assertArrayEquals(byteArrayOf(6, 5, 4, 3, 2, 1), small.rotated(180).pixels)
        assertArrayEquals(small.pixels, small.turned(0.0, 1.0).pixels)
    }
}
