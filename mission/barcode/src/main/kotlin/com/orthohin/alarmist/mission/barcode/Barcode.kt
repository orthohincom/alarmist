package com.orthohin.alarmist.mission.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.orthohin.alarmist.mission.camera.rotate90

/** A decoded code. [format] is a ZXing `BarcodeFormat` name, stored in the mission config: never rename. */
data class Scan(val text: String, val format: String)

/** Reads barcodes and QR codes from luma frames. Not thread-safe: one per analysis thread. */
class BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to FORMATS,
                DecodeHintType.ALSO_INVERTED to true
            )
        )
    }

    /** The code in the frame, if any. 1D barcodes read only across their bars, so it also tries the frame turned. */
    fun decode(luma: ByteArray, width: Int, height: Int): Scan? =
        decodeAsIs(luma, width, height) ?: decodeAsIs(rotate90(luma, width, height), height, width)

    private fun decodeAsIs(luma: ByteArray, width: Int, height: Int): Scan? = try {
        val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        Scan(result.text, result.barcodeFormat.name)
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    private companion object {
        val FORMATS = listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR
        )
    }
}

/**
 * Accepts a code once it's read [times] times in a row. Registering a one-frame misread (1D formats like Code 39 or
 * Codabar have no checksum) would make the alarm unsolvable.
 */
class ScanConfirmer(private val times: Int = 2) {
    private var last: Scan? = null
    private var count = 0

    /** True when [scan] is confirmed. */
    fun onScan(scan: Scan): Boolean {
        if (scan == last) count++ else count = 1
        last = scan
        return count >= times
    }
}

/** True if [scanned] is the [registered] code. A UPC-A product code also reads as EAN-13 with a leading 0. */
fun codesMatch(registered: Scan, scanned: Scan): Boolean {
    if (registered.text == scanned.text) return true
    val upc = setOf(BarcodeFormat.UPC_A.name, BarcodeFormat.EAN_13.name)
    if (registered.format !in upc || scanned.format !in upc) return false
    return registered.text.padStart(EAN_13_LENGTH, '0') == scanned.text.padStart(EAN_13_LENGTH, '0')
}

private const val EAN_13_LENGTH = 13
