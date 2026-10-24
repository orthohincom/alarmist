package com.orthohin.alarmist.mission.api

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.orthohin.alarmist.core.model.MissionSpec

/**
 * A way to dismiss an alarm. Each mission lives in its own module and registers itself with Hilt:
 * `@Binds @IntoMap @StringKey("math") abstract fun bind(m: MathMission): Mission` (the key is [type]).
 * Core modules never look inside [MissionSpec.config]; the mission owns that JSON.
 */
interface Mission {
    /** Stored in [MissionSpec.type]: never change it. */
    val type: String

    @get:StringRes val name: Int

    @get:DrawableRes val icon: Int

    /** One short line for the mission picker, e.g. "Solve math problems". */
    @get:StringRes val description: Int

    /** The mission picker's group. */
    val category: MissionCategory

    /** Bump when the config format changes. Older specs keep their version: read them, don't reject them. */
    val configVersion: Int

    fun defaultConfig(): String

    /** One line for the alarm editor, e.g. "Hard · 3 problems". */
    fun summary(spec: MissionSpec, resources: Resources): String

    /** Runtime permissions it needs; the editor asks for them when the mission is added. */
    val permissions: List<String> get() = emptyList()

    /** False if this device can't run it (no sensor, no camera). The alarm editor greys it out. */
    fun isAvailable(context: Context): Boolean = true

    /**
     * False if it can't run right now, on the ringing screen: unavailable, or missing something that can't be asked
     * for over the lock screen (e.g. the camera permission). The ringing screen then offers to skip it.
     */
    fun isReady(context: Context): Boolean = isAvailable(context)

    /** Edits the config; [onChange] gets the new JSON. */
    @Composable
    fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit)

    /**
     * The mission itself, on the ringing screen (or in the editor's preview). Call [MissionHost.onComplete] once
     * when solved. Touches count as activity by themselves; call [MissionHost.onActivity] for other input.
     */
    @Composable
    fun Runner(spec: MissionSpec, host: MissionHost)
}

/** Groups in the mission picker, in this order. */
enum class MissionCategory {
    /** Thinking: math, typing, memory games. */
    Brain,

    /** Moving: steps, shaking, squats. */
    Body,

    /** Getting up to reach something: scan a code, photograph a place, touch a tag. */
    GetUp,

    Other
}

/** What a running mission reports to the ringing screen. */
interface MissionHost {
    /** Input other than touch (shake, step, typing on a hardware keyboard): keeps the alarm muted. */
    fun onActivity()

    /** Solved. */
    fun onComplete()
}

/** A new spec with the default config. */
fun Mission.newSpec(): MissionSpec = MissionSpec(type, configVersion, defaultConfig())
