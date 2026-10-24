package com.orthohin.alarmist.core.ringing

import android.annotation.SuppressLint
import android.content.Context
import com.orthohin.alarmist.core.model.MissionProgress
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.RingSessions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/** Keeps ring sessions across process death and reboots. */
internal interface RingSessionStore {
    fun load(): RingSessions

    /** Blocking: returns once the sessions are on disk. */
    fun save(sessions: RingSessions)
}

/** SharedPreferences in device-protected storage, so it works before the first unlock. */
internal class PrefsRingSessionStore @Inject constructor(@ApplicationContext context: Context) : RingSessionStore {
    private val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(
        "ringing",
        Context.MODE_PRIVATE
    )

    override fun load(): RingSessions = prefs.getString(KEY, null)?.let(RingSessionCodec::decode) ?: RingSessions()

    @SuppressLint("ApplySharedPref") // It must be on disk before the caller relies on it.
    override fun save(sessions: RingSessions) {
        check(prefs.edit().putString(KEY, RingSessionCodec.encode(sessions)).commit()) { "Write failed" }
    }

    private companion object {
        const val KEY = "sessions"
    }
}

/**
 * One line per session after a version line:
 * `alarmId;firedAt;snoozeCount;R|S|C|A;since|until|askAt|until;unansweredRings;missionIndex@mutedUntil;respondSeconds`
 * (epoch millis; C = wake-up check pending, A = asking). The last three fields were added later and may be missing
 * (0, no mission); the mission field is empty without one, the last one is written only for C.
 */
internal object RingSessionCodec {
    private const val VERSION = "1"

    fun encode(sessions: RingSessions): String = (listOf(VERSION) + sessions.sessions.map(::encode)).joinToString("\n")

    /** Throws [IllegalArgumentException] on anything it doesn't understand. */
    fun decode(text: String): RingSessions {
        val lines = text.lines()
        require(lines.first() == VERSION) { "Unknown ring session format: ${lines.first()}" }
        return RingSessions(lines.drop(1).filter { it.isNotEmpty() }.map(::decodeLine))
    }

    private fun encode(s: RingSession): String {
        val (kind, at) = when (val st = s.state) {
            is RingSession.State.Ringing -> "R" to st.since
            is RingSession.State.Snoozed -> "S" to st.until
            is RingSession.State.CheckPending -> "C" to st.askAt
            is RingSession.State.Checking -> "A" to st.until
        }
        val mission = s.mission?.let { "${it.index}@${it.mutedUntil.toEpochMilli()}" }.orEmpty()
        return listOf(
            s.alarmId,
            s.firedAt.toEpochMilli(),
            s.snoozeCount,
            kind,
            at.toEpochMilli(),
            s.unansweredRings,
            mission
        )
            .plus(listOfNotNull((s.state as? RingSession.State.CheckPending)?.respondSeconds))
            .joinToString(";")
    }

    private fun decodeLine(line: String): RingSession {
        val f = line.split(";")
        require(f.size in 5..8) { "Bad ring session: $line" }
        val at = Instant.ofEpochMilli(f[4].toLong())
        val state = when (f[3]) {
            "R" -> RingSession.State.Ringing(at)
            "S" -> RingSession.State.Snoozed(at)
            "C" -> RingSession.State.CheckPending(at, f.getOrNull(7)?.toInt() ?: throw IllegalArgumentException(line))
            "A" -> RingSession.State.Checking(at)
            else -> throw IllegalArgumentException("Bad ring state: $line")
        }
        return RingSession(
            f[0].toLong(),
            Instant.ofEpochMilli(f[1].toLong()),
            state,
            f[2].toInt(),
            f.getOrNull(5)?.toInt() ?: 0,
            f.getOrNull(6)?.takeIf { it.isNotEmpty() }?.let { m ->
                val (index, mutedUntil) = m.split("@").also { require(it.size == 2) { "Bad mission: $line" } }
                MissionProgress(index.toInt(), Instant.ofEpochMilli(mutedUntil.toLong()))
            }
        )
    }
}
