package com.orthohin.alarmist.mission.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.orthohin.alarmist.mission.camera.CameraFrame
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

/** The registered photo: a JPEG, [PhotoMatcher.WORKING_SIZE] px on the long side, stored as Base64 in the config. */
internal object PhotoCodec {
    private const val QUALITY = 85

    fun encode(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream()
        fit(bitmap).compress(Bitmap.CompressFormat.JPEG, QUALITY, bytes)
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /** Null if [base64] isn't a picture (a broken config). */
    fun decode(base64: String): Bitmap? = runCatching {
        val bytes = Base64.getDecoder().decode(base64)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun fit(bitmap: Bitmap): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= PhotoMatcher.WORKING_SIZE) return bitmap
        val scale = PhotoMatcher.WORKING_SIZE.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(),
            true
        )
    }

    /** Brightness only, as the camera's Y plane has it (BT.601 weights). */
    fun gray(bitmap: Bitmap): GrayImage {
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luma = ByteArray(colors.size) {
            val c = colors[it]
            ((77 * (c shr 16 and 0xFF) + 150 * (c shr 8 and 0xFF) + 29 * (c and 0xFF)) shr 8).toByte()
        }
        return GrayImage(bitmap.width, bitmap.height, luma)
    }
}

/** A camera frame upright, at the size photos are compared. */
internal fun CameraFrame.gray(): GrayImage =
    GrayImage(width, height, luma).fitWithin(PhotoMatcher.WORKING_SIZE).rotated(rotationDegrees)

/** A photo taken for registering: [photo] is the config's Base64 JPEG; [recognisable] if it has enough detail. */
internal class Registration(val photo: String, val recognisable: Boolean) {
    companion object {
        /** Runs on the analysis thread: encoding and the detail check take a moment. */
        fun of(frame: CameraFrame): Registration {
            val photo = PhotoCodec.encode(frame.toBitmap())
            // Judged after JPEG compression, as the ringing screen will see it.
            val gray = PhotoCodec.decode(photo)?.let(PhotoCodec::gray)
            return Registration(
                photo,
                gray != null && PhotoMatcher.distinctiveness(gray) >= PhotoMatcher.MIN_DISTINCTIVENESS
            )
        }
    }
}

/** What the runner learns from a frame: its [score] against the photo, and whether it's too [dark] to tell. */
internal data class FrameResult(val score: Int, val dark: Boolean)

/** Scores camera frames against the registered [photo]. Only for the analysis thread. */
internal class LiveMatcher(private val photo: Bitmap) {
    // Worked out on the analysis thread with the first frame, not on the main thread.
    private val reference by lazy(LazyThreadSafetyMode.NONE) { Orb.detect(PhotoCodec.gray(photo)) }

    fun analyze(frame: CameraFrame): FrameResult {
        val image = frame.gray()
        var sum = 0L
        for (b in image.pixels) sum += b.toInt() and 0xFF
        return FrameResult(PhotoMatcher.score(reference, Orb.detect(image)), sum / image.pixels.size < DARK)
    }

    private companion object {
        // Mean brightness, 0–255.
        const val DARK = 35
    }
}
