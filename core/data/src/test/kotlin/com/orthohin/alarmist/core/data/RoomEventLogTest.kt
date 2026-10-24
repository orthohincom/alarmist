package com.orthohin.alarmist.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orthohin.alarmist.core.common.DefaultAppDispatchers
import com.orthohin.alarmist.core.common.FakeClock
import com.orthohin.alarmist.core.data.db.AlarmistDatabase
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.MissReason
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomEventLogTest {
    private lateinit var db: AlarmistDatabase
    private lateinit var log: RoomEventLog
    private val clock = FakeClock(Instant.parse("2026-10-16T06:00:00Z"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AlarmistDatabase::class.java).build()
        log = RoomEventLog(db.eventDao(), clock, DefaultAppDispatchers)
    }

    @After
    fun tearDown() = db.close()

    private fun awaitEvents(until: (List<AlarmEvent>) -> Boolean) = runBlocking {
        withTimeout(5_000) { log.observeRecent().first(until) }
    }

    @Test
    fun `events come back newest first with every field`() {
        val missed =
            AlarmEvent(
                clock.now(),
                AlarmEvent.Type.Missed,
                7,
                clock.now() - Duration.ofHours(1),
                MissReason.PhoneOff.name
            )
        log.add(AlarmEvent(clock.now() - Duration.ofMinutes(1), AlarmEvent.Type.Rescheduled, detail = "boot"))
        log.add(missed)
        val events = awaitEvents { it.size == 2 }
        assertEquals(missed, events.first())
        assertEquals(AlarmEvent.Type.Rescheduled, events.last().type)
    }

    @Test
    fun `old events are pruned on the next add`() {
        log.add(AlarmEvent(clock.now() - Duration.ofDays(31), AlarmEvent.Type.Fired, 1))
        log.add(AlarmEvent(clock.now(), AlarmEvent.Type.Dismissed, 1))
        // Waits until the second add has pruned the first entry.
        awaitEvents { events -> events.map { it.type } == listOf(AlarmEvent.Type.Dismissed) }
    }

    @Test
    fun `expected rings are set, replaced and cleared`() = runBlocking {
        val t = clock.now()
        log.set(1, t)
        log.set(1, t + Duration.ofDays(1))
        log.set(2, t)
        log.clear(2)
        assertEquals(mapOf(1L to t + Duration.ofDays(1)), log.all())
    }
}
