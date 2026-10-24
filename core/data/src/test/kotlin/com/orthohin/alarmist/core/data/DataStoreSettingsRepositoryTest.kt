package com.orthohin.alarmist.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.repository(file: File = File(folder.root, "settings.preferences_pb")) =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )

    @Test
    fun `starts with the defaults`() = runTest {
        assertEquals(AppSettings(), repository().settings.first())
    }

    @Test
    fun `keeps changes`() = runTest {
        val repository = repository()
        repository.update { it.copy(theme = ThemeMode.DARK) }
        repository.update { it.copy(dynamicColor = false) }
        repository.update { it.copy(overlayHintDismissed = true) }
        repository.update { it.copy(timeInput = TimeInput.DIAL) }
        assertEquals(
            AppSettings(ThemeMode.DARK, dynamicColor = false, timeInput = TimeInput.DIAL, overlayHintDismissed = true),
            repository.settings.first()
        )
    }
}
