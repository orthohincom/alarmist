package com.orthohin.alarmist.mission.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.orthohin.alarmist.mission.camera.rotate90
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeTest {
    private val frameWidth = 640
    private val frameHeight = 480

    /** A grey camera-like frame with [text] encoded as [format], [codeWidth]×[codeHeight] px, in the middle. */
    private fun frame(
        text: String,
        format: BarcodeFormat,
        codeWidth: Int,
        codeHeight: Int,
        dark: Boolean = false
    ): ByteArray {
        val matrix = MultiFormatWriter().encode(text, format, codeWidth, codeHeight)
        val luma = ByteArray(frameWidth * frameHeight) { 160.toByte() }
        val left = (frameWidth - matrix.width) / 2
        val top = (frameHeight - matrix.height) / 2
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val black = matrix[x, y] != dark
                luma[(top + y) * frameWidth + left + x] = (if (black) 20 else 235).toByte()
            }
        }
        return luma
    }

    private fun decode(luma: ByteArray, width: Int = frameWidth, height: Int = frameHeight) =
        BarcodeDecoder().decode(luma, width, height)

    @Test
    fun `reads QR codes, also inverted`() {
        assertEquals(
            Scan("alarmist-bathroom", "QR_CODE"),
            decode(frame("alarmist-bathroom", BarcodeFormat.QR_CODE, 300, 300))
        )
        assertEquals(Scan("night", "QR_CODE"), decode(frame("night", BarcodeFormat.QR_CODE, 300, 300, dark = true)))
    }

    @Test
    fun `reads product barcodes in either orientation`() {
        val ean = frame("4006381333931", BarcodeFormat.EAN_13, 400, 120)
        assertEquals(Scan("4006381333931", "EAN_13"), decode(ean))
        // The phone held the other way: the bars run across the frame's rows.
        assertEquals(
            Scan("4006381333931", "EAN_13"),
            decode(rotate90(ean, frameWidth, frameHeight), frameHeight, frameWidth)
        )
        assertEquals("CODE_128", decode(frame("TOOTHPASTE-42", BarcodeFormat.CODE_128, 500, 120))?.format)
    }

    @Test
    fun `an empty frame has no code`() {
        assertNull(decode(ByteArray(frameWidth * frameHeight) { 128.toByte() }))
    }

    @Test
    fun `codes match by text, and UPC-A matches its EAN-13 form`() {
        assertTrue(codesMatch(Scan("abc", "QR_CODE"), Scan("abc", "QR_CODE")))
        assertFalse(codesMatch(Scan("abc", "QR_CODE"), Scan("abd", "QR_CODE")))
        assertTrue(codesMatch(Scan("036000291452", "UPC_A"), Scan("0036000291452", "EAN_13")))
        assertTrue(codesMatch(Scan("0036000291452", "EAN_13"), Scan("036000291452", "UPC_A")))
        assertFalse(codesMatch(Scan("36000291452", "CODE_128"), Scan("036000291452", "UPC_A")))
    }

    @Test
    fun `a code is confirmed only when read twice in a row`() {
        val confirmer = ScanConfirmer()
        assertFalse(confirmer.onScan(Scan("123", "CODE_39")))
        assertFalse(confirmer.onScan(Scan("128", "CODE_39")))
        assertFalse(confirmer.onScan(Scan("123", "CODE_39")))
        assertTrue(confirmer.onScan(Scan("123", "CODE_39")))
    }
}
