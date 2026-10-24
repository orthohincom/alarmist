package com.orthohin.alarmist.mission.api

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject

/** Every mission bound with `@IntoMap @StringKey(type)`. */
class MissionRegistry @Inject constructor(missions: Map<String, @JvmSuppressWildcards Mission>) {
    val all: List<Mission> = missions.values.toList()

    /** Null if no module provides [type] (e.g. a mission removed in a later version). */
    operator fun get(type: String): Mission? = all.firstOrNull { it.type == type }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MissionApiModule {
    /** Declares the map, so it exists (empty) before any mission module is added. */
    @Multibinds
    abstract fun missions(): Map<String, Mission>
}
