package com.orthohin.alarmist.core.sounds

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.model.AlarmSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** A system alarm sound or ringtone. */
data class DeviceRingtone(val title: String, val uri: String) {
    val sound: AlarmSound.Custom get() = AlarmSound.Custom(uri)
}

/** The device's alarm sounds and ringtones. Needs no permission. */
class DeviceRingtones @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers
) {
    /** Sorted by title; empty if the media provider can't be read. */
    suspend fun load(): List<DeviceRingtone> = withContext(dispatchers.io) {
        try {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            }
            manager.cursor.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val uri = manager.getRingtoneUri(cursor.position) ?: continue
                        add(DeviceRingtone(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX), uri.toString()))
                    }
                }
            }.distinctBy { it.uri }.sortedBy { it.title.lowercase() }
        } catch (e: Exception) {
            Log.w(TAG, "Can't list device ringtones", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "Alarmist"
    }
}
