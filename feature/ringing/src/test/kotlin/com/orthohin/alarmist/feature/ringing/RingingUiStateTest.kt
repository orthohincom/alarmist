package com.orthohin.alarmist.feature.ringing

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AntiCheatConfig
import com.orthohin.alarmist.core.model.MissionProgress
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RingingUiStateTest {
    private val t0 = Instant.parse("2026-10-16T07:00:00Z")
    private val session = RingSession(1, t0, RingSession.State.Ringing(t0), snoozeCount = 1)
    private val alarm =
        Alarm(id = 1, time = LocalTime.of(7, 0), label = "Gym", snooze = SnoozeConfig(minutes = 10, maxCount = 3))

    @Test
    fun `nothing ringing closes the screen`() {
        assertEquals(RingingUiState.Idle, ringingUiState(null, alarm))
    }

    @Test
    fun `the wake-up check counts down from the dismissal, then asks with the response time`() {
        val checked = alarm.copy(
            wakeUpCheck = WakeUpCheckConfig(enabled = true, delayMinutes = 5, responseSeconds = 60)
        )
        val askAt = t0.plusSeconds(300)
        assertEquals(
            RingingUiState.CheckWaiting(1, "Gym", since = t0, askAt = askAt),
            ringingUiState(session.copy(state = RingSession.State.CheckPending(askAt, 60)), checked)
        )
        assertEquals(
            RingingUiState.CheckAsking(1, "Gym", since = askAt, until = askAt.plusSeconds(60)),
            ringingUiState(session.copy(state = RingSession.State.Checking(askAt.plusSeconds(60))), checked)
        )
    }

    @Test
    fun `shows label, snooze minutes and snoozes left`() {
        assertEquals(
            RingingUiState.Ringing(1, t0, "Gym", SnoozeOption(10, 2)),
            ringingUiState(session, alarm)
        )
    }

    @Test
    fun `no snooze at the limit or when off, unlimited has no count`() {
        val atLimit = session.copy(snoozeCount = 3)
        assertEquals(null, (ringingUiState(atLimit, alarm) as RingingUiState.Ringing).snooze)
        val off = alarm.copy(snooze = SnoozeConfig(enabled = false))
        assertEquals(null, (ringingUiState(session, off) as RingingUiState.Ringing).snooze)
        val unlimited = alarm.copy(snooze = SnoozeConfig(minutes = 5, maxCount = null))
        assertEquals(SnoozeOption(5, null), (ringingUiState(session, unlimited) as RingingUiState.Ringing).snooze)
    }

    @Test
    fun `a locked volume blocks the volume keys`() {
        val locked = alarm.copy(antiCheat = AntiCheatConfig(lockVolume = true))
        assertEquals(true, (ringingUiState(session, locked) as RingingUiState.Ringing).lockVolume)
        assertEquals(false, (ringingUiState(session, null) as RingingUiState.Ringing).lockVolume)
    }

    @Test
    fun `missions hide dismiss`() {
        val withMission = alarm.copy(missions = listOf(MissionSpec("math")))
        assertEquals(false, (ringingUiState(session, withMission) as RingingUiState.Ringing).canDismiss)
    }

    @Test
    fun `mission progress comes from the session`() {
        val withMission = alarm.copy(missions = listOf(MissionSpec("math"), MissionSpec("shake")))
        val solving = session.copy(mission = MissionProgress(1, t0))
        val state = ringingUiState(solving, withMission) as RingingUiState.Ringing
        assertEquals(1, state.missionIndex)
        assertEquals(2, state.missions.size)
    }

    @Test
    fun `an unreadable alarm uses defaults and can be dismissed`() {
        assertEquals(
            RingingUiState.Ringing(1, t0, "", SnoozeOption(5, 2)),
            ringingUiState(session, null)
        )
    }
}
