package com.orthohin.alarmist.core.ringing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The notifications' Snooze, Dismiss and "I'm awake" buttons. */
@AndroidEntryPoint
internal class RingingActionReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: RingingController

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                when (action) {
                    ACTION_SNOOZE -> controller.snooze(alarmId)

                    ACTION_AWAKE -> controller.awake(alarmId)

                    // The buttons are hidden for alarms with missions; an old notification mustn't get around that.
                    ACTION_DISMISS -> if (controller.alarm(alarmId)?.missions.isNullOrEmpty()) {
                        controller.dismiss(alarmId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$action of alarm $alarmId failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.orthohin.alarmist.ringing.SNOOZE"
        const val ACTION_DISMISS = "com.orthohin.alarmist.ringing.DISMISS"
        const val ACTION_AWAKE = "com.orthohin.alarmist.ringing.AWAKE"

        fun intent(context: Context, action: String, alarmId: Long): Intent =
            Intent(context, RingingActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("alarmist://ring/$alarmId"))
    }
}
