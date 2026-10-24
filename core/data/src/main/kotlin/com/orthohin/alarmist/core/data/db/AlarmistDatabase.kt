package com.orthohin.alarmist.core.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema changes bump [version] and add a migration. There is deliberately no destructive fallback:
 * losing the user's alarms is never acceptable.
 */
@Database(
    entities = [AlarmEntity::class, MissionEntity::class, EventEntity::class, ExpectedRingEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // 2: auto-silence columns.
        AutoMigration(from = 1, to = 2),
        // 3: event log and expected rings (reliability check).
        AutoMigration(from = 2, to = 3)
    ]
)
internal abstract class AlarmistDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "alarmist.db"

        /**
         * Lives in device-protected storage, so alarms can be read and rescheduled after a reboot before
         * the user unlocks the phone (Direct Boot).
         */
        fun create(context: Context): AlarmistDatabase =
            Room.databaseBuilder(context.createDeviceProtectedStorageContext(), AlarmistDatabase::class.java, NAME)
                .build()
    }
}
