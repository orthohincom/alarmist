package com.orthohin.alarmist.feature.reliability

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the first-run setup (the reliability screen in setup mode) was finished. The app shows it at
 * start until then. Credential-encrypted storage is fine: the main activity only runs after unlock.
 */
class ReliabilitySetup @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean(KEY_DONE, false)

    internal fun markDone() = prefs.edit().putBoolean(KEY_DONE, true).apply()

    private companion object {
        const val PREFS = "reliability"
        const val KEY_DONE = "setup_done"
    }
}
