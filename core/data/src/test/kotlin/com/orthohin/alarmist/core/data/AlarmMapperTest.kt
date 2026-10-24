package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.data.db.AlarmMapper
import com.orthohin.alarmist.core.data.db.AlarmWithMissions
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.AntiCheatConfig
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.SoundConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmMapperTest {
    private fun roundTrip(alarm: Alarm): Alarm =
        AlarmMapper.toModel(AlarmWithMissions(AlarmMapper.toEntity(alarm, 0), AlarmMapper.toMissionEntities(alarm)))

    @Test
    fun `default alarm survives a round trip`() {
        val alarm = Alarm(id = 1, time = LocalTime.of(7, 30))
        assertEquals(alarm, roundTrip(alarm))
    }

    @Test
    fun `every non-default field survives a round trip`() {
        val alarm = Alarm(
            id = 42,
            time = LocalTime.of(23, 59),
            schedule = Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            enabled = false,
            label = "Gym",
            skippedDate = LocalDate.of(2026, 10, 19),
            sound = SoundConfig(
                sound = AlarmSound.Custom("content://media/external/audio/media/7"),
                backup = AlarmSound.Bundled("birds"),
                volumePercent = 60,
                rampUpSeconds = 0,
                vibrate = false
            ),
            snooze = SnoozeConfig(enabled = false, minutes = 10, maxCount = null),
            autoSilence = AutoSilenceConfig(enabled = false, ringMinutes = 10, snoozeMinutes = 1, maxRings = 5),
            missions = listOf(MissionSpec("math", 2, """{"level":3}"""), MissionSpec("shake")),
            wakeUpCheck = WakeUpCheckConfig(enabled = true, delayMinutes = 15, responseSeconds = 30),
            antiCheat = AntiCheatConfig(relaunchIfLeft = true, lockVolume = true, powerMenuGuard = true)
        )
        assertEquals(alarm, roundTrip(alarm))
    }

    @Test
    fun `imported and bundled sounds survive a round trip`() {
        for (sound in listOf(AlarmSound.Imported("Morning song.mp3"), AlarmSound.Bundled("siren"))) {
            val alarm = Alarm(id = 2, time = LocalTime.NOON, sound = SoundConfig(sound = sound))
            assertEquals(alarm, roundTrip(alarm))
        }
    }

    @Test
    fun `one-off alarm on a date survives a round trip`() {
        val alarm = Alarm(id = 3, time = LocalTime.MIDNIGHT, schedule = Schedule.Once(LocalDate.of(2027, 1, 1)))
        assertEquals(alarm, roundTrip(alarm))
    }

    @Test
    fun `missions come back in position order`() {
        val alarm = Alarm(id = 5, time = LocalTime.NOON, missions = listOf(MissionSpec("a"), MissionSpec("b")))
        val rows = AlarmMapper.toMissionEntities(alarm).reversed()
        val model = AlarmMapper.toModel(AlarmWithMissions(AlarmMapper.toEntity(alarm, 0), rows))
        assertEquals(listOf("a", "b"), model.missions.map { it.type })
    }

    @Test
    fun `day mask uses bit 0 for Monday and bit 6 for Sunday`() {
        assertEquals(1, AlarmMapper.daysToMask(setOf(DayOfWeek.MONDAY)))
        assertEquals(64, AlarmMapper.daysToMask(setOf(DayOfWeek.SUNDAY)))
        assertEquals(127, AlarmMapper.daysToMask(DayOfWeek.entries.toSet()))
        assertEquals(DayOfWeek.entries.toSet(), AlarmMapper.maskToDays(127))
    }
}
