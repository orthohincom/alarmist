# mission:taporder

Tap in order mission: numbers are scattered on a square board; the user taps them from 1 up. Android library with
Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on `mission:api` only. Registered as
type `"taporder"` (`TapOrderMissionModule`).

Config (version 1, JSON): `{"count": 10, "rounds": 2}`. `count` 5–20 numbers, `rounds` 1–5 (a new board each
round). A broken value falls back to the defaults, never an error.

Public API (`com.orthohin.alarmist.mission.taporder`, pure Kotlin, tested): `TAP_ORDER_GRID` (6), `TapTarget`,
`tapOrderBoard(count, random)` (each number in its own cell of the 6×6 grid, nudged inside it, so they never
overlap), `TapProgress` (`next`, `solved`, `tap`).

UI: tapped numbers fade; a wrong number flashes red and costs nothing (finding the next number is the task). A
circle takes 82% of a cell (about 50 dp on a phone). The seed, round and progress are saved with the screen.

Tests: `TapOrderTest`.
