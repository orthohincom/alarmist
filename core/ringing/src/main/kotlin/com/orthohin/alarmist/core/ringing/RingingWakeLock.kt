package com.orthohin.alarmist.core.ringing

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the CPU on from the alarm broadcast until ringing stops. Not reference counted. */
@Singleton
internal class RingingWakeLock @Inject constructor(@ApplicationContext context: Context) {
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Alarmist:ringing")
        .apply { setReferenceCounted(false) }

    /** (Re)starts the timeout. Playback holds its own wake lock, so a longer ring is still safe. */
    fun acquire() = lock.acquire(TIMEOUT_MS)

    fun release() {
        if (lock.isHeld) lock.release()
    }

    private companion object {
        const val TIMEOUT_MS = 60 * 60 * 1000L
    }
}
