# mission:squats

Squats mission: squat a number of times while holding the phone. Android library with Compose
(`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on `mission:api` only. Registered as type
`"squats"` (`SquatsMissionModule`). `isAvailable` is false without an accelerometer.

Config (version 1, JSON): `{"squats": 10}`, 5–50 in steps of 5. A broken value falls back to the default.

Public API (`com.orthohin.alarmist.mission.squats`, pure Kotlin, tested): `SquatCounter(speed, minGapNanos,
maxGapNanos).onSample(x, y, z, timestampNanos)`. It estimates vertical speed from the acceleration magnitude
(smoothed, minus a clamped slow gravity baseline, integrated with a leak, zeroed after 0.4 s still). A squat is
going down faster than 0.25 m/s, then up faster than 0.25 m/s, 0.4–4 s later. Counts squats of 0.25–0.5 m at
1.2–3 s each; walking (any pace), shaking, a still phone and 15 cm knee bends don't count. Moving the phone up and
down by arm also counts (an accelerometer can't tell).

UI: `CountingProgress` ("Do squats", ring, "n squats to go", a hold-at-your-chest hint); the editor says it's easier to
cheat than other missions (an up-and-down phone counts) and suggests adding a barcode, photo or NFC mission. Each squat calls
`MissionHost.onActivity()`. The count is saved with the screen.

Tests: `SquatCounterTest` (synthetic squats, walking, shaking). On the emulator, one squat is
`acceleration 0:7.8:0.81` for 0.45 s, `0:11.8:0.81` for 0.9 s, `0:7.8:0.81` for 0.45 s, then `0:9.78:0.81`.
