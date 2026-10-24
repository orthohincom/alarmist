package com.orthohin.alarmist.mission.steps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.SensorEvent
import android.util.Log
import java.io.File
import java.io.Writer

/**
 * Debug builds only: writes every sensor reading the steps mission sees to `files/steps-<n>.csv` in device-protected
 * storage (the mission can ring before the first unlock), so real walks and shakes can be replayed through
 * [GaitCheck] in `GaitReplayTest`. Lines, in the order they arrived: `# steps 1 detector=<bool> gyro=<bool>`, then
 * `a,<nanos>,x,y,z` (accelerometer, m/s²), `g,<nanos>,x,y,z` (gyroscope, rad/s), `s,<nanos>` (step detector).
 */
internal class SensorRecording private constructor(private val file: File, private var out: Writer?) {
    fun accel(event: SensorEvent) =
        write("a,${event.timestamp},${event.values[0]},${event.values[1]},${event.values[2]}")

    fun gyro(event: SensorEvent) =
        write("g,${event.timestamp},${event.values[0]},${event.values[1]},${event.values[2]}")

    fun step(event: SensorEvent) = write("s,${event.timestamp}")

    fun close() {
        out?.let { runCatching { it.close() } }
        out = null
        Log.d(TAG, "Sensor recording saved: ${file.absolutePath}")
    }

    private fun write(line: String) {
        val writer = out ?: return
        runCatching {
            writer.write(line)
            writer.write("\n")
        }.onFailure {
            // A full disk mustn't break the mission: stop recording.
            Log.d(TAG, "Sensor recording stopped: $it")
            runCatching { writer.close() }
            out = null
        }
    }

    companion object {
        private const val TAG = "Alarmist"

        /** A new recording, or null in a release build (or if the file can't be made). */
        fun start(context: Context, detector: Boolean, gyro: Boolean): SensorRecording? {
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
            return runCatching {
                val dir = context.createDeviceProtectedStorageContext().filesDir
                val last = dir.list().orEmpty().mapNotNull {
                    NAME.matchEntire(it)?.groupValues?.get(1)?.toInt()
                }.maxOrNull()
                val file = File(dir, "steps-${(last ?: 0) + 1}.csv")
                val out = file.bufferedWriter()
                out.write("# steps 1 detector=$detector gyro=$gyro\n")
                Log.d(TAG, "Recording sensors to ${file.absolutePath}")
                SensorRecording(file, out)
            }.getOrNull()
        }

        private val NAME = Regex("""steps-(\d+)\.csv""")
    }
}
