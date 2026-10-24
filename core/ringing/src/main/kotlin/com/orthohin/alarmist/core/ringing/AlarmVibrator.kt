package com.orthohin.alarmist.core.ringing

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.orthohin.alarmist.core.sounds.AlarmAudio
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Repeating alarm vibration, tagged as an alarm so Do Not Disturb treats it like the alarm sound. */
internal class AlarmVibrator @Inject constructor(@ApplicationContext context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    fun start() {
        val effect = VibrationEffect.createWaveform(PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AlarmAudio.ATTRIBUTES)
        }
    }

    fun stop() = vibrator.cancel()

    private companion object {
        /** Off, on, off... in ms; repeats from the start. */
        val PATTERN = longArrayOf(0, 800, 600)
    }
}
