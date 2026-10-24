# mission:steps

Steps mission: walk a number of steps. Android library with Compose (`alarmist.android.library`, `.compose`,
`alarmist.hilt`). Depends on `mission:api` only. Registered as type `"steps"` (`StepsMissionModule`).

Config (version 1, JSON): `{"steps": 30}`, 10–300 in steps of 10. A broken value falls back to the default.

Sensors (`StepSources`): `StepCounter` on the accelerometer always runs; the phone's step detector
(`TYPE_STEP_DETECTOR`) runs beside it when it exists and ACTIVITY_RECOGNITION is granted (runtime on Android 10+,
declared in this module's manifest, listed in `permissions` so the editor asks when the mission is added). Each source
has its own `GaitCheck`; the mission counts whichever has counted more, so a denied permission, a missing detector or
a silent one (some report nothing while the phone is in the hand) never blocks it. `isAvailable`: either
sensor. Priority: count real walking; catching cheats comes second.

Anti-shake (`GaitCheck`, accelerometer plus gyroscope if present): rejected as shaking: two steps in a row < 300 ms
apart (one is dropped silently: a doubled detector event), RMS rotation > 3.5 rad/s, RMS motion across gravity
> 2.5 m/s² and 2x the motion along it, or a peak > 20 m/s² (last 1.5 s). A streak starts in a steady rhythm (each
interval within ±50 % of the last, or ~2x it for a missed step, ≤ 2 s): the first 4 are credited together. After that
only a pause (> 2 s) or shaking restarts it. Every step logs one debug line (`<source>: Step counted`/`held for the
streak`/`not counted`/`rejected: <reason>`, with the interval and the window's motion). Rejections by the leading
source show "That looks like shaking" for 3 s; they aren't activity, so shaking doesn't keep the alarm muted. An
up-and-down arm pump passes.

Public API (`com.orthohin.alarmist.mission.steps`, pure Kotlin, tested): `StepSources(hasGyro, onVerdict)`:
`onAccel`/`onDetectorStep` → steps to add, `onGyro`, `steps`. `StepCounter(threshold = 0.8, minGapNanos = 350 ms)
.onSample(x, y, z, t)`: smoothed force rises 0.8 m/s² above gravity, falls back below it. `GaitCheck(hasGyro)`:
`onAccel`, `onGyro`, `onStep(t)` → `Verdict(credited, shaking, restart, stats)`, `describe()`.

UI: `CountingProgress` ("Get up and walk", ring, "n steps to go", a hold-your-phone hint); the editor says it's easier to
cheat than other missions and suggests adding a barcode, photo or NFC mission. Each step calls
`MissionHost.onActivity()`. The count is saved with the screen.

Tests: `StepCounterTest`, `GaitCheckTest` (synthetic), `GaitReplayTest` (real recordings, below). The emulator has no
step detector; fake a bounce with `adb emu sensor set acceleration 0:13.5:0.8`, then `0:6.5:0.8`, 250 ms apart.

Recordings (debug builds only, `SensorRecording`): every run of the mission (alarm or Try it) writes all readings to
`/data/user_de/0/<package>/files/steps-<n>.csv` (logcat says which). Pull one with
`adb exec-out run-as com.orthohin.alarmist.debug cat /data/user_de/0/com.orthohin.alarmist.debug/files/steps-1.csv > x.csv`,
gzip it into `src/test/resources/gait/` as `walk-*.csv.gz` (must count ≥ 85 % of the busier source's candidates) or
`shake-*.csv.gz` (≤ 25 %); other names only print. A failure prints the per-step log. Try it doesn't keep the screen
on: if the phone locks, readings stop.
