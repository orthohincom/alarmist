# mission:shake

Shake mission: shake the phone a number of times. Android library with Compose (`alarmist.android.library`,
`.compose`, `alarmist.hilt`). Depends on `mission:api` only. Registered as type `"shake"` (`ShakeMissionModule`).
`isAvailable` is false without an accelerometer (the ringing screen then lets the user skip it).

Config (version 1, JSON): `{"strength": "normal", "shakes": 30}`. `strength` is a `ShakeStrength.key` (`gentle`,
`normal`, `strong`: 5, 9 or 14 m/s² away from gravity); `shakes` is 10–150 in steps of 10. A broken value falls
back to the defaults, never an error.

Public API (`com.orthohin.alarmist.mission.shake`, pure Kotlin, tested): `ShakeStrength` (`threshold`, `fromKey`),
`ShakeCounter(threshold, minGapNanos).onSample(x, y, z, timestampNanos)`: one count per swing (the force must drop
below half the threshold before the next) and at most one per 150 ms.

UI: a progress ring and "n shakes to go". The accelerometer listener (`SENSOR_DELAY_GAME`, main thread) lives only
while the runner is shown. Each counted shake calls `MissionHost.onActivity()` (not a touch). The count is saved with
the screen, since shaking can rotate it.

Tests: `ShakeTest`. On the emulator: `adb emu sensor set acceleration 0:30:0.8`, then `0:9.78:0.81`, is one shake.
