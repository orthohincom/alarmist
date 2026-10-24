package com.orthohin.alarmist.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Settings in a Preferences DataStore in device-protected storage, like the alarms (readable before the first unlock). */
@Singleton
internal class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            // Not preferencesDataStoreFile(): it resolves against applicationContext, the credential-protected storage.
            produceFile = { File(context.createDeviceProtectedStorageContext().filesDir, FILE) }
        )
    )

    override val settings: Flow<AppSettings> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.e("Alarmist", "Couldn't read settings", e)
            emit(emptyPreferences())
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[THEME] = new.theme.name.lowercase()
            prefs[DYNAMIC_COLOR] = new.dynamicColor
            prefs[TIME_INPUT] = new.timeInput.name.lowercase()
            prefs[OVERLAY_HINT_DISMISSED] = new.overlayHintDismissed
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name.equals(this[THEME], ignoreCase = true) } ?: defaults.theme,
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            timeInput = TimeInput.entries.firstOrNull { it.name.equals(this[TIME_INPUT], ignoreCase = true) }
                ?: defaults.timeInput,
            overlayHintDismissed = this[OVERLAY_HINT_DISMISSED] ?: defaults.overlayHintDismissed
        )
    }

    private companion object {
        const val FILE = "datastore/settings.preferences_pb"
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val TIME_INPUT = stringPreferencesKey("time_input")
        val OVERLAY_HINT_DISMISSED = booleanPreferencesKey("overlay_hint_dismissed")
    }
}
