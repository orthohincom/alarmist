package com.orthohin.alarmist.core.scheduling.di

import com.orthohin.alarmist.core.scheduling.AlarmEditor
import com.orthohin.alarmist.core.scheduling.AlarmScheduler
import com.orthohin.alarmist.core.scheduling.AndroidBootClock
import com.orthohin.alarmist.core.scheduling.AndroidExactAlarmPermission
import com.orthohin.alarmist.core.scheduling.AndroidSystemAlarms
import com.orthohin.alarmist.core.scheduling.BootClock
import com.orthohin.alarmist.core.scheduling.DefaultAlarmScheduler
import com.orthohin.alarmist.core.scheduling.DefaultSnoozeAlarms
import com.orthohin.alarmist.core.scheduling.ExactAlarmPermission
import com.orthohin.alarmist.core.scheduling.SnoozeAlarms
import com.orthohin.alarmist.core.scheduling.SystemAlarms
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Needs an `AlarmRinger` binding (core:ringing). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SchedulingModule {
    @Binds
    abstract fun alarmEditor(impl: DefaultAlarmScheduler): AlarmEditor

    @Binds
    abstract fun alarmScheduler(impl: DefaultAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun systemAlarms(impl: AndroidSystemAlarms): SystemAlarms

    @Binds
    abstract fun snoozeAlarms(impl: DefaultSnoozeAlarms): SnoozeAlarms

    @Binds
    abstract fun exactAlarmPermission(impl: AndroidExactAlarmPermission): ExactAlarmPermission

    @Binds
    abstract fun bootClock(impl: AndroidBootClock): BootClock
}
