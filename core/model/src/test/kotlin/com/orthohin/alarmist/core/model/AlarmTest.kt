package com.orthohin.alarmist.core.model

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmTest {
    private val seven = LocalTime.of(7, 0)

    @Test
    fun `defaults describe a plain one-off alarm`() {
        val a = Alarm(time = seven)
        assertEquals(Alarm.NEW_ID, a.id)
        assertEquals(Schedule.Once(), a.schedule)
        assertEquals(AlarmSound.Default, a.sound.sound)
        assertEquals(emptyList<MissionSpec>(), a.missions)
    }

    @Test
    fun `time must be whole minutes`() {
        assertThrows(IllegalArgumentException::class.java) { Alarm(time = LocalTime.of(7, 0, 30)) }
    }

    @Test
    fun `at most five missions`() {
        val mission = MissionSpec(type = "math")
        Alarm(time = seven, missions = List(Alarm.MAX_MISSIONS) { mission })
        assertThrows(IllegalArgumentException::class.java) {
            Alarm(time = seven, missions = List(Alarm.MAX_MISSIONS + 1) { mission })
        }
    }

    @Test
    fun `weekly schedule needs a day`() {
        assertThrows(IllegalArgumentException::class.java) { Schedule.Weekly(emptySet()) }
    }

    @Test
    fun `config ranges are enforced`() {
        assertThrows(IllegalArgumentException::class.java) { SoundConfig(volumePercent = 101) }
        assertThrows(IllegalArgumentException::class.java) { SoundConfig(rampUpSeconds = -1) }
        assertThrows(IllegalArgumentException::class.java) { SnoozeConfig(minutes = 0) }
        assertThrows(IllegalArgumentException::class.java) { SnoozeConfig(maxCount = 0) }
        assertThrows(IllegalArgumentException::class.java) { WakeUpCheckConfig(delayMinutes = 0) }
        assertThrows(IllegalArgumentException::class.java) { MissionSpec(type = " ") }
        SnoozeConfig(maxCount = null)
    }
}
