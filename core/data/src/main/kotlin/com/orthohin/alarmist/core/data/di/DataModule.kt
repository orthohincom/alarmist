package com.orthohin.alarmist.core.data.di

import android.content.Context
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.DataStoreSettingsRepository
import com.orthohin.alarmist.core.data.EventLog
import com.orthohin.alarmist.core.data.ExpectedRings
import com.orthohin.alarmist.core.data.RoomAlarmRepository
import com.orthohin.alarmist.core.data.RoomEventLog
import com.orthohin.alarmist.core.data.SettingsRepository
import com.orthohin.alarmist.core.data.db.AlarmDao
import com.orthohin.alarmist.core.data.db.AlarmistDatabase
import com.orthohin.alarmist.core.data.db.EventDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Needs a `Clock` binding, which the app provides. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun alarmRepository(impl: RoomAlarmRepository): AlarmRepository

    @Binds
    abstract fun eventLog(impl: RoomEventLog): EventLog

    @Binds
    abstract fun expectedRings(impl: RoomEventLog): ExpectedRings

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): AlarmistDatabase = AlarmistDatabase.create(context)

        @Provides
        fun alarmDao(database: AlarmistDatabase): AlarmDao = database.alarmDao()

        @Provides
        fun eventDao(database: AlarmistDatabase): EventDao = database.eventDao()
    }
}
