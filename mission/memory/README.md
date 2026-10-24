# mission:memory

Memory mission (Alarmy's "Find Color Tiles"): tiles light up, then the user taps the same ones. Android library with
Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on `mission:api` only. Registered as
type `"memory"` (`MemoryMissionModule`).

Config (version 1, JSON): `{"difficulty": "normal", "rounds": 2, "mistakes": 1}`. `difficulty` is a
`MemoryDifficulty.key` (`easy` 3×3 grid, 3 tiles; `normal` 4×4, 5; `hard` 5×5, 7; `very_hard` 5×5, 10);
`rounds` 1–5; `mistakes` allowed per pattern 0–3. A broken value falls back to the defaults, never an error.

Public API (`com.orthohin.alarmist.mission.memory`, pure Kotlin, tested): `MemoryDifficulty` (`showMillis`,
`fromKey`), `memoryPattern(difficulty, random)`, `MemoryRound` (`tap`, `solved`, `failed`, `replay(pattern, taps)`).

UI: tiles stay lit for `showMillis` (1 s + 250 ms per tile), then the user taps. A wrong tile turns red; one mistake
too many shows what was missed for 2 s and starts a new pattern for the same round. In landscape the status and grid
sit side by side. The seed, round, attempt, taps and phase are saved with the screen (taps are replayed).

Tests: `MemoryTest`.
