package com.orthohin.alarmist.core.data

import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the alarm editor sets the time: scroll wheels or Material's clock face. */
enum class TimeInput { WHEELS, DIAL }

/** App-wide settings. Per-alarm settings live in the alarm. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colours from the wallpaper (Android 12+; ignored before). */
    val dynamicColor: Boolean = true,
    val timeInput: TimeInput = TimeInput.WHEELS,
    /** "Don't show again" on the alarm list's "display over other apps" card. This phone only: not backed up. */
    val overlayHintDismissed: Boolean = false
)

/** Main-safe. */
interface SettingsRepository {
    /** The current settings, again after each change. Emits the defaults if the file can't be read. */
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
