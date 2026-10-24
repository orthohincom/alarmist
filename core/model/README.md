# core:model

Pure Kotlin domain model (`alarmist.jvm.library`). No Android, no dependencies.

Public API (`com.orthohin.alarmist.core.model`):
- `Alarm`: the complete alarm. `time` is wall-clock `LocalTime` (whole minutes), `id == Alarm.NEW_ID` until saved.
  Every feature's fields are there from the start, with defaults.
- `Schedule`: `Once(date: LocalDate? = null)` or `Weekly(days)`.
- `SoundConfig` (`AlarmSound.Bundled(id)`/`Imported(fileName)`/`Custom(uri)`, bundled backup, volume (0 = vibrate only), ramp-up, vibrate),
  `SnoozeConfig`, `AutoSilenceConfig` (ring N min, quiet M min, stop after K unanswered rings; on by default),
  `WakeUpCheckConfig`, `AntiCheatConfig`.
- `AlarmEvent(at, type, alarmId, due, detail)`: an event log entry (types stored by name); `MissReason` (`PhoneOff`, `AppStopped`).
- `MissionSpec(type, configVersion, config)`: `config` is JSON owned by the mission plugin; core never parses it.
- `Alarm.nextTrigger(after: Instant, zone: ZoneId): Instant?`: the next ring strictly after `after`, or null.
  Honours `skippedDate`; DST gap rings when the gap ends, DST overlap rings on the first pass.
- `Alarm.skippingNext(after, zone)`: sets `skippedDate` to the next occurrence's date. Cancel with `copy(skippedDate = null)`.
- `RingSessions` / `RingSession`: the ring state machine (`Ringing(since)` ⇄ `Snoozed(until)`, dismiss removes;
  with the wake-up check, `dismissed` → `CheckPending(askAt)` → `checkTimer` → `Checking(until)` → `checkTimer` →
  `Ringing`, or `awake` removes; `wakeAt` is the session's next timer).
  Pure transitions `fired`, `snooze` (null if not allowed), `timedOut` (auto-silence), `snoozeEnded`, `dismiss`; `checking` is the wake-up check to show when nothing rings; `current` is the session
  ringing the longest. Missions: `RingSession.mission` (`MissionProgress(index, mutedUntil)`), `isAudible(now)`,
  `missionsOpened`, `missionActivity` (same object if < 5 s change), `missionCompleted(index, total)` (ends after
  the last, via `dismissed`), `missionsLeft`; snooze and timeout drop progress.

Invariants are checked in `init` blocks (`IllegalArgumentException`). Only Java 8 `java.time` APIs: minSdk 26, no desugaring.
