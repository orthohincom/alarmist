package com.orthohin.alarmist.core.model

import com.orthohin.alarmist.core.model.RingSession.State.CheckPending
import com.orthohin.alarmist.core.model.RingSession.State.Checking
import com.orthohin.alarmist.core.model.RingSession.State.Ringing
import com.orthohin.alarmist.core.model.RingSession.State.Snoozed
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RingSessionsTest {
    private val t0 = Instant.parse("2026-10-16T07:00:00Z")
    private fun minutes(n: Long): Instant = t0.plusSeconds(n * 60)

    private val snooze = SnoozeConfig(minutes = 5, maxCount = 2)

    @Test
    fun `a fired alarm rings`() {
        val s = RingSessions().fired(1, t0)
        assertEquals(RingSession(1, t0, Ringing(t0)), s.current)
    }

    @Test
    fun `snooze waits the configured minutes and counts`() {
        val s = RingSessions().fired(1, t0).snooze(1, minutes(1), snooze)!!
        assertEquals(RingSession(1, t0, Snoozed(minutes(6)), snoozeCount = 1), s[1])
        assertNull(s.current)
    }

    @Test
    fun `snooze ending rings again and keeps the count`() {
        val s = RingSessions().fired(1, t0).snooze(1, t0, snooze)!!.snoozeEnded(1, minutes(5))
        assertEquals(RingSession(1, t0, Ringing(minutes(5)), snoozeCount = 1), s.current)
    }

    @Test
    fun `snooze stops at the limit`() {
        var s = RingSessions().fired(1, t0)
        repeat(2) { s = s.snooze(1, t0, snooze)!!.snoozeEnded(1, t0) }
        assertNull(s.snooze(1, t0, snooze))
    }

    @Test
    fun `unlimited snooze never stops`() {
        var s = RingSessions().fired(1, t0)
        repeat(50) { s = s.snooze(1, t0, snooze.copy(maxCount = null))!!.snoozeEnded(1, t0) }
        assertEquals(50, s[1]!!.snoozeCount)
    }

    @Test
    fun `snooze is refused when disabled, already snoozed or unknown`() {
        val s = RingSessions().fired(1, t0)
        assertNull(s.snooze(1, t0, snooze.copy(enabled = false)))
        assertNull(s.snooze(1, t0, snooze)!!.snooze(1, t0, snooze))
        assertNull(s.snooze(2, t0, snooze))
    }

    @Test
    fun `snoozeEnded ignores an alarm that is not snoozed`() {
        val s = RingSessions().fired(1, t0)
        assertEquals(s, s.snoozeEnded(1, minutes(3)))
        assertEquals(s, s.snoozeEnded(2, minutes(3)))
    }

    @Test
    fun `firing again starts a fresh session`() {
        val s = RingSessions().fired(1, t0).snooze(1, t0, snooze)!!.fired(1, minutes(10))
        assertEquals(listOf(RingSession(1, minutes(10), Ringing(minutes(10)))), s.sessions)
    }

    @Test
    fun `dismiss ends the session`() {
        val s = RingSessions().fired(1, t0).fired(2, t0).dismiss(1)
        assertEquals(listOf(2L), s.sessions.map { it.alarmId })
        assertEquals(RingSessions(), s.dismiss(2))
    }

    @Test
    fun `a second alarm waits until the first is snoozed or dismissed`() {
        val s = RingSessions().fired(1, t0).fired(2, minutes(1))
        assertEquals(1L, s.current!!.alarmId)
        assertEquals(2L, s.snooze(1, minutes(2), snooze)!!.current!!.alarmId)
        assertEquals(2L, s.dismiss(1).current!!.alarmId)
    }

    @Test
    fun `an alarm back from snooze waits behind the one ringing now`() {
        val s = RingSessions().fired(1, t0).snooze(1, t0, snooze)!!.fired(2, minutes(1)).snoozeEnded(1, minutes(5))
        assertEquals(2L, s.current!!.alarmId)
        assertEquals(1L, s.dismiss(2).current!!.alarmId)
    }

    private val silence = AutoSilenceConfig(ringMinutes = 3, snoozeMinutes = 5, maxRings = 3)

    @Test
    fun `an unanswered ring goes quiet and comes back`() {
        val s = RingSessions().fired(1, t0).timedOut(1, minutes(3), silence)
        assertEquals(RingSession(1, t0, Snoozed(minutes(8)), unansweredRings = 1), s[1])
        assertEquals(Ringing(minutes(8)), s.snoozeEnded(1, minutes(8))[1]!!.state)
    }

    @Test
    fun `the last unanswered ring ends the session`() {
        var s = RingSessions().fired(1, t0)
        repeat(2) { s = s.timedOut(1, t0, silence).snoozeEnded(1, t0) }
        assertEquals(2, s[1]!!.unansweredRings)
        assertEquals(RingSessions(), s.timedOut(1, t0, silence))
    }

    @Test
    fun `timing out changes nothing when disabled or not ringing`() {
        val s = RingSessions().fired(1, t0)
        assertEquals(s, s.timedOut(1, t0, silence.copy(enabled = false)))
        val snoozed = s.snooze(1, t0, snooze)!!
        assertEquals(snoozed, snoozed.timedOut(1, t0, silence))
        assertEquals(s, s.timedOut(2, t0, silence))
    }

    @Test
    fun `user snoozes and unanswered rings are counted apart`() {
        val s = RingSessions().fired(1, t0).timedOut(1, t0, silence).snoozeEnded(1, t0).snooze(1, t0, snooze)!!
        assertEquals(1, s[1]!!.snoozeCount)
        assertEquals(1, s[1]!!.unansweredRings)
    }

    private fun seconds(n: Long): Instant = t0.plusSeconds(n)

    @Test
    fun `opening the missions mutes for 30 seconds and keeps progress`() {
        val s = RingSessions().fired(1, t0).missionsOpened(1, t0)
        assertEquals(MissionProgress(0, seconds(30)), s[1]!!.mission)
        assertFalse(s[1]!!.isAudible(seconds(29)))
        assertTrue(s[1]!!.isAudible(seconds(30)))
        val again = s.missionCompleted(1, 0, 3, seconds(10)).missionsLeft(1, seconds(20)).missionsOpened(1, seconds(60))
        assertEquals(MissionProgress(1, seconds(90)), again[1]!!.mission)
        assertEquals(s.current!!.state, again.current!!.state) // Still the same ring: no restart of the sound.
    }

    @Test
    fun `activity extends the mute in steps of at least 5 seconds`() {
        val s = RingSessions().fired(1, t0).missionsOpened(1, t0)
        assertSame(s, s.missionActivity(1, seconds(4)))
        assertEquals(seconds(35), s.missionActivity(1, seconds(5))[1]!!.mission!!.mutedUntil)
        val idle = RingSessions().fired(2, t0)
        assertSame(idle, idle.missionActivity(2, seconds(10))) // No missions open: nothing to extend.
    }

    @Test
    fun `completing the last mission ends the session, a stale report changes nothing`() {
        val s = RingSessions().fired(1, t0).missionsOpened(1, t0).missionCompleted(1, 0, 2, seconds(10))
        assertEquals(MissionProgress(1, seconds(40)), s[1]!!.mission)
        assertSame(s, s.missionCompleted(1, 0, 2, seconds(11)))
        assertNull(s.missionCompleted(1, 1, 2, seconds(20))[1])
    }

    @Test
    fun `leaving the missions rings at once`() {
        val s = RingSessions().fired(1, t0).missionsOpened(1, t0).missionsLeft(1, seconds(3))
        assertTrue(s[1]!!.isAudible(seconds(3)))
        assertEquals(0, s[1]!!.mission!!.index)
    }

    @Test
    fun `snoozing and timing out drop mission progress`() {
        val open = RingSessions().fired(1, t0).missionsOpened(1, t0).missionCompleted(1, 0, 3, t0)
        assertNull(open.snooze(1, t0, snooze)!![1]!!.mission)
        assertNull(open.timedOut(1, t0, silence)[1]!!.mission)
        assertEquals(open, open.missionsOpened(2, t0)) // Unknown alarm.
    }

    @Test(expected = IllegalArgumentException::class)
    fun `one session per alarm`() {
        RingSessions(listOf(RingSession(1, t0, Ringing(t0)), RingSession(1, t0, Snoozed(t0))))
    }

    private val check = WakeUpCheckConfig(enabled = true, delayMinutes = 5, responseSeconds = 60)

    @Test
    fun `dismissing with the wake-up check waits, asks, then rings again`() {
        var s = RingSessions().fired(1, t0).snooze(1, t0, snooze)!!.snoozeEnded(1, minutes(5))
        s = s.dismissed(1, minutes(6), check)
        assertEquals(CheckPending(minutes(11), 60), s[1]!!.state)
        assertEquals(minutes(11), s[1]!!.wakeAt)
        assertNull(s.current)
        assertSame(s, s.checkTimer(1, minutes(10))) // early: no change
        s = s.checkTimer(1, minutes(11))
        assertEquals(Checking(minutes(12)), s[1]!!.state)
        assertSame(s, s.checkTimer(1, minutes(11)))
        s = s.checkTimer(1, minutes(12))
        assertEquals(RingSession(1, t0, Ringing(minutes(12)), snoozeCount = 1), s.current)
    }

    @Test
    fun `the check shown is the one asking, else the next to ask`() {
        var s = RingSessions().fired(1, t0).fired(2, t0).fired(3, t0)
        assertNull(s.checking)
        s = s.dismissed(1, t0, check).dismissed(2, minutes(1), check)
        assertEquals(1L, s.checking!!.alarmId)
        s = s.checkTimer(2, minutes(6))
        assertEquals(2L, s.checking!!.alarmId)
        assertEquals(3L, s.current!!.alarmId)
    }

    @Test
    fun `a late check timer still gives the full response time`() {
        val s = RingSessions().fired(1, t0).dismissed(1, t0, check).checkTimer(1, minutes(30))
        assertEquals(Checking(minutes(31)), s[1]!!.state)
    }

    @Test
    fun `answering the check ends the session, answering elsewhere does nothing`() {
        val s = RingSessions().fired(1, t0).dismissed(1, t0, check)
        assertEquals(RingSessions(), s.awake(1))
        assertEquals(RingSessions(), s.checkTimer(1, minutes(5)).awake(1))
        val ringing = RingSessions().fired(1, t0)
        assertSame(ringing, ringing.awake(1))
    }

    @Test
    fun `without the check, or dismissed again during one, the session ends`() {
        val s = RingSessions().fired(1, t0)
        assertEquals(RingSessions(), s.dismissed(1, t0, WakeUpCheckConfig()))
        assertEquals(RingSessions(), s.dismissed(1, t0, check).dismissed(1, t0, check))
    }

    @Test
    fun `a re-ring after the check starts missions over with a fresh auto-silence count`() {
        val silence = AutoSilenceConfig(maxRings = 3)
        var s = RingSessions().fired(1, t0).timedOut(1, minutes(3), silence).snoozeEnded(1, minutes(8))
        s = s.missionsOpened(1, minutes(8)).missionCompleted(1, 0, total = 1, now = minutes(9), check = check)
        assertEquals(CheckPending(minutes(14), 60), s[1]!!.state)
        assertNull(s[1]!!.mission)
        s = s.checkTimer(1, minutes(14)).checkTimer(1, minutes(15))
        assertEquals(0, s[1]!!.unansweredRings)
        assertNull(s[1]!!.mission)
        assertTrue(s[1]!!.isAudible(minutes(15)))
    }
}
