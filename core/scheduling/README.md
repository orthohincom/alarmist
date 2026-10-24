# core:scheduling

Puts alarms into the system alarm service and keeps them there. Android library (`alarmist.android.library`,
`alarmist.hilt`). Depends on `core:data` (api).

Public API (`com.orthohin.alarmist.core.scheduling`):
- `AlarmEditor`: `save`, `setEnabled`, `delete`. **Features change alarms only through this**, never through
  `AlarmRepository` directly, so the database and the system schedule can't drift apart. Read via `AlarmRepository`.
- `AlarmScheduler.rescheduleAll(reason: RescheduleReason)`: idempotent; schedules every enabled alarm's next ring
  and cancels the rest. First catches up on rings that never came: ≤ 10 min late rings now,
  later calls `AlarmRinger.missed` (reason from the boot time). Not after a clock change.
- `AlarmRinger`: implemented by `core:ringing`. `ring` on fire, `snoozeEnded` when a snooze alarm goes off,
  `restore` after every `rescheduleAll`, `missed(id, due, reason)` for a ring that never came.
- `SnoozeAlarms`: `set(id, at)`, `cancel(id)`; one snooze system alarm per alarm, separate from its regular ring.
- `ExactAlarmPermission`: `isGranted()`, `requestIntent()` (only Android 12 can revoke it).

Behaviour:
- `setAlarmClock` (Doze-exempt, status bar icon); inexact `setAndAllowWhileIdle` if the permission is missing.
  One PendingIntent per alarm and kind, told apart by the data URI `alarmist://alarm/<id>` or `alarmist://snooze/<id>`.
- On fire: skips stale fires (alarm deleted or off), calls `AlarmRinger`, then turns a one-off alarm off or books
  a weekly one's next ring (never at or before the ring that just fired). If the DB fails, it rings anyway.
- `RescheduleReceiver`: (locked) boot, quickboot, time set, time zone, package replaced, exact-alarm grant.
  Both receivers are `directBootAware`. The app also reschedules on start (force stop clears alarms silently).
- All edits and reschedules are serialised by one mutex.
- Keeps `ExpectedRings` in step with the system alarms and logs Scheduled (on change), Fired, Missed and
  Rescheduled (not for app start) to the `EventLog`.

Tests: `DefaultAlarmSchedulerTest` (pure JVM, `FakeSystemAlarms` + `FakeAlarmRepository` from core:data fixtures).
