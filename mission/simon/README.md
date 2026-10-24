# mission:simon

Simon mission: four pads flash a pattern, the user repeats it; each pattern is one pad longer than the last.
Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on `mission:api`
only. Registered as type `"simon"` (`SimonMissionModule`).

Config (version 1, JSON): `{"length": 6, "speed": "normal"}`. `length` 3–12 is the last pattern's length (patterns
start at 3 pads); `speed` is a `SimonSpeed.key` (`slow` 800 ms flash + 400 ms gap, `normal` 550 + 250, `fast`
350 + 150). A broken value falls back to the defaults, never an error.

Public API (`com.orthohin.alarmist.mission.simon`, pure Kotlin, tested): `SIMON_PADS`, `SIMON_START`, `SimonSpeed`
(`fromKey`), `simonSequence(length, random)`, `simonStart(length)`, `simonCheck(sequence, shown, taps)` →
`SimonResult` (`PARTIAL`, `CORRECT`, `WRONG`).

UI: pads have fixed game colours (green, red, yellow, blue; dimmed when off) and light up while pressed. A wrong
pad shows the one that was due for 2 s, then a new sequence at the same length. The seed, length, attempt, taps and
phase are saved with the screen; a pattern being shown when the screen rotates starts over.

Tests: `SimonTest`.
