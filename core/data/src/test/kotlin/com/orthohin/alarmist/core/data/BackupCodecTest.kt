package com.orthohin.alarmist.core.data

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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val at = Instant.parse("2026-10-16T08:00:00Z")

    private fun roundTrip(backup: Backup) = BackupCodec.decode(BackupCodec.encode(backup, at))

    private fun reasonOf(text: String) = assertThrows(BackupException::class.java) { BackupCodec.decode(text) }.reason

    @Test
    fun `defaults survive a round trip, without ids`() {
        val backup = Backup(listOf(Alarm(time = LocalTime.of(7, 30))), AppSettings())
        assertEquals(backup, roundTrip(backup))
        val stored = Backup(listOf(Alarm(id = 9, time = LocalTime.of(7, 30))))
        assertEquals(Alarm(time = LocalTime.of(7, 30)), roundTrip(stored).alarms.single())
    }

    @Test
    fun `every non-default field survives a round trip`() {
        val alarm = Alarm(
            time = LocalTime.of(23, 59),
            schedule = Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)),
            enabled = false,
            label = "Gym \"early\" ☀",
            skippedDate = LocalDate.of(2026, 10, 19),
            sound = SoundConfig(AlarmSound.Custom("content://media/7"), AlarmSound.Bundled("birds"), 60, 0, false),
            snooze = SnoozeConfig(enabled = false, minutes = 10, maxCount = null),
            autoSilence = AutoSilenceConfig(enabled = false, ringMinutes = 10, snoozeMinutes = 1, maxRings = 5),
            missions = listOf(MissionSpec("math", 2, """{"level":3,"x":[1.50,"a"]}"""), MissionSpec("shake")),
            wakeUpCheck = WakeUpCheckConfig(enabled = true, delayMinutes = 15, responseSeconds = 30),
            antiCheat = AntiCheatConfig(relaunchIfLeft = true, lockVolume = true, powerMenuGuard = true)
        )
        val once = Alarm(time = LocalTime.NOON, schedule = Schedule.Once(LocalDate.of(2026, 12, 24)))
        val imported = Alarm(time = LocalTime.MIDNIGHT, sound = SoundConfig(sound = AlarmSound.Imported("My song.mp3")))
        val backup =
            Backup(
                listOf(alarm, once, imported),
                AppSettings(ThemeMode.DARK, dynamicColor = false, timeInput = TimeInput.DIAL)
            )
        assertEquals(backup, roundTrip(backup))
    }

    @Test
    fun `missing fields decode to the defaults`() {
        val backup = BackupCodec.decode("""{"app":"alarmist","version":1,"alarms":[{"time":"06:15"}]}""")
        assertEquals(Backup(listOf(Alarm(time = LocalTime.of(6, 15))), settings = null), backup)
        val oldSettings = BackupCodec.decode(
            """{"app":"alarmist","version":1,"settings":{"theme":"dark"},"alarms":[]}"""
        )
        assertEquals(AppSettings(ThemeMode.DARK), oldSettings.settings)
    }

    @Test
    fun `snooze maxCount null is unlimited, missing is the default`() {
        fun maxCount(snooze: String) = BackupCodec.decode(
            """{"app":"alarmist","version":1,"alarms":[{"time":"06:15","snooze":$snooze}]}"""
        ).alarms.single().snooze.maxCount
        assertEquals(null, maxCount("""{"maxCount":null}"""))
        assertEquals(SnoozeConfig().maxCount, maxCount("{}"))
    }

    @Test
    fun `other files are not backups`() {
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reasonOf("not json"))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reasonOf("[1,2]"))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reasonOf("""{"app":"other","version":1,"alarms":[]}"""))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reasonOf("""{"app":7}"""))
    }

    @Test
    fun `a newer version is refused`() {
        assertEquals(BackupException.Reason.NEWER_VERSION, reasonOf("""{"app":"alarmist","version":2,"alarms":[]}"""))
    }

    @Test
    fun `invalid values are refused`() {
        for (alarm in listOf(
            "{}",
            """{"time":"25:00"}""",
            """{"time":"07:30:15"}""",
            """{"time":"07:30","days":["funday"]}""",
            """{"time":"07:30","sound":{"volumePercent":101}}""",
            """{"time":"07:30","sound":{"imported":"a/b.mp3"}}""",
            """{"time":"07:30","enabled":"yes"}""",
            """{"time":7}""",
            """{"time":"07:30","missions":[{"config":"{}"}]}"""
        )) {
            assertEquals(
                alarm,
                BackupException.Reason.INVALID,
                reasonOf("""{"app":"alarmist","version":1,"alarms":[$alarm]}""")
            )
        }
        assertEquals(BackupException.Reason.INVALID, reasonOf("""{"app":"alarmist","alarms":[]}"""))
        assertEquals(BackupException.Reason.INVALID, reasonOf("""{"app":"alarmist","version":1}"""))
    }
}
