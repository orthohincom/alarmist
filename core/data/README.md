# core:data

Stored alarms: Room in **device-protected storage**, so alarms can be read and rescheduled after a reboot
before the first unlock (Direct Boot). Android library (`alarmist.android.library`, `alarmist.hilt`, `alarmist.room`).

Public API (`com.orthohin.alarmist.core.data`):
- `AlarmRepository`: `observeAlarms()` (by time of day, then id), `getAlarm(id)`, `getEnabledAlarms()`,
  `save(alarm): Long` (inserts on `Alarm.NEW_ID`, else replaces, re-creating a deleted row), `setEnabled`, `delete`.
  All main-safe. Features write through `AlarmEditor` (core:scheduling) instead. Writes stamp `updated_at` from the injected `Clock`.
- `EventLog`: `add(event)` (fire and forget, never throws), `observeRecent(limit)` newest first; entries older
  than 30 days are pruned. `ExpectedRings`: the ring each alarm is booked for (core:scheduling's missed-ring check).
- `SettingsRepository` (`settings: Flow<AppSettings>`, `update { }`): `AppSettings(theme: ThemeMode, dynamicColor, timeInput: TimeInput (WHEELS/DIAL), overlayHintDismissed)` (the last isn't backed up),
  in a Preferences DataStore (`datastore/settings.preferences_pb`, device-protected; unreadable → defaults).
- `BackupCodec.encode(Backup(alarms, settings), exportedAt)` / `decode(text)` (throws `BackupException` with
  `NOT_A_BACKUP`, `NEWER_VERSION` or `INVALID`): the JSON backup file, version 1, pure Kotlin (kotlinx-serialization's
  `JsonObject`, no plugin). Field by field like `AlarmMapper`; missing fields decode to the defaults, ids aren't kept,
  mission configs stay strings.
- Hilt: `DataModule` binds the repositories, the event log and the database. The app must bind `Clock`.

Storage (internal): tables `events` and `expected_rings` (no foreign key: saving replaces alarm rows), `alarms` (one flat row; times as minute of day, dates as epoch days, weekdays as a
bitmask with bit 0 = Monday, 0 = one-off) and `alarm_missions` (`alarm_id`, `position`, `type`,
`config_version`, `config` JSON; cascade delete). The encoding in `AlarmMapper` is schema: don't change it.

Schema: version 3 (2 added the auto-silence columns, 3 the event tables; both `AutoMigration`), exported to `schemas/` (commit it).
A change bumps the version and adds a migration plus a case in `MigrationTest` (Robolectric; the schemas are
added to the unit-test assets in `build.gradle.kts`). No destructive fallback, ever.

Test fixtures: `FakeAlarmRepository`, `FakeEventLog` (also `ExpectedRings`) (`testImplementation(testFixtures(project(":core:data")))`).

Tests: `AlarmMapperTest`, `BackupCodecTest` (pure), `DataStoreSettingsRepositoryTest` (temp file), `RoomEventLogTest` and `RoomAlarmRepositoryTest` (Robolectric, in-memory DB, SDK 34 because
SDK 35+ needs Java 21).
