package com.orthohin.alarmist.core.ringing.di

import com.orthohin.alarmist.core.ringing.AndroidRingingOutputs
import com.orthohin.alarmist.core.ringing.DefaultRingingController
import com.orthohin.alarmist.core.ringing.PowerMenuGuard
import com.orthohin.alarmist.core.ringing.PrefsRingSessionStore
import com.orthohin.alarmist.core.ringing.RingSessionStore
import com.orthohin.alarmist.core.ringing.RingingController
import com.orthohin.alarmist.core.ringing.RingingOutputs
import com.orthohin.alarmist.core.scheduling.AlarmRinger
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RingingModule {
    @Binds
    abstract fun alarmRinger(impl: DefaultRingingController): AlarmRinger

    @Binds
    abstract fun ringingController(impl: DefaultRingingController): RingingController

    @Binds
    abstract fun ringSessionStore(impl: PrefsRingSessionStore): RingSessionStore

    /** Bound only by builds that ship the guard (the app's fdroid flavor). */
    @BindsOptionalOf
    abstract fun powerMenuGuard(): PowerMenuGuard

    @Binds
    abstract fun ringingOutputs(impl: AndroidRingingOutputs): RingingOutputs
}
