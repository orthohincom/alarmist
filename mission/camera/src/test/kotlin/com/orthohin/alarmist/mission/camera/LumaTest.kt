package com.orthohin.alarmist.mission.camera

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaTest {
    @Test
    fun `row padding and pixel stride are dropped`() {
        val width = 3
        val rowStride = 8
        // Two rows of 3 pixels, padded to 8 bytes; the last row is cut short, as camera buffers often are.
        val plane = byteArrayOf(1, 2, 3, 0, 0, 0, 0, 0, 4, 5, 6)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), lumaFromPlane(ByteBuffer.wrap(plane), width, 2, rowStride))
        val interleaved = byteArrayOf(1, 9, 2, 9, 3, 9, 4, 9, 5, 9, 6)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), lumaFromPlane(ByteBuffer.wrap(interleaved), 3, 2, 6, 2))
        val tight = byteArrayOf(1, 2, 3, 4, 5, 6)
        assertArrayEquals(tight, lumaFromPlane(ByteBuffer.wrap(tight), 3, 2, 3))
    }

    @Test
    fun `rotation turns clockwise`() {
        // 1 2 3      4 1
        // 4 5 6  ->  5 2
        //            6 3
        assertArrayEquals(byteArrayOf(4, 1, 5, 2, 6, 3), rotate90(byteArrayOf(1, 2, 3, 4, 5, 6), 3, 2))
    }

    @Test
    fun `motion is a change in the image, not noise`() {
        val width = 640
        val height = 480
        val detector = MotionDetector()
        // A dark square on a light background.
        val still = ByteArray(width * height) {
            val x = it % width
            val y = it / width
            (if (x in 200..440 && y in 120..360) 30 else 200).toByte()
        }
        assertFalse(detector.moved(still, width, height))
        val noisy = ByteArray(still.size) { (still[it] + (it % 3) - 1).toByte() }
        assertFalse(detector.moved(noisy, width, height))
        val darker = ByteArray(still.size) { ((still[it].toInt() and 0xFF) / 2).toByte() }
        assertTrue(detector.moved(darker, width, height))
    }
}
