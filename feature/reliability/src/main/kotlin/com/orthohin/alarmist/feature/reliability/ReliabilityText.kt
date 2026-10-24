package com.orthohin.alarmist.feature.reliability

import androidx.annotation.StringRes
import com.orthohin.alarmist.core.model.AlarmEvent
import java.time.Duration
import java.util.Locale

/** Battery tips for a phone maker; [slug] is its page on dontkillmyapp.com ("" for the home page). */
internal data class OemTip(@StringRes val text: Int, val slug: String, val known: Boolean = true)

internal fun oemTip(manufacturer: String): OemTip = when (manufacturer.lowercase(Locale.ROOT)) {
    "samsung" -> OemTip(R.string.rel_oem_samsung, "samsung")
    "xiaomi", "redmi", "poco" -> OemTip(R.string.rel_oem_xiaomi, "xiaomi")
    "huawei" -> OemTip(R.string.rel_oem_huawei, "huawei")
    "honor" -> OemTip(R.string.rel_oem_huawei, "honor")
    "oneplus" -> OemTip(R.string.rel_oem_oneplus, "oneplus")
    "oppo" -> OemTip(R.string.rel_oem_oppo, "oppo")
    "realme" -> OemTip(R.string.rel_oem_oppo, "realme")
    "vivo" -> OemTip(R.string.rel_oem_vivo, "vivo")
    else -> OemTip(R.string.rel_oem_generic, "", known = false)
}

internal fun dontKillMyAppUrl(slug: String) = "https://dontkillmyapp.com/$slug"

/** Whole minutes between when it was due and when it rang; 0 when on time or unknown. */
internal fun AlarmEvent.minutesLate(): Long = due?.let { Duration.between(it, at).toMinutes().coerceAtLeast(0) } ?: 0
