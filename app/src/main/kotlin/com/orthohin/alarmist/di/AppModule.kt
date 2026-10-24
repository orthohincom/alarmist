package com.orthohin.alarmist.di

import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.common.DefaultAppDispatchers
import com.orthohin.alarmist.core.common.DeviceClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Bindings for the pure Kotlin modules, which can't use Hilt themselves. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun clock(): Clock = DeviceClock

    @Provides
    fun dispatchers(): AppDispatchers = DefaultAppDispatchers
}
