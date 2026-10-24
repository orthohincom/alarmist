package com.orthohin.alarmist.mission.steps

import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays phone recordings (`SensorRecording`, debug builds; gzipped) from `src/test/resources/gait/` through
 * [StepSources], exactly as the runner feeds it. `walk-*` must count at least [WALK_SHARE] of the candidate steps (of
 * the busier source); `shake-*` at most [SHAKE_SHARE]. Other names (`pump-updown`: an up-and-down arm pump, which
 * passes as walking by design) only print their counts.
 */
class GaitReplayTest {
    private class Replay(val name: String, val credited: Int, val candidates: Int, val log: List<String>)

    private fun replay(file: File): Replay {
        val lines = GZIPInputStream(file.inputStream()).bufferedReader().readLines()
        val log = mutableListOf<String>()
        val candidates = mutableMapOf<String, Int>()
        val sources = StepSources(hasGyro = "gyro=true" in lines.first()) { source, verdict, _ ->
            log += "$source: ${verdict.describe()}"
            candidates[source] = (candidates[source] ?: 0) + 1
        }
        var credited = 0
        for (line in lines.drop(1)) {
            val parts = line.split(',')
            val nanos = parts.getOrNull(1)?.toLongOrNull() ?: continue
            // The last line can be cut off if the file was pulled while the mission was still writing.
            val values = parts.drop(2).map { it.toFloatOrNull() ?: continue }
            if (parts[0] != "s" && values.size != 3) continue
            when (parts[0]) {
                "a" -> credited += sources.onAccel(values[0], values[1], values[2], nanos)

                "g" -> sources.onGyro(values[0], values[1], values[2], nanos)

                // Recorded only if the phone used its detector.
                "s" -> credited += sources.onDetectorStep(nanos)
            }
        }
        return Replay(file.name, credited, candidates.values.maxOrNull() ?: 0, log)
    }

    @Test
    fun `recorded walks count, recorded shaking doesn't`() {
        val dir = javaClass.getResource("/gait")?.let { File(it.toURI()) } ?: return
        val replays = dir.listFiles().orEmpty().filter { it.name.endsWith(".csv.gz") }.sorted().map(::replay)
        replays.forEach { println("REPLAY ${it.name}: ${it.credited} counted, ${it.candidates} candidates") }
        val failures = replays.filter {
            when {
                it.name.startsWith("walk") -> it.credited < it.candidates * WALK_SHARE
                it.name.startsWith("shake") -> it.credited > it.candidates * SHAKE_SHARE
                else -> false
            }
        }
        assertTrue(
            failures.joinToString("\n\n") { r ->
                "${r.name}: ${r.credited} counted of ${r.candidates} candidates\n" + r.log.joinToString("\n")
            },
            failures.isEmpty()
        )
    }

    companion object {
        private const val WALK_SHARE = 0.85
        private const val SHAKE_SHARE = 0.25
    }
}
