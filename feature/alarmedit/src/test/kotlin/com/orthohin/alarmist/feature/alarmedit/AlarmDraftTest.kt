package com.orthohin.alarmist.feature.alarmedit

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.SoundConfig
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDraftTest {
    private val alarm = Alarm(id = 3, time = LocalTime.of(7, 30))

    @Test
    fun `a new alarm starts at the next whole hour`() {
        val now = Instant.parse("2026-10-16T05:12:45Z")
        assertEquals(LocalTime.of(12, 0), newAlarm(now, ZoneId.of("Asia/Dhaka")).time)
        assertEquals(LocalTime.of(0, 0), newAlarm(Instant.parse("2026-10-16T23:30:00Z"), ZoneId.of("UTC")).time)
    }

    @Test
    fun `the volume slider moves in 5 percent steps`() {
        assertEquals(0, volumeStep(2.4f))
        assertEquals(5, volumeStep(2.6f))
        assertEquals(100, volumeStep(99.9f))
    }

    @Test
    fun `days switch between one-off and weekly`() {
        val weekly = alarm.toggleDay(MONDAY).toggleDay(FRIDAY)
        assertEquals(Schedule.Weekly(setOf(MONDAY, FRIDAY)), weekly.schedule)
        assertEquals(Schedule.Once(), weekly.toggleDay(MONDAY).toggleDay(FRIDAY).schedule)
    }

    @Test
    fun `daily selects all seven days, and off makes it one-off`() {
        val daily = alarm.toggleDay(MONDAY).withDaily(true)
        assertEquals(Schedule.Weekly(DayOfWeek.entries.toSet()), daily.schedule)
        assertTrue(daily.isDaily)
        assertFalse(daily.toggleDay(MONDAY).isDaily)
        assertEquals(Schedule.Once(), daily.withDaily(false).schedule)
    }

    @Test
    fun `volume 0 is allowed only with vibration`() {
        assertEquals(0, alarm.withVolume(0).sound.volumePercent)
        val noVibration = alarm.withVibrate(false)
        assertEquals(MIN_AUDIBLE_VOLUME, noVibration.withVolume(0).sound.volumePercent)
    }

    @Test
    fun `vibration stays on while vibrate only`() {
        val vibrateOnly = alarm.withVolume(0)
        assertFalse(vibrateOnly.canTurnOffVibration)
        assertTrue(vibrateOnly.withVibrate(false).sound.vibrate)
        assertFalse(alarm.withVibrate(false).sound.vibrate)
    }

    @Test
    fun `saving turns the alarm on, trims the label and drops a one-off date`() {
        val draft = alarm.copy(enabled = false, label = " Gym ", schedule = Schedule.Once(LocalDate.of(2026, 1, 1)))
        val saved = draft.forSave(alarm)
        assertTrue(saved.enabled)
        assertEquals("Gym", saved.label)
        assertEquals(Schedule.Once(), saved.schedule)
    }

    @Test
    fun `a skip survives only an edit that keeps the time and days`() {
        val skipping = alarm.copy(schedule = Schedule.Weekly(setOf(MONDAY)), skippedDate = LocalDate.of(2026, 10, 19))
        assertEquals(skipping.skippedDate, skipping.copy(label = "x").forSave(skipping).skippedDate)
        assertNull(skipping.withTime(8, 0).forSave(skipping).skippedDate)
        assertNull(skipping.toggleDay(FRIDAY).forSave(skipping).skippedDate)
    }

    @Test
    fun `a bundled sound becomes the backup, other sounds keep it`() {
        val siren = alarm.withSound(AlarmSound.Bundled("siren"))
        assertEquals(AlarmSound.Bundled("siren"), siren.sound.backup)
        val imported = siren.withSound(AlarmSound.Imported("song.mp3"))
        assertEquals(AlarmSound.Imported("song.mp3"), imported.sound.sound)
        assertEquals(AlarmSound.Bundled("siren"), imported.sound.backup)
    }

    @Test
    fun `missions are added up to the limit, replaced and removed`() {
        var a = alarm
        repeat(Alarm.MAX_MISSIONS + 1) { a = a.withMission(null, MissionSpec("m$it")) }
        assertEquals(Alarm.MAX_MISSIONS, a.missions.size)
        assertFalse(a.canAddMission)
        a = a.withMission(1, MissionSpec("x")).withoutMission(0)
        assertEquals(listOf("x", "m2", "m3", "m4"), a.missions.map { it.type })
        assertEquals(a, a.withoutMission(9).withMission(9, MissionSpec("y")))
    }

    @Test
    fun `defaults are among the offered choices`() {
        assertTrue(SoundConfig().rampUpSeconds in RAMP_UP_SECONDS)
        assertTrue(alarm.snooze.minutes in SNOOZE_MINUTES)
        assertTrue(alarm.snooze.maxCount in SNOOZE_MAX_COUNTS)
    }
}
