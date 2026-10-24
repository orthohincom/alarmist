package com.orthohin.alarmist.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orthohin.alarmist.core.common.FakeClock
import com.orthohin.alarmist.core.data.db.AlarmistDatabase
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAlarmRepositoryTest {
    private lateinit var db: AlarmistDatabase
    private lateinit var repo: RoomAlarmRepository
    private val clock = FakeClock(Instant.parse("2026-10-16T06:00:00Z"))

    private val seven = Alarm(time = LocalTime.of(7, 0), missions = listOf(MissionSpec("math"), MissionSpec("shake")))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AlarmistDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomAlarmRepository(db.alarmDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `saving a new alarm assigns an id and stores every field`() = runTest {
        val id = repo.save(seven)
        assertNotEquals(Alarm.NEW_ID, id)
        assertEquals(seven.copy(id = id), repo.getAlarm(id))
    }

    @Test
    fun `saving an existing alarm replaces it and its missions`() = runTest {
        val id = repo.save(seven)
        val edited = seven.copy(id = id, label = "Work", missions = listOf(MissionSpec("typing")))
        assertEquals(id, repo.save(edited))
        assertEquals(edited, repo.getAlarm(id))
        assertEquals(1, missionRows())
    }

    @Test
    fun `saving an alarm deleted meanwhile re-creates it`() = runTest {
        val id = repo.save(seven)
        repo.delete(id)
        repo.save(seven.copy(id = id))
        assertEquals(seven.copy(id = id), repo.getAlarm(id))
    }

    @Test
    fun `delete removes the alarm and its missions`() = runTest {
        val id = repo.save(seven)
        repo.delete(id)
        assertNull(repo.getAlarm(id))
        assertEquals(0, missionRows())
    }

    @Test
    fun `alarms are observed in time order`() = runTest {
        val late = repo.save(Alarm(time = LocalTime.of(22, 0)))
        val early = repo.save(Alarm(time = LocalTime.of(6, 0)))
        assertEquals(listOf(early, late), repo.observeAlarms().first().map { it.id })
    }

    @Test
    fun `setEnabled toggles and getEnabledAlarms filters`() = runTest {
        val a = repo.save(seven)
        val b = repo.save(Alarm(time = LocalTime.of(8, 0), schedule = Schedule.Weekly(setOf(DayOfWeek.FRIDAY))))
        repo.setEnabled(a, false)
        assertEquals(false, repo.getAlarm(a)?.enabled)
        assertEquals(listOf(b), repo.getEnabledAlarms().map { it.id })
    }

    @Test
    fun `writes record the injected clock`() = runTest {
        val id = repo.save(seven)
        assertEquals(clock.instant.toEpochMilli(), updatedAt(id))
        clock.advanceBy(java.time.Duration.ofMinutes(1))
        repo.setEnabled(id, false)
        assertEquals(clock.instant.toEpochMilli(), updatedAt(id))
    }

    private fun missionRows(): Int = db.query("SELECT COUNT(*) FROM alarm_missions", null).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun updatedAt(id: Long): Long = db.query("SELECT updated_at FROM alarms WHERE id = ?", arrayOf(id)).use {
        it.moveToFirst()
        it.getLong(0)
    }
}
