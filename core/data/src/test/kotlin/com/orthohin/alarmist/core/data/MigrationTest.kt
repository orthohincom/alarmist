package com.orthohin.alarmist.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.orthohin.alarmist.core.data.db.AlarmistDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every schema version must migrate with the user's alarms intact. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AlarmistDatabase::class.java)

    @Test
    fun `1 to 2 keeps alarms and turns auto-silence on with defaults`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO alarms (id, minute_of_day, repeat_days, once_date, enabled, label, skipped_date,
                   sound_kind, sound_value, backup_sound_id, volume_percent, ramp_up_seconds, vibrate,
                   snooze_enabled, snooze_minutes, snooze_max_count, wake_check_enabled, wake_check_delay_minutes,
                   wake_check_response_seconds, relaunch_if_left, lock_volume, power_menu_guard, updated_at)
                   VALUES (1, 420, 31, NULL, 1, 'Work', NULL, 'bundled', 'default', 'default', 80, 30, 1,
                   1, 5, 3, 0, 5, 100, 0, 0, 0, 0)"""
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query(
                """SELECT label, auto_silence_enabled, auto_silence_ring_minutes, auto_silence_snooze_minutes,
                   auto_silence_max_rings FROM alarms WHERE id = 1"""
            ).use { c ->
                c.moveToFirst()
                assertEquals(listOf("Work", "1", "3", "5", "3"), (0 until 5).map(c::getString))
            }
        }
    }

    @Test
    fun `2 to 3 keeps alarms and adds the event tables`() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                """INSERT INTO alarms (id, minute_of_day, repeat_days, once_date, enabled, label, skipped_date,
                   sound_kind, sound_value, backup_sound_id, volume_percent, ramp_up_seconds, vibrate,
                   snooze_enabled, snooze_minutes, snooze_max_count, auto_silence_enabled, auto_silence_ring_minutes,
                   auto_silence_snooze_minutes, auto_silence_max_rings, wake_check_enabled, wake_check_delay_minutes,
                   wake_check_response_seconds, relaunch_if_left, lock_volume, power_menu_guard, updated_at)
                   VALUES (1, 420, 31, NULL, 1, 'Work', NULL, 'bundled', 'default', 'default', 80, 30, 1,
                   1, 5, 3, 1, 3, 5, 3, 0, 5, 100, 0, 0, 0, 0)"""
            )
        }
        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT label FROM alarms WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals("Work", c.getString(0))
            }
            db.execSQL("INSERT INTO events (at, type, alarm_id, due, detail) VALUES (1, 'Fired', 1, 1, '')")
            db.execSQL("INSERT INTO expected_rings (alarm_id, trigger_at) VALUES (1, 2)")
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
