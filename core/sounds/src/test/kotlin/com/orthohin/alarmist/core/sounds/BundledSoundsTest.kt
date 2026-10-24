package com.orthohin.alarmist.core.sounds

import com.orthohin.alarmist.core.model.AlarmSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledSoundsTest {
    @Test
    fun `ids are unique and simple`() {
        val ids = BundledSounds.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-z_]+")) })
    }

    @Test
    fun `default is the model's default`() {
        assertEquals(AlarmSound.Default, BundledSounds.default.sound)
        assertEquals(BundledSounds.default, BundledSounds.find(AlarmSound.Default.id))
    }

    @Test
    fun `every category has sounds and the list is grouped in category order`() {
        val categories = BundledSounds.all.map { it.category }
        assertEquals(SoundCategory.entries, categories.distinct())
        assertEquals(categories.sortedBy { it.ordinal }, categories)
    }
}
