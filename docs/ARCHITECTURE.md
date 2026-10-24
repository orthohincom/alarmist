# How Alarmist works

A guide for developers: the modules, the path an alarm takes from the editor to "I'm awake", and the rules the code
keeps. Each module's `README.md` has its public API and details.

## Modules
```
app/                Hilt wiring, MainActivity, navigation, flavors (fdroid/play)
build-logic/        Gradle convention plugins (alarmist.android.library, .compose, .hilt, .room, …)
core/model          pure Kotlin: Alarm, Schedule, MissionSpec, next-trigger calculator, RingSession state machine
core/common         Clock (+ FakeClock test fixture), AppDispatchers
core/data           Room in device-protected storage: alarms, event log, expected rings; DataStore settings; backup JSON
core/scheduling     AlarmEditor, AlarmScheduler (setAlarmClock), fire and reschedule receivers, snooze alarms
core/ringing        RingingController, RingingService (foreground service), audio, vibration, notifications
core/sounds         bundled sounds, imported sounds, device ringtones, preview player
core/designsystem   theme and shared Compose components
feature/alarmlist   the alarm list
feature/alarmedit   the alarm editor, sound picker, mission slots
feature/ringing     the ringing screen, missions while ringing, wake-up check screens
feature/reliability first-run setup, reliability check, event log
feature/settings    theme, time input, backup and restore
mission/api         the mission plugin contract: Mission, MissionHost, MissionRegistry, shared mission UI
mission/camera      shared camera UI for the camera missions
mission/<name>      one module per mission: barcode, math, memory, nfc, photo, shake, simon, squats, steps,
                    taporder, typing
```

Dependency rules:
- `app` → `feature/*`, `mission/*`, `core/*`. Nothing depends on `app`.
- `feature/*` → `core/*` and `mission/api`. Features don't depend on each other; `app` navigates between them.
- `mission/<name>` → `mission/api`, `core/model`, `core/designsystem` (camera missions also `mission/camera`).
  A new mission never changes core modules.
- `core/model` and `core/common` are pure Kotlin (`alarmist.jvm.library`) and unit-tested without Android.
- Features change alarms only through `AlarmEditor`, which keeps the database and the system schedule in sync.
- Time comes from the injected `Clock`; tests use `FakeClock`.

## The life of an alarm
1. **Edit.** `AlarmEditScreen` edits a draft `Alarm` (core:model). Save calls `AlarmEditor.save`, which writes the
   alarm (`AlarmRepository`, Room) and reschedules it, under one mutex.
2. **Schedule.** `AlarmScheduler` computes the next ring with `Alarm.nextTrigger(after, zone)` and books it with
   `AlarmManager.setAlarmClock` (exempt from Doze, shows the alarm icon). One PendingIntent per alarm and kind,
   told apart by a data URI (`alarmist://alarm/<id>`, `alarmist://snooze/<id>`). Each booked ring is also stored
   as an *expected ring*.
3. **Fire.** `AlarmFiredReceiver` (direct-boot aware) skips stale fires, hands the alarm to `AlarmRinger.ring`
   (implemented by core:ringing), then turns a one-off alarm off or books a weekly alarm's next ring. If the
   database fails, it rings anyway.
4. **Ring.** `RingingController` adds a `RingSession` and persists it. `RingingService`, a foreground service,
   plays the current session: alarm audio stream, volume ramp, vibration, a partial wake lock, and a notification
   whose full-screen intent opens `RingingActivity` over the lock screen. If the service can't start, an
   insistent notification plays the default sound instead.
5. **Snooze or dismiss.** Snooze books a separate snooze system alarm (armed before the sound stops). Dismiss, on
   an alarm with missions, first opens the missions.
6. **Missions.** The ringing screen runs each mission's `Runner` in order. Opening a mission mutes the alarm for
   30 s; every input (`MissionHost.onActivity()`, or any touch) extends that. With no input it rings again at full
   volume; leaving the screen rings at once. Solving the last mission dismisses.
7. **Wake-up check** (optional). After dismissing, a "Stay awake" countdown; then "Still awake?" with a time limit.
   No answer and the alarm rings again, missions from the start.

## Ring sessions
`RingSessions` (core:model) is the whole ringing state, a pure state machine persisted on every change
(`RingSessionCodec`, versioned), so ringing resumes after process death or a reboot:

```
fired → Ringing ⇄ Snoozed            (snooze / snooze alarm ends)
Ringing → (dismiss) → gone, or with the wake-up check:
          CheckPending → Checking → Ringing (no answer) | gone ("I'm awake")
Ringing → (auto-silence) quiet, ring again; after the last unanswered ring: gone + "Missed" notification
```

One alarm rings at a time; others wait in the order they went off, so a mission in progress is never swapped out.
A mission in progress is `RingSession.mission` (index, muted until), persisted too.

## Reliability
- **Direct boot:** alarms, settings and sessions live in device-protected storage, and the receivers and the
  service are direct-boot aware, so alarms ring after a reboot before the phone is unlocked.
- **Rescheduling:** `RescheduleReceiver` calls the idempotent `rescheduleAll` on boot (also locked boot), time or
  time zone changes, app update and exact-alarm permission changes; the app also calls it on every start
  (a force stop clears alarms silently).
- **Missed and late rings:** an expected ring that never fired is found at the next reschedule. Up to 10 minutes
  late it rings now; later it is reported ("didn't ring at 7:00: the phone was off / Android stopped Alarmist").
- **Reliability check** (`feature/reliability`): notifications, full-screen intent, exact alarms, background
  restriction, battery optimisation, "display over other apps", phone-maker tips, and the event log.

## Missions
A mission implements `Mission` (`mission/api`) and is registered into a Hilt map by its `type`. An alarm stores
missions as `MissionSpec(type, configVersion, config)`; the JSON `config` belongs to the mission, and core never
parses it. The editor shows `ConfigEditor` (with "Try it"); the ringing screen shows `Runner`. A mission that is
unknown, unavailable (no hardware) or not ready (no permission) can be skipped while ringing. Sensor missions count
in plain Kotlin classes (`ShakeCounter`, `StepSources`, `SquatCounter`) that are unit-tested with synthetic or
recorded data. See CONTRIBUTING.md for adding one.

## Rules the code keeps
- **Never silent:** volume 0 only with vibration. Sounds fall back custom → backup → bundled default.
- **Never trapped:** a mission that can't run can be skipped; if a timer can't be armed, the dismissal goes through.
- **Early beats late, an extra ring beats a missed one.** In a daylight-saving gap the alarm rings when the gap
  ends; in an overlap, on the first pass.
- **Wall-clock alarms:** a 07:00 alarm rings at 07:00 local time, also after a time zone change.
- **Skip next** is a date (`skippedDate`), so it needs no cleanup and survives reboots.

## Storage and backup
- Room database (`core/data`), schema exported to `schemas/` and migrated, never destructively.
- Settings in a Preferences DataStore. Ring sessions in SharedPreferences (read synchronously at start).
- Backup: a JSON file through the system file picker (no storage permission). Restore adds alarms, never
  replaces or deletes them.

## Builds
- Flavors: `fdroid` (all features) and `play` (without Play-restricted ones: the power-menu accessibility service
  and the direct battery-optimisation request).
- Release: R8 with resource shrinking, no dependency-info or VCS block, reproducible (`scripts/release-check.sh`
  builds twice from clean clones and compares). Optional signing from `keystore.properties` or `ALARMIST_*`
  environment variables.
- CI (`.github/workflows/ci.yml`): `scripts/check.sh` on every push and pull request, the reproducibility check on
  `main` and tags.
- Versions in `gradle/libs.versions.toml`. minSdk 26, Java 8 `java.time` only (no desugaring).
