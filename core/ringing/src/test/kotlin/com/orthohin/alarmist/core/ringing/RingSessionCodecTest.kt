package com.orthohin.alarmist.core.ringing

import com.orthohin.alarmist.core.model.MissionProgress
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.RingSession.State.CheckPending
import com.orthohin.alarmist.core.model.RingSession.State.Checking
import com.orthohin.alarmist.core.model.RingSession.State.Ringing
import com.orthohin.alarmist.core.model.RingSession.State.Snoozed
import com.orthohin.alarmist.core.model.RingSessions
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RingSessionCodecTest {
    private val t0 = Instant.parse("2026-10-16T07:00:00Z")

    @Test
    fun `round trip`() {
        val sessions = RingSessions(
            listOf(
                RingSession(1, t0, Ringing(t0.plusSeconds(1))),
                RingSession(42, t0, Snoozed(t0.plusSeconds(300)), 2, 1),
                RingSession(7, t0, Ringing(t0), mission = MissionProgress(2, t0.plusSeconds(30))),
                RingSession(8, t0, CheckPending(t0.plusSeconds(300), 90), 1),
                RingSession(9, t0, Checking(t0.plusSeconds(100)))
            )
        )
        assertEquals(sessions, RingSessionCodec.decode(RingSessionCodec.encode(sessions)))
        assertEquals(RingSessions(), RingSessionCodec.decode(RingSessionCodec.encode(RingSessions())))
    }

    @Test
    fun `stable format`() {
        assertEquals(
            "1\n5;1792134000000;1;S;1792134300000;0;",
            RingSessionCodec.encode(RingSessions(listOf(RingSession(5, t0, Snoozed(t0.plusSeconds(300)), 1))))
        )
        assertEquals(
            "1\n5;1792134000000;0;C;1792134300000;0;;90",
            RingSessionCodec.encode(RingSessions(listOf(RingSession(5, t0, CheckPending(t0.plusSeconds(300), 90)))))
        )
    }

    @Test
    fun `lines without unansweredRings still load`() {
        assertEquals(
            RingSessions(listOf(RingSession(5, t0, Snoozed(t0.plusSeconds(300)), 1))),
            RingSessionCodec.decode("1\n5;1792134000000;1;S;1792134300000")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown version`() {
        RingSessionCodec.decode("2\n1;0;0;R;0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupt line`() {
        RingSessionCodec.decode("1\n1;0;R;0")
    }
}
