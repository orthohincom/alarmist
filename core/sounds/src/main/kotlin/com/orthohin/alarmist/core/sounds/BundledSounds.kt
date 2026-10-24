package com.orthohin.alarmist.core.sounds

import android.media.AudioAttributes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.orthohin.alarmist.core.model.AlarmSound

/** Groups of bundled sounds, in the order the picker shows them. */
enum class SoundCategory(@StringRes val title: Int) {
    Classic(R.string.sounds_category_classic),
    Gentle(R.string.sounds_category_gentle),
    Loud(R.string.sounds_category_loud),
    Nature(R.string.sounds_category_nature)
}

/** A sound shipped in the APK, so it is always available. */
class BundledSound internal constructor(
    /** Stored in alarms as [AlarmSound.Bundled.id]: never rename or reuse one. */
    val id: String,
    @StringRes val name: Int,
    val category: SoundCategory,
    @RawRes val resource: Int
) {
    val sound: AlarmSound.Bundled get() = AlarmSound.Bundled(id)
}

/** The catalogue of bundled sounds (made by `scripts/gen-sounds.py`), grouped by category. */
object BundledSounds {
    val all: List<BundledSound> = listOf(
        BundledSound(AlarmSound.Default.id, R.string.sounds_default, SoundCategory.Classic, R.raw.sound_default),
        BundledSound("digital", R.string.sounds_digital, SoundCategory.Classic, R.raw.sound_digital),
        BundledSound("alarm_clock", R.string.sounds_alarm_clock, SoundCategory.Classic, R.raw.sound_alarm_clock),
        BundledSound("telephone", R.string.sounds_telephone, SoundCategory.Classic, R.raw.sound_telephone),
        BundledSound("chimes", R.string.sounds_chimes, SoundCategory.Gentle, R.raw.sound_chimes),
        BundledSound("music_box", R.string.sounds_music_box, SoundCategory.Gentle, R.raw.sound_music_box),
        BundledSound("marimba", R.string.sounds_marimba, SoundCategory.Gentle, R.raw.sound_marimba),
        BundledSound("sunrise", R.string.sounds_sunrise, SoundCategory.Gentle, R.raw.sound_sunrise),
        BundledSound("siren", R.string.sounds_siren, SoundCategory.Loud, R.raw.sound_siren),
        BundledSound("buzzer", R.string.sounds_buzzer, SoundCategory.Loud, R.raw.sound_buzzer),
        BundledSound("klaxon", R.string.sounds_klaxon, SoundCategory.Loud, R.raw.sound_klaxon),
        BundledSound("pulse", R.string.sounds_pulse, SoundCategory.Loud, R.raw.sound_pulse),
        BundledSound("birds", R.string.sounds_birds, SoundCategory.Nature, R.raw.sound_birds),
        BundledSound("brook", R.string.sounds_brook, SoundCategory.Nature, R.raw.sound_brook)
    )

    /** [AlarmSound.Default]. */
    val default: BundledSound = all.first()

    fun find(id: String): BundledSound? = all.firstOrNull { it.id == id }
}

/** How every alarm sound plays: on the alarm stream, so Do Not Disturb and the alarm volume apply. */
object AlarmAudio {
    val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}
