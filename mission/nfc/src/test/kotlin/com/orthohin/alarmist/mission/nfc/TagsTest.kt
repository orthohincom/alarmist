package com.orthohin.alarmist.mission.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {
    private val sticker = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0x3C, 0x4D, 0x5E, 0x6F)

    @Test
    fun `IDs are upper-case hex`() {
        assertEquals("04A1B23C4D5E6F", tagId(sticker))
        assertEquals("", tagId(byteArrayOf()))
    }

    @Test
    fun `random and empty IDs are recognised`() {
        assertTrue(isRandomId(byteArrayOf(0x08, 0x12, 0x34, 0x56)))
        assertTrue(isRandomId(byteArrayOf()))
        assertFalse(isRandomId(sticker))
        // A fixed 4-byte UID (e.g. MIFARE Classic) is fine.
        assertFalse(isRandomId(byteArrayOf(0x5A, 0x12, 0x34, 0x56)))
    }

    @Test
    fun `a tag is confirmed by a second tap with the same ID`() {
        val confirmer = TagConfirmer()
        assertEquals(TagConfirmer.Result.FIRST, confirmer.onTag(sticker))
        assertEquals(TagConfirmer.Result.CONFIRMED, confirmer.onTag(sticker))
    }

    @Test
    fun `a tag whose ID changes is refused`() {
        val confirmer = TagConfirmer()
        assertEquals(TagConfirmer.Result.FIRST, confirmer.onTag(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)))
        assertEquals(TagConfirmer.Result.CHANGES, confirmer.onTag(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x06)))
        // The new one counts as a first tap.
        assertEquals(TagConfirmer.Result.CONFIRMED, confirmer.onTag(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x06)))
        assertEquals(TagConfirmer.Result.CHANGES, confirmer.onTag(byteArrayOf(0x08, 0x12, 0x34, 0x56)))
        assertEquals(TagConfirmer.Result.FIRST, confirmer.onTag(sticker))
    }

    @Test
    fun `carrying the phone counts as motion, at most once a second`() {
        val gate = MotionGate()
        assertFalse(gate.moved(0f, 0f, 9.8f, 0))
        assertFalse(gate.moved(0.3f, 0.2f, 9.6f, 10_000_000))
        assertTrue(gate.moved(2f, 1f, 12f, 20_000_000))
        assertFalse(gate.moved(2f, 1f, 12f, 500_000_000))
        assertTrue(gate.moved(2f, 1f, 6f, 1_100_000_000))
    }
}
