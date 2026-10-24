package com.orthohin.alarmist.feature.reliability

import com.orthohin.alarmist.core.model.AlarmEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReliabilityTextTest {
    @Test
    fun `phone makers map to their tips and dontkillmyapp pages`() {
        assertEquals(OemTip(R.string.rel_oem_samsung, "samsung"), oemTip("samsung"))
        assertEquals(OemTip(R.string.rel_oem_oppo, "realme"), oemTip("realme"))
        assertEquals(OemTip(R.string.rel_oem_oneplus, "oneplus"), oemTip("OnePlus"))
        assertEquals(OemTip(R.string.rel_oem_xiaomi, "xiaomi"), oemTip("Redmi"))
        assertFalse(oemTip("Google").known)
        assertEquals("https://dontkillmyapp.com/", dontKillMyAppUrl(oemTip("Google").slug))
    }

    @Test
    fun `lateness is in whole minutes and never negative`() {
        val due = Instant.parse("2026-10-16T07:00:00Z")
        fun fired(at: String) = AlarmEvent(Instant.parse(at), AlarmEvent.Type.Fired, 1, due)
        assertEquals(0, fired("2026-10-16T07:00:40Z").minutesLate())
        assertEquals(8, fired("2026-10-16T07:08:10Z").minutesLate())
        assertEquals(0, fired("2026-10-16T06:59:59Z").minutesLate())
        assertEquals(0, AlarmEvent(due, AlarmEvent.Type.Fired, 1).minutesLate())
    }
}
