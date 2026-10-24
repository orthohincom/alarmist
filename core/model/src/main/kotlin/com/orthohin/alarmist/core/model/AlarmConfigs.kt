package com.orthohin.alarmist.core.model

sealed interface AlarmSound {
    /** A sound shipped with the app, so it is always available. */
    data class Bundled(val id: String) : AlarmSound

    /** A ringtone or file the user picked (a content URI). It can disappear, so ringing has a backup. */
    data class Custom(val uri: String) : AlarmSound

    /**
     * A sound the user imported, by its file name in the app's device-protected sound folder (owned by
     * core:sounds), so it plays before the first unlock. Deleting it falls back to the backup.
     */
    data class Imported(val fileName: String) : AlarmSound {
        init {
            require(fileName.isNotBlank() && '/' !in fileName) { "Invalid sound file name: $fileName" }
        }
    }

    companion object {
        val Default = Bundled("default")
    }
}

data class SoundConfig(
    val sound: AlarmSound = AlarmSound.Default,
    /** Played if [sound] fails to load. Always bundled, so it can't fail the same way. */
    val backup: AlarmSound.Bundled = AlarmSound.Default,
    /** Percentage of the alarm stream's maximum volume. 0 is vibrate only (if [vibrate]); the UI must not allow 0 without it. */
    val volumePercent: Int = 100,
    /** How long the volume takes to rise to [volumePercent]. 0 starts at full volume. */
    val rampUpSeconds: Int = 30,
    val vibrate: Boolean = true
) {
    init {
        require(volumePercent in 0..100) { "volumePercent out of range: $volumePercent" }
        require(rampUpSeconds in 0..MAX_RAMP_UP_SECONDS) { "rampUpSeconds out of range: $rampUpSeconds" }
    }

    companion object {
        const val MAX_RAMP_UP_SECONDS = 600
    }
}

data class SnoozeConfig(
    val enabled: Boolean = true,
    val minutes: Int = 5,
    /** How many times the user may snooze one ring. Null means unlimited. */
    val maxCount: Int? = 3
) {
    init {
        require(minutes in 1..60) { "Snooze minutes out of range: $minutes" }
        require(maxCount == null || maxCount >= 1) { "maxCount must be at least 1 or null: $maxCount" }
    }
}

/**
 * What happens when nobody answers. Enabled: ring for [ringMinutes], go quiet for [snoozeMinutes], and after
 * [maxRings] unanswered rings stop for good (reported as missed). Disabled: ring until handled.
 * Separate from [SnoozeConfig]: the user's own snoozes don't count here, and this works with snooze off.
 */
data class AutoSilenceConfig(
    val enabled: Boolean = true,
    val ringMinutes: Int = 3,
    val snoozeMinutes: Int = 5,
    val maxRings: Int = 3
) {
    init {
        require(ringMinutes in 1..60) { "ringMinutes out of range: $ringMinutes" }
        require(snoozeMinutes in 1..60) { "snoozeMinutes out of range: $snoozeMinutes" }
        require(maxRings in 1..10) { "maxRings out of range: $maxRings" }
    }
}

/**
 * After dismissal, asks "still awake?" after [delayMinutes]. If nobody answers within [responseSeconds],
 * the alarm rings again.
 */
data class WakeUpCheckConfig(val enabled: Boolean = false, val delayMinutes: Int = 5, val responseSeconds: Int = 100) {
    init {
        require(delayMinutes in 1..60) { "delayMinutes out of range: $delayMinutes" }
        require(responseSeconds in 10..600) { "responseSeconds out of range: $responseSeconds" }
    }
}

/** Measures that stop the user from escaping a ringing alarm. All opt-in. */
data class AntiCheatConfig(
    /** Bring the ringing screen back if the user leaves it. */
    val relaunchIfLeft: Boolean = false,
    /** Keep the alarm stream volume from being turned down while ringing. */
    val lockVolume: Boolean = false,
    /** Block the power menu while ringing. Only the fdroid flavor implements it; others ignore it. */
    val powerMenuGuard: Boolean = false
)

/**
 * One mission in an alarm's sequence. The mission plugin registered for [type] owns [config], a JSON string
 * it parses itself, and bumps [configVersion] when that format changes. Core code never looks inside
 * [config], so adding a mission doesn't touch core modules.
 */
data class MissionSpec(val type: String, val configVersion: Int = 1, val config: String = "{}") {
    init {
        require(type.isNotBlank()) { "Mission type is blank" }
        require(configVersion >= 1) { "configVersion must be at least 1: $configVersion" }
    }
}
