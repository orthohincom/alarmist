package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.AntiCheatConfig
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.SoundConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What a backup file holds. Alarm ids are not kept: restored alarms are new. */
data class Backup(val alarms: List<Alarm>, val settings: AppSettings? = null)

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not JSON, or not written by Alarmist. */
        NOT_A_BACKUP,

        /** Written by a newer app version with a format this one doesn't know. */
        NEWER_VERSION,

        /** An Alarmist backup with a missing or invalid value. */
        INVALID
    }
}

/**
 * The backup file format (version [VERSION]), written field by field like `AlarmMapper` so it stays stable
 * when the model changes. A field that is missing decodes to the model's default, so older files keep
 * working; a new field doesn't need a version bump, a changed meaning does. Mission configs are stored as
 * the plugin's JSON string, untouched. Pure Kotlin.
 */
object BackupCodec {
    const val VERSION = 1
    private const val APP = "alarmist"
    private val json = Json { prettyPrint = true }

    fun encode(backup: Backup, exportedAt: Instant): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("app", APP)
            put("version", VERSION)
            put("exportedAt", exportedAt.toString())
            backup.settings?.let { settings ->
                put(
                    "settings",
                    buildJsonObject {
                        put("theme", settings.theme.name.lowercase())
                        put("dynamicColor", settings.dynamicColor)
                        put("timeInput", settings.timeInput.name.lowercase())
                    }
                )
            }
            put("alarms", buildJsonArray { backup.alarms.forEach { add(encodeAlarm(it)) } })
        }
    )

    /** @throws BackupException if [text] isn't a backup this version can read. */
    fun decode(text: String): Backup {
        val root = runCatching { (Json.parseToJsonElement(text) as JsonObject).takeIf { it.string("app") == APP } }
            .getOrNull() ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        try {
            if (root.getValue("version").jsonPrimitive.int > VERSION) {
                throw BackupException(BackupException.Reason.NEWER_VERSION)
            }
            return Backup(
                alarms = root.getValue("alarms").jsonArray.map { decodeAlarm(it.jsonObject) },
                settings = root.obj("settings")?.let { o ->
                    val defaults = AppSettings()
                    AppSettings(
                        theme = o.string("theme")?.let { name ->
                            ThemeMode.valueOf(name.uppercase())
                        } ?: defaults.theme,
                        dynamicColor = o.bool("dynamicColor") ?: defaults.dynamicColor,
                        // Added after version 1 shipped: older backups don't have it.
                        timeInput =
                            o.string("timeInput")?.let { TimeInput.valueOf(it.uppercase()) } ?: defaults.timeInput
                    )
                }
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: RuntimeException) {
            // Wrong types, unknown names and values the model rejects (IllegalArgumentException, DateTimeException).
            throw BackupException(BackupException.Reason.INVALID, e)
        }
    }

    private fun encodeAlarm(alarm: Alarm): JsonObject = buildJsonObject {
        put("time", alarm.time.toString())
        when (val schedule = alarm.schedule) {
            is Schedule.Once -> schedule.date?.let { put("date", it.toString()) }

            is Schedule.Weekly -> put(
                "days",
                buildJsonArray { schedule.days.sorted().forEach { add(JsonPrimitive(it.name.lowercase())) } }
            )
        }
        put("enabled", alarm.enabled)
        put("label", alarm.label)
        alarm.skippedDate?.let { put("skippedDate", it.toString()) }
        put(
            "sound",
            buildJsonObject {
                val sound = alarm.sound
                when (val s = sound.sound) {
                    is AlarmSound.Bundled -> put("bundled", s.id)
                    is AlarmSound.Imported -> put("imported", s.fileName)
                    is AlarmSound.Custom -> put("custom", s.uri)
                }
                put("backup", sound.backup.id)
                put("volumePercent", sound.volumePercent)
                put("rampUpSeconds", sound.rampUpSeconds)
                put("vibrate", sound.vibrate)
            }
        )
        put(
            "snooze",
            buildJsonObject {
                put("enabled", alarm.snooze.enabled)
                put("minutes", alarm.snooze.minutes)
                put("maxCount", alarm.snooze.maxCount)
            }
        )
        put(
            "autoSilence",
            buildJsonObject {
                put("enabled", alarm.autoSilence.enabled)
                put("ringMinutes", alarm.autoSilence.ringMinutes)
                put("snoozeMinutes", alarm.autoSilence.snoozeMinutes)
                put("maxRings", alarm.autoSilence.maxRings)
            }
        )
        put(
            "missions",
            buildJsonArray {
                alarm.missions.forEach { m ->
                    add(
                        buildJsonObject {
                            put("type", m.type)
                            put("configVersion", m.configVersion)
                            put("config", m.config)
                        }
                    )
                }
            }
        )
        put(
            "wakeUpCheck",
            buildJsonObject {
                put("enabled", alarm.wakeUpCheck.enabled)
                put("delayMinutes", alarm.wakeUpCheck.delayMinutes)
                put("responseSeconds", alarm.wakeUpCheck.responseSeconds)
            }
        )
        put(
            "antiCheat",
            buildJsonObject {
                put("relaunchIfLeft", alarm.antiCheat.relaunchIfLeft)
                put("lockVolume", alarm.antiCheat.lockVolume)
                put("powerMenuGuard", alarm.antiCheat.powerMenuGuard)
            }
        )
    }

    private fun decodeAlarm(o: JsonObject): Alarm {
        val d = Alarm(time = LocalTime.MIDNIGHT)
        val days = o["days"]?.jsonArray?.map { DayOfWeek.valueOf(it.jsonPrimitive.content.uppercase()) }
        val sound = o.obj("sound")
        val snooze = o.obj("snooze")
        val silence = o.obj("autoSilence")
        val check = o.obj("wakeUpCheck")
        val anti = o.obj("antiCheat")
        return Alarm(
            time = LocalTime.parse(o.string("time") ?: throw IllegalArgumentException("No time")),
            schedule = if (days.isNullOrEmpty()) {
                Schedule.Once(o.string("date")?.let(LocalDate::parse))
            } else {
                Schedule.Weekly(days.toSet())
            },
            enabled = o.bool("enabled") ?: d.enabled,
            label = o.string("label") ?: d.label,
            skippedDate = o.string("skippedDate")?.let(LocalDate::parse),
            sound = SoundConfig(
                sound = sound?.string("bundled")?.let(AlarmSound::Bundled)
                    ?: sound?.string("imported")?.let(AlarmSound::Imported)
                    ?: sound?.string("custom")?.let(AlarmSound::Custom)
                    ?: d.sound.sound,
                backup = sound?.string("backup")?.let(AlarmSound::Bundled) ?: d.sound.backup,
                volumePercent = sound?.int("volumePercent") ?: d.sound.volumePercent,
                rampUpSeconds = sound?.int("rampUpSeconds") ?: d.sound.rampUpSeconds,
                vibrate = sound?.bool("vibrate") ?: d.sound.vibrate
            ),
            snooze = SnoozeConfig(
                enabled = snooze?.bool("enabled") ?: d.snooze.enabled,
                minutes = snooze?.int("minutes") ?: d.snooze.minutes,
                // An explicit null is "unlimited"; a missing field is the default.
                maxCount = if (snooze != null && "maxCount" in snooze) snooze.int("maxCount") else d.snooze.maxCount
            ),
            autoSilence = AutoSilenceConfig(
                enabled = silence?.bool("enabled") ?: d.autoSilence.enabled,
                ringMinutes = silence?.int("ringMinutes") ?: d.autoSilence.ringMinutes,
                snoozeMinutes = silence?.int("snoozeMinutes") ?: d.autoSilence.snoozeMinutes,
                maxRings = silence?.int("maxRings") ?: d.autoSilence.maxRings
            ),
            missions = o["missions"]?.jsonArray.orEmpty().map { m ->
                val mission = m.jsonObject
                MissionSpec(
                    type = mission.string("type") ?: throw IllegalArgumentException("No mission type"),
                    configVersion = mission.int("configVersion") ?: 1,
                    config = mission.string("config") ?: "{}"
                )
            },
            wakeUpCheck = WakeUpCheckConfig(
                enabled = check?.bool("enabled") ?: d.wakeUpCheck.enabled,
                delayMinutes = check?.int("delayMinutes") ?: d.wakeUpCheck.delayMinutes,
                responseSeconds = check?.int("responseSeconds") ?: d.wakeUpCheck.responseSeconds
            ),
            antiCheat = AntiCheatConfig(
                relaunchIfLeft = anti?.bool("relaunchIfLeft") ?: d.antiCheat.relaunchIfLeft,
                lockVolume = anti?.bool("lockVolume") ?: d.antiCheat.lockVolume,
                powerMenuGuard = anti?.bool("powerMenuGuard") ?: d.antiCheat.powerMenuGuard
            )
        )
    }

    /** A JSON null counts as missing. A value of the wrong type throws. */
    private fun JsonObject.value(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive

    private fun JsonObject.string(key: String): String? = value(key)?.also {
        require(it.isString) { "$key is not a string" }
    }?.content

    private fun JsonObject.int(key: String): Int? = value(key)?.int

    private fun JsonObject.bool(key: String): Boolean? = value(key)?.boolean

    private fun JsonObject.obj(key: String): JsonObject? = this[key]?.takeUnless { it is JsonNull }?.jsonObject
}
