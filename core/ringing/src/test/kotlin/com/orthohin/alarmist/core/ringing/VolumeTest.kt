package com.orthohin.alarmist.core.ringing

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {
    @Test
    fun `ramp rises from -40 dB to full`() {
        assertEquals(0.01f, rampGain(0, 30), 1e-4f)
        assertEquals(0.1f, rampGain(15_000, 30), 1e-4f)
        assertEquals(1f, rampGain(30_000, 30), 0f)
        assertEquals(1f, rampGain(90_000, 30), 0f)
    }

    @Test
    fun `no ramp starts at full volume`() {
        assertEquals(1f, rampGain(0, 0), 0f)
    }

    @Test
    fun `stream volume is a share of the range but never muted`() {
        assertEquals(7, streamVolume(100, 1, 7))
        assertEquals(4, streamVolume(50, 1, 7))
        assertEquals(1, streamVolume(1, 0, 7))
        assertEquals(2, streamVolume(1, 2, 7))
    }
}
