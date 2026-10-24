package com.orthohin.alarmist

import android.app.Application
import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.scheduling.AlarmScheduler
import com.orthohin.alarmist.core.scheduling.RescheduleReason
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs before the first unlock too (direct-boot-aware receivers), so it must not touch credential-encrypted
 * storage (default SharedPreferences, `filesDir`).
 */
@HiltAndroidApp
class AlarmistApp : Application() {
    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onCreate() {
        super.onCreate()
        // A force stop or an OEM task killer clears the system's alarms without any broadcast.
        val logErrors = CoroutineExceptionHandler { _, e -> Log.e("Alarmist", "Reschedule on start failed", e) }
        CoroutineScope(SupervisorJob() + dispatchers.io + logErrors).launch {
            scheduler.rescheduleAll(RescheduleReason.AppStart)
        }
    }
}
