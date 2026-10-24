package com.orthohin.alarmist.core.scheduling

import android.os.SystemClock
import com.orthohin.alarmist.core.common.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** When the phone last started, to tell "the phone was off" from "the app was stopped". */
internal fun interface BootClock {
    fun bootTime(): Instant
}

internal class AndroidBootClock @Inject constructor(private val clock: Clock) : BootClock {
    override fun bootTime(): Instant = clock.now() - Duration.ofMillis(SystemClock.elapsedRealtime())
}
